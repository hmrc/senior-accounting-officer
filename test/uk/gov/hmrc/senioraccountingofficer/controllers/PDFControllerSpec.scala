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

package uk.gov.hmrc.senioraccountingofficer.controllers

import org.apache.pekko.stream.Materializer
import org.apache.pekko.stream.scaladsl.Source
import org.apache.pekko.util.ByteString
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.eq as eqTo
import org.mockito.Mockito.{reset, when}
import org.scalatest.BeforeAndAfterEach
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Application
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.mvc.Result
import play.api.test.FakeRequest
import play.api.test.Helpers.*
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.objectstore.client.Path
import uk.gov.hmrc.objectstore.client.{Md5Hash, Object as ObjectStoreObject, ObjectMetadata}
import uk.gov.hmrc.senioraccountingofficer.controllers.actions.{FakeIdentifierAction, IdentifierAction}
import uk.gov.hmrc.senioraccountingofficer.services.SubmissionDocumentType.{Certificate, Notification}
import uk.gov.hmrc.senioraccountingofficer.services.{ObjectStoreService, SubmissionDocumentType}

import scala.concurrent.Future

import java.time.Instant
import java.util.UUID

class PDFControllerSpec
    extends AnyWordSpec
    with Matchers
    with MockitoSugar
    with GuiceOneAppPerSuite
    with BeforeAndAfterEach {

  private val mockObjectStoreService: ObjectStoreService =
    mock[ObjectStoreService]

  implicit val hc: HeaderCarrier               = HeaderCarrier()
  implicit lazy val materializer: Materializer =
    Materializer(app.actorSystem)

  val pdfContent                                                                 = "a-pdf-document"
  private def storedPdf(reference: String, documentType: SubmissionDocumentType) =
    ObjectStoreObject(
      Path.Directory(s"/senior-accounting-officer/$reference/").file(documentType.pdfFileName(reference)),
      Source.single(ByteString(pdfContent)),
      ObjectMetadata("application/pdf", pdfContent.length.toLong, Md5Hash("hash"), Instant.now(), Map.empty)
    )

  override lazy val app: Application =
    GuiceApplicationBuilder()
      .configure("work-items.enabled" -> false)
      .overrides(
        bind[ObjectStoreService].toInstance(mockObjectStoreService),
        bind[IdentifierAction].to[FakeIdentifierAction]
      )
      .build()

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockObjectStoreService)
  }

  private val saoSubscriptionId     = "testSaoSubscriptionId"
  private val certificateReference  = "ABCDEFGHI"
  private val notificationReference = "JKLMNOPQR"

  "PDFController" should {
    "pdfStatusCertificate" should {
      "return 200 when a certificate PDF is available" in {
        when(
          mockObjectStoreService.isCertificatePdfAvailable(
            eqTo(saoSubscriptionId),
            eqTo(certificateReference)
          )(using any[HeaderCarrier])
        ).thenReturn(Future.successful(true))

        val request = FakeRequest(
          "HEAD",
          routes.PDFController
            .pdfStatusCertificate(saoSubscriptionId, certificateReference)
            .url
        )
          .withHeaders(
            "correlationId" -> UUID.randomUUID().toString
          )

        val result: Future[Result] = route(app, request).get

        status(result) mustBe OK
      }

      "return 404 when a certificate PDF is unavailable" in {
        when(
          mockObjectStoreService.isCertificatePdfAvailable(
            eqTo(saoSubscriptionId),
            eqTo(certificateReference)
          )(using any[HeaderCarrier])
        ).thenReturn(Future.successful(false))

        val request = FakeRequest(
          "HEAD",
          routes.PDFController
            .pdfStatusCertificate(saoSubscriptionId, certificateReference)
            .url
        )
          .withHeaders(
            "correlationId" -> UUID.randomUUID().toString
          )

        val result = route(app, request).get

        status(result) mustBe NOT_FOUND
      }
    }
    "pdfStatusNotification" should {
      "return 200 when a notification PDF is available" in {
        when(
          mockObjectStoreService.isNotificationPdfAvailable(
            eqTo(saoSubscriptionId),
            eqTo(notificationReference)
          )(using any[HeaderCarrier])
        ).thenReturn(Future.successful(true))

        val request = FakeRequest(
          "HEAD",
          routes.PDFController
            .pdfStatusNotification(saoSubscriptionId, notificationReference)
            .url
        )
          .withHeaders(
            "correlationId" -> UUID.randomUUID().toString
          )

        val result = route(app, request).get

        status(result) mustBe OK
      }

      "return 404 when a notification PDF is unavailable" in {
        when(
          mockObjectStoreService.isNotificationPdfAvailable(
            eqTo(saoSubscriptionId),
            eqTo(notificationReference)
          )(using any[HeaderCarrier])
        ).thenReturn(Future.successful(false))

        val request = FakeRequest(
          "HEAD",
          routes.PDFController
            .pdfStatusNotification(saoSubscriptionId, notificationReference)
            .url
        )
          .withHeaders(
            "correlationId" -> UUID.randomUUID().toString
          )

        val result = route(app, request).get

        status(result) mustBe NOT_FOUND
      }
    }
    "getPDFNotification" should {
      "return the PDF when it exists" in {
        val expectedBytes = ByteString("a-pdf-document")

        val pdf = storedPdf(notificationReference, Notification)

        when(
          mockObjectStoreService.downloadSubmissionPdf(
            eqTo(saoSubscriptionId),
            eqTo(notificationReference),
            eqTo(Notification)
          )(using any[HeaderCarrier])
        ).thenReturn(Future.successful(Some(pdf)))

        val request = FakeRequest(
          "GET",
          routes.PDFController
            .getPDFNotification(saoSubscriptionId, notificationReference)
            .url
        )
          .withHeaders(
            "correlationId" -> UUID.randomUUID().toString
          )

        val result = route(app, request).get

        status(result) mustBe OK

        contentAsBytes(result) mustBe expectedBytes
      }

      "return 404 when the PDF does not exist" in {
        when(
          mockObjectStoreService.downloadSubmissionPdf(
            eqTo(saoSubscriptionId),
            eqTo(notificationReference),
            eqTo(Notification)
          )(using any[HeaderCarrier])
        ).thenReturn(Future.successful(None))

        val request = FakeRequest(
          "GET",
          routes.PDFController
            .getPDFNotification(saoSubscriptionId, notificationReference)
            .url
        )
          .withHeaders(
            "correlationId" -> UUID.randomUUID().toString
          )

        val result = route(app, request).get

        status(result) mustBe NOT_FOUND
      }
    }
    "getPDFCertificate" should {
      "return the PDF when it exists" in {
        val expectedBytes = ByteString("a-pdf-document")

        val pdf = storedPdf(certificateReference, Certificate)

        when(
          mockObjectStoreService.downloadSubmissionPdf(
            eqTo(saoSubscriptionId),
            eqTo(certificateReference),
            eqTo(Certificate)
          )(using any[HeaderCarrier])
        ).thenReturn(Future.successful(Some(pdf)))

        val request = FakeRequest(
          "GET",
          routes.PDFController
            .getPDFCertificate(saoSubscriptionId, certificateReference)
            .url
        )
          .withHeaders(
            "correlationId" -> UUID.randomUUID().toString
          )

        val result = route(app, request).get

        status(result) mustBe OK

        contentAsBytes(result) mustBe expectedBytes
      }

      "return 404 when the PDF does not exist" in {
        when(
          mockObjectStoreService.downloadSubmissionPdf(
            eqTo(saoSubscriptionId),
            eqTo(certificateReference),
            eqTo(Certificate)
          )(using any[HeaderCarrier])
        ).thenReturn(Future.successful(None))

        val request = FakeRequest(
          "GET",
          routes.PDFController
            .getPDFCertificate(saoSubscriptionId, certificateReference)
            .url
        )
          .withHeaders(
            "correlationId" -> UUID.randomUUID().toString
          )

        val result = route(app, request).get

        status(result) mustBe NOT_FOUND
      }
    }
  }

}
