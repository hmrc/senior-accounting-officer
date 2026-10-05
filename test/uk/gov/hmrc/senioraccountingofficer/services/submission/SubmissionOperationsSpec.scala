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
import org.apache.pekko.stream.scaladsl.Source
import org.apache.pekko.stream.{Materializer, SystemMaterializer}
import org.apache.pekko.util.ByteString
import org.mockito.ArgumentMatchers.{any, eq as meq}
import org.mockito.Mockito.*
import org.scalatest.*
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatestplus.mockito.MockitoSugar
import play.api.libs.json.Json
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}
import uk.gov.hmrc.objectstore.client.play.PlayObjectStoreClient
import uk.gov.hmrc.objectstore.client.{Object as StoredObject, *}
import uk.gov.hmrc.senioraccountingofficer.connectors.*
import uk.gov.hmrc.senioraccountingofficer.models.crmm.RetrieveCustomerRequest
import uk.gov.hmrc.senioraccountingofficer.models.documentum.PreparedSdesSubmission
import uk.gov.hmrc.senioraccountingofficer.models.submission.*
import uk.gov.hmrc.senioraccountingofficer.models.submission.SubmissionStep.*
import uk.gov.hmrc.senioraccountingofficer.services.PdfService
import uk.gov.hmrc.senioraccountingofficer.services.documentum.DocumentumPackageService

import scala.concurrent.{ExecutionContext, Future}

import java.time.{Clock, Instant, ZoneOffset}

import SubmissionTestData.*
import uk.gov.hmrc.objectstore.client.Path.File

