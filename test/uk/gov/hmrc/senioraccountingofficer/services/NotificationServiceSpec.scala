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

package uk.gov.hmrc.senioraccountingofficer.services

import org.apache.pekko.NotUsed
import org.apache.pekko.stream.scaladsl.Source
import org.apache.pekko.util.ByteString
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.eq as meq
import org.mockito.Mockito.{verify, when}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.must.Matchers
import org.scalatest.time.{Millis, Seconds, Span}
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.mockito.MockitoSugar
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}
import uk.gov.hmrc.senioraccountingofficer.connectors.NotificationConnector
import uk.gov.hmrc.senioraccountingofficer.models.documentum.{DocumentumPackageContext, DocumentumPackageResult}
import uk.gov.hmrc.senioraccountingofficer.models.dps.NotificationDpsRequest
import uk.gov.hmrc.senioraccountingofficer.services.NotificationService.DownstreamService.DPS
import uk.gov.hmrc.senioraccountingofficer.services.documentum.DocumentumPackageService

import scala.concurrent.{ExecutionContext, Future}

import NotificationService.PostNotificationResponse.*
import NotificationServiceSpec.*

class NotificationServiceSpec extends AnyWordSpec with Matchers with MockitoSugar with ScalaFutures {

  override given patienceConfig: PatienceConfig =
    PatienceConfig(timeout = Span(5, Seconds), interval = Span(25, Millis))

  given ExecutionContext = ExecutionContext.global
  given HeaderCarrier    = HeaderCarrier()

  val mockConnector: NotificationConnector                   = mock[NotificationConnector]
  val mockDocumentumPackageService: DocumentumPackageService = mock[DocumentumPackageService]
  val mockPdfService: PdfService                             = mock[PdfService]
  val service = new NotificationService(mockConnector, mockDocumentumPackageService, mockPdfService)

  "postNotification" must {
    "return Success if everything was orchestrated successfully" in {
      val mockResponse = HttpResponse(201, validDpsResponseBody)
      when(mockConnector.postNotification(any(), any())(using any())).thenReturn(Future.successful(mockResponse))
      when(mockPdfService.generateNotificationPdf(any())).thenReturn(objectStoreFileContent)
      when(mockDocumentumPackageService.packageAndSubmit(any(), any())(using any()))
        .thenReturn(Future.successful(DocumentumPackageResult(packageAvailable = true, Some(objectStoreFilename))))

      val result = service.postNotification(requestId, testRequest).futureValue

      result mustBe Success(notificationReference, true)
      verify(mockDocumentumPackageService)
        .packageAndSubmit(
          meq(DocumentumPackageContext.notification(notificationReference, requestId, testRequest)),
          meq(objectStoreFileContent)
        )(using any())
    }

    "return MalformedResponse(DPS) for a malformed 201 response from DPS" in {
      val malformedResponseBody = "{"
      val mockResponse          = HttpResponse(201, malformedResponseBody)
      when(mockConnector.postNotification(any(), any())(using any())).thenReturn(Future.successful(mockResponse))

      val result = service.postNotification(requestId, testRequest).futureValue

      result mustBe MalformedResponse(DPS)
    }

    "return MalformedResponse(DPS) for an invalid 201 response from DPS" in {
      val invalidResponseBody = "{}"
      val mockResponse        = HttpResponse(201, invalidResponseBody)
      when(mockConnector.postNotification(any(), any())(using any())).thenReturn(Future.successful(mockResponse))

      val result = service.postNotification(requestId, testRequest).futureValue

      result mustBe MalformedResponse(DPS)
    }

    "return BadRequestFailure(DPS) for an 400 response from DPS" in {
      val mockResponse = HttpResponse(400)
      when(mockConnector.postNotification(any(), any())(using any())).thenReturn(Future.successful(mockResponse))

      val result = service.postNotification(requestId, testRequest).futureValue

      result mustBe BadRequestFailure(DPS)
    }

    "return InternalServerFailure(DPS) for an 500 response from DPS" in {
      val mockResponse = HttpResponse(500)
      when(mockConnector.postNotification(any(), any())(using any())).thenReturn(Future.successful(mockResponse))

      val result = service.postNotification(requestId, testRequest).futureValue

      result mustBe InternalServerFailure(DPS)
    }

    "return ServiceUnavailableFailure(DPS) for an 503 response from DPS" in {
      val requestId    = "123"
      val mockResponse = HttpResponse(503)
      when(mockConnector.postNotification(any(), any())(using any())).thenReturn(Future.successful(mockResponse))

      val result = service.postNotification(requestId, testRequest).futureValue

      result mustBe ServiceUnavailableFailure(DPS)
    }

    "return UnknownFailure(DPS, status) for an unexpected status response from DPS" in {
      val unexpectedStatus = 600
      val mockResponse     = HttpResponse(unexpectedStatus)
      when(mockConnector.postNotification(any(), any())(using any())).thenReturn(Future.successful(mockResponse))

      val result = service.postNotification(requestId, testRequest).futureValue

      result mustBe UnknownFailure(DPS, unexpectedStatus)
    }
  }

}

object NotificationServiceSpec {
  val requestId                           = "123"
  val testRequest: NotificationDpsRequest = NotificationDpsRequest(List.empty, List.empty)
  val notificationReference               = "NOT0123456789"
  val validDpsResponseBody: String        = s"""{"notificationRef":"$notificationReference"}"""
  val objectStoreFilename: String         = s"20260728_${notificationReference}_SAO_Notification_OFFICIAL_SENSITIVE.ZIP"
  val objectStoreFileContent: Source[ByteString, NotUsed] = Source.single(ByteString("dummy file content"))
}
