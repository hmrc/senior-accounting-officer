/*
 * Copyright 2026 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.gov.hmrc.senioraccountingofficer.services.submission

import org.apache.pekko.NotUsed
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.{Materializer, SystemMaterializer}
import org.apache.pekko.stream.scaladsl.Source
import org.apache.pekko.util.ByteString
import org.mockito.ArgumentMatchers.{any, eq as meq}
import org.mockito.Mockito.*
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatestplus.mockito.MockitoSugar
import play.api.libs.json.Json
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}
import uk.gov.hmrc.objectstore.client.{Object as StoredObject, *}
import uk.gov.hmrc.objectstore.client.play.PlayObjectStoreClient
import uk.gov.hmrc.senioraccountingofficer.connectors.*
import uk.gov.hmrc.senioraccountingofficer.models.documentum.PreparedSdesSubmission
import uk.gov.hmrc.senioraccountingofficer.models.submission.*
import uk.gov.hmrc.senioraccountingofficer.models.submission.SubmissionStep.*
import uk.gov.hmrc.senioraccountingofficer.services.PdfService
import uk.gov.hmrc.senioraccountingofficer.services.documentum.DocumentumPackageService
import java.time.{Clock, Instant, ZoneOffset}
import scala.concurrent.{ExecutionContext, Future}
import SubmissionTestData.*

class SubmissionOperationsSpec
    extends AnyFreeSpec
    with Matchers
    with ScalaFutures
    with MockitoSugar
    with BeforeAndAfterAll {
  given ActorSystem             = ActorSystem("submission-operations-test")
  given Materializer            = SystemMaterializer(summon[ActorSystem]).materializer
  given ExecutionContext        = summon[ActorSystem].dispatcher
  given HeaderCarrier           = HeaderCarrier()
  override def afterAll(): Unit = { summon[ActorSystem].terminate().futureValue; super.afterAll() }

  class Fixture {
    val subscriptions = mock[GetSubscriptionConnector]
    val customers     = mock[CrmmConnector]
    val notifications = mock[NotificationConnector]
    val certificates  = mock[CertificateConnector]
    val emails        = mock[EmailConnector]
    val sdes          = mock[SdesConnector]
    val pdf           = mock[PdfService]
    val store         = mock[PlayObjectStoreClient]
    val packages      = mock[DocumentumPackageService]
    val operations    = new SubmissionOperations(
      subscriptions,
      customers,
      notifications,
      certificates,
      emails,
      sdes,
      pdf,
      store,
      packages,
      Clock.fixed(Instant.parse("2026-10-05T09:00:00Z"), ZoneOffset.UTC)
    )
    val path   = Path.Directory("/test/").file("test.pdf")
    val stored = StoredObject(
      path,
      Source.single(ByteString("pdf")),
      ObjectMetadata("application/pdf", 3, Md5Hash("hash"), Instant.now(), Map.empty)
    )
    def existingPdf(): Unit = when(store.getObject[Source[ByteString, NotUsed]](any(), any())(using any(), any()))
      .thenReturn(Future.successful(Some(stored)))
  }

  "subscription retrieval preserves the snapshot for customer lookup" in {
    val f = new Fixture
    when(f.subscriptions.getSubscription(any())(using any()))
      .thenReturn(Future.successful(HttpResponse(200, Json.toJson(subscription).toString)))
    val result = f.operations.execute(command(RetrieveSubscription)).futureValue
    result.data.subscription mustBe Some(subscription)
    f.operations.successors(result).map(_.step) mustBe Seq(RetrieveCustomer)
  }
  "CRMM receives the nominated UTR and preserves an optional customer number" in {
    val f = new Fixture
    when(f.customers.retrieveCustomer(any())(using any()))
      .thenReturn(Future.successful(HttpResponse(200, """{"customerId":"found"}""")))
    f.operations.execute(command(RetrieveCustomer)).futureValue.data.customerNumber mustBe Some("found")
    verify(f.customers).retrieveCustomer(
      meq(
        uk.gov.hmrc.senioraccountingofficer.models.crmm
          .RetrieveCustomerRequest(subscription.nominatedCompany.crn, Some(utr))
      )
    )(using any())
    when(f.customers.retrieveCustomer(any())(using any())).thenReturn(Future.successful(HttpResponse(200, "{}")))
    f.operations.execute(command(RetrieveCustomer)).futureValue.data.customerNumber mustBe None
  }
  for kind <- SubmissionKind.values do {
    s"$kind DPS submission includes the customer number and records its reference" in {
      val f = new Fixture
      val c = command(SubmitDps, kind)
      when(f.notifications.postNotification(any(), any())(using any()))
        .thenReturn(Future.successful(HttpResponse(201, """{"notificationRef":"NOT123"}""")))
      when(f.certificates.postCertificate(any(), any())(using any()))
        .thenReturn(Future.successful(HttpResponse(201, """{"certificateRef":"CRT123"}""")))
      val result = f.operations.execute(c).futureValue
      result.data.reference mustBe Some(if kind == SubmissionKind.Notification then "NOT123" else "CRT123")
      if kind == SubmissionKind.Notification then
        verify(f.notifications).postNotification(
          meq("subscription"),
          meq(notification.toNotificationDpsRequest(Some("customer-number")))
        )(using any())
      else
        verify(f.certificates).postCertificate(
          meq("subscription"),
          meq(certificate.toCertificateDpsRequest(Some("customer-number")))
        )(using any())
    }
    s"$kind initial PDF fans out independent recipient and delivery commands" in {
      val f        = new Fixture
      val children = f.operations.successors(command(InitialPdf, kind).copy(operationFinished = true))
      children.count(_.step == EnsurePdf) mustBe 1
      children.count(_.step == SendEmail) mustBe (if kind == SubmissionKind.Notification then 2 else 3)
      children.map(_.commandKey).distinct.size mustBe children.size
    }
  }
  "email failures propagate for retry, and permanent rejection is classified" in {
    val f     = new Fixture
    val email = f.operations.successors(command(InitialPdf)).find(_.step == SendEmail).get
    when(f.emails.postEmail(any())(using any())).thenReturn(Future.successful(HttpResponse(503)))
    f.operations.execute(email).failed.futureValue.asInstanceOf[OperationRejected].retriable mustBe true
    when(f.emails.postEmail(any())(using any())).thenReturn(Future.successful(HttpResponse(400)))
    f.operations.execute(email).failed.futureValue.asInstanceOf[OperationRejected].retriable mustBe false
    when(f.emails.postEmail(any())(using any())).thenReturn(Future.successful(HttpResponse(202)))
    f.operations.execute(email).futureValue.operationFinished mustBe true
  }
  "DPS server errors and malformed success are terminal ambiguous outcomes" in {
    val f = new Fixture
    when(f.notifications.postNotification(any(), any())(using any())).thenReturn(Future.successful(HttpResponse(500)))
    val error = f.operations.execute(command(SubmitDps)).failed.futureValue.asInstanceOf[OperationRejected]
    error.retriable mustBe false
    error.failure.ambiguous mustBe true
    when(f.notifications.postNotification(any(), any())(using any()))
      .thenReturn(Future.successful(HttpResponse(201, "{}")))
    f.operations
      .execute(command(SubmitDps))
      .failed
      .futureValue
      .asInstanceOf[OperationRejected]
      .failure
      .ambiguous mustBe true
  }
  "delivery reuses an existing PDF without regenerating it" in {
    val f = new Fixture
    f.existingPdf()
    f.operations.execute(command(EnsurePdf)).futureValue.operationFinished mustBe true
    verifyNoInteractions(f.pdf)
  }
  "a missing PDF is generated and uploaded before delivery" in {
    val f = new Fixture
    when(f.store.getObject[Source[ByteString, NotUsed]](any(), any())(using any(), any()))
      .thenReturn(Future.successful(None), Future.successful(Some(f.stored)))
    when(f.pdf.generateNotificationPdf(any())).thenReturn(Source.single(ByteString("new-pdf")))
    when(f.store.putObject(any(), any[Source[ByteString, ?]](), any(), any(), any(), any())(using any(), any()))
      .thenReturn(Future.successful(ObjectSummaryWithMd5(f.path, 3, Md5Hash("hash"), Instant.now())))
    f.operations.execute(command(EnsurePdf)).futureValue.operationFinished mustBe true
    verify(f.pdf).generateNotificationPdf(any())
  }
  "packaging preserves customer metadata and the prepared SDES parameters" in {
    val f = new Fixture
    f.existingPdf()
    val prepared = PreparedSdesSubmission("REF123", "file.zip", "owner", "/sdes/file.zip", "md5", 100)
    when(f.packages.preparePackage(any(), any(), any())(using any())).thenReturn(Future.successful(prepared))
    val result = f.operations.execute(command(PackageDocumentum)).futureValue
    result.data.prepared mustBe Some(prepared)
    val next = f.operations.successors(result).head
    when(f.sdes.notifyFileReady(any(), any(), any(), any(), any())(using any()))
      .thenReturn(Future.successful(HttpResponse(202)))
    f.operations.execute(next).futureValue.operationFinished mustBe true
    verify(f.sdes).notifyFileReady(meq("file.zip"), meq("owner"), meq("/sdes/file.zip"), meq("md5"), meq(100L))(using
      any()
    )
  }
}