class SubmissionOperationsSpec
    extends AnyFreeSpec
    with Matchers
    with Inside
    with OptionValues
    with ScalaFutures
    with MockitoSugar
    with BeforeAndAfterAll
    with BeforeAndAfterEach {

  given ActorSystem = ActorSystem("submission-operations-test")

  given Materializer = SystemMaterializer(summon[ActorSystem]).materializer

  given ExecutionContext = summon[ActorSystem].dispatcher

  given HeaderCarrier = HeaderCarrier()

  override def afterAll(): Unit = {
    summon[ActorSystem].terminate().futureValue
    super.afterAll()
  }

  val mockGetSubscriptionConnector: GetSubscriptionConnector = mock[GetSubscriptionConnector]
  val mockCrmmConnector: CrmmConnector                       = mock[CrmmConnector]
  val mockNotificationConnector: NotificationConnector       = mock[NotificationConnector]
  val mockCertificateConnector: CertificateConnector         = mock[CertificateConnector]
  val mockEmailConnector: EmailConnector                     = mock[EmailConnector]
  val mockSdesConnector: SdesConnector                       = mock[SdesConnector]
  val mockPdfService: PdfService                             = mock[PdfService]
  val mockObjectStoreClient: PlayObjectStoreClient           = mock[PlayObjectStoreClient]
  val mockDocumentumPackageService: DocumentumPackageService = mock[DocumentumPackageService]

  val operations = new SubmissionOperations(
    mockGetSubscriptionConnector,
    mockCrmmConnector,
    mockNotificationConnector,
    mockCertificateConnector,
    mockEmailConnector,
    mockSdesConnector,
    mockPdfService,
    mockObjectStoreClient,
    mockDocumentumPackageService,
    Clock.fixed(Instant.parse("2026-10-05T09:00:00Z"), ZoneOffset.UTC)
  )

  val path: File = Path.Directory("/test/").file("test.pdf")
  val stored: uk.gov.hmrc.objectstore.client.Object[Source[ByteString, NotUsed]] = StoredObject(
    path,
    Source.single(ByteString("pdf")),
    ObjectMetadata("application/pdf", 3, Md5Hash("hash"), Instant.parse("2026-10-05T09:00:00Z"), Map.empty)
  )

  def existingPdf(): Unit =
    when(mockObjectStoreClient.getObject[Source[ByteString, NotUsed]](any(), any())(using any(), any()))
      .thenReturn(Future.successful(Some(stored)))

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(
      mockGetSubscriptionConnector,
      mockCrmmConnector,
      mockNotificationConnector,
      mockCertificateConnector,
      mockEmailConnector,
      mockSdesConnector,
      mockPdfService,
      mockObjectStoreClient,
      mockDocumentumPackageService
    )
  }

  "SubmissionOperations" - {
    "subscription retrieval preserves the snapshot for customer lookup" in {
      when(mockGetSubscriptionConnector.getSubscription(any())(using any()))
        .thenReturn(Future.successful(HttpResponse(200, Json.toJson(subscription).toString)))
      val result = operations.execute(command(RetrieveSubscription)).futureValue
      result.data.subscription mustBe Some(subscription)
      operations.successors(result).map(_.step) mustBe Seq(RetrieveCustomer)
    }

    "CRMM receives the nominated UTR and preserves an optional customer number" in {
      when(mockCrmmConnector.retrieveCustomer(any())(using any()))
        .thenReturn(Future.successful(HttpResponse(200, """{"customerId":"found"}""")))
      operations.execute(command(RetrieveCustomer)).futureValue.data.customerNumber mustBe Some("found")
      verify(mockCrmmConnector).retrieveCustomer(
        meq(
          RetrieveCustomerRequest(subscription.nominatedCompany.crn, Some(utr))
        )
      )(using any())
      when(mockCrmmConnector.retrieveCustomer(any())(using any()))
        .thenReturn(Future.successful(HttpResponse(200, "{}")))
      operations.execute(command(RetrieveCustomer)).futureValue.data.customerNumber mustBe None
    }

    for kind <- SubmissionKind.values do {
      s"$kind DPS submission includes the customer number and records its reference" in {
        val submission = command(SubmitDps, kind)
        when(mockNotificationConnector.postNotification(any(), any())(using any()))
          .thenReturn(Future.successful(HttpResponse(201, """{"notificationRef":"NOT123"}""")))
        when(mockCertificateConnector.postCertificate(any(), any())(using any()))
          .thenReturn(Future.successful(HttpResponse(201, """{"certificateRef":"CRT123"}""")))
        val result = operations.execute(submission).futureValue
        result.data.reference mustBe Some(if kind == SubmissionKind.Notification then "NOT123" else "CRT123")
        if kind == SubmissionKind.Notification then
          verify(mockNotificationConnector).postNotification(
            meq("subscription"),
            meq(notification.toNotificationDpsRequest(Some("customer-number")))
          )(using any())
        else
          verify(mockCertificateConnector).postCertificate(
            meq("subscription"),
            meq(certificate.toCertificateDpsRequest(Some("customer-number")))
          )(using any())
      }

      s"$kind initial PDF fans out independent recipient and delivery commands" in {
        val children = operations.successors(command(InitialPdf, kind).copy(operationFinished = true))
        children.count(_.step == EnsurePdf) mustBe 1
        children.count(_.step == SendEmail) mustBe (if kind == SubmissionKind.Notification then 2 else 3)
        children.map(_.commandKey).distinct.size mustBe children.size
      }
    }

    "email delivery" - {
      for (status, retriable) <- Seq(503 -> true, 400 -> false) do {
        s"classify an HTTP $status rejection" in {
          val email = operations.successors(command(InitialPdf)).find(_.step == SendEmail).value
          when(mockEmailConnector.postEmail(any())(using any())).thenReturn(Future.successful(HttpResponse(status)))

          inside(operations.execute(email).failed.futureValue) { case rejection: OperationRejected =>
            rejection.retriable mustBe retriable
          }
        }
      }

      "complete when the email is accepted" in {
        val email = operations.successors(command(InitialPdf)).find(_.step == SendEmail).value
        when(mockEmailConnector.postEmail(any())(using any())).thenReturn(Future.successful(HttpResponse(202)))

        operations.execute(email).futureValue.operationFinished mustBe true
      }
    }

    "DPS submission" - {
      for (description, response) <- Seq(
          "server error"      -> HttpResponse(500),
          "malformed success" -> HttpResponse(201, "{}")
        )
      do {
        s"treat a $description as a terminal ambiguous outcome" in {
          when(mockNotificationConnector.postNotification(any(), any())(using any()))
            .thenReturn(Future.successful(response))

          inside(operations.execute(command(SubmitDps)).failed.futureValue) { case rejection: OperationRejected =>
            rejection.retriable mustBe false
            rejection.failure.ambiguous mustBe true
          }
        }
      }
    }

    "delivery reuses an existing PDF without regenerating it" in {
      existingPdf()
      operations.execute(command(EnsurePdf)).futureValue.operationFinished mustBe true
      verifyNoInteractions(mockPdfService)
    }

    "a missing PDF is generated and uploaded before delivery" in {
      when(mockObjectStoreClient.getObject[Source[ByteString, NotUsed]](any(), any())(using any(), any()))
        .thenReturn(Future.successful(None), Future.successful(Some(stored)))
      when(mockPdfService.generateNotificationPdf(any())).thenReturn(Source.single(ByteString("new-pdf")))
      when(
        mockObjectStoreClient.putObject(any(), any[Source[ByteString, ?]](), any(), any(), any(), any())(using
          any(),
          any()
        )
      )
        .thenReturn(
          Future.successful(ObjectSummaryWithMd5(path, 3, Md5Hash("hash"), Instant.parse("2026-10-05T09:00:00Z")))
        )
      operations.execute(command(EnsurePdf)).futureValue.operationFinished mustBe true
      verify(mockPdfService).generateNotificationPdf(any())
    }

    "packaging preserves customer metadata and the prepared SDES parameters" in {
      existingPdf()
      val prepared = PreparedSdesSubmission("REF123", "file.zip", "owner", "/sdes/file.zip", "md5", 100)
      when(mockDocumentumPackageService.preparePackage(any(), any(), any())(using any()))
        .thenReturn(Future.successful(prepared))
      val result = operations.execute(command(PackageDocumentum)).futureValue
      result.data.prepared mustBe Some(prepared)
      val next = operations.successors(result).head
      when(mockSdesConnector.notifyFileReady(any(), any(), any(), any(), any())(using any()))
        .thenReturn(Future.successful(HttpResponse(202)))
      operations.execute(next).futureValue.operationFinished mustBe true
      verify(mockSdesConnector).notifyFileReady(
        meq("file.zip"),
        meq("owner"),
        meq("/sdes/file.zip"),
        meq("md5"),
        meq(100L)
      )(using
        any()
      )
    }
  }
}
