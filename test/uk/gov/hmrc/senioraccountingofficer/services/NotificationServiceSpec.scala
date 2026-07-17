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

import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.eq as meq
import org.mockito.ArgumentMatchers.isNull
import org.mockito.Mockito.when
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.mockito.MockitoSugar
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}
import uk.gov.hmrc.objectstore.client.Md5Hash
import uk.gov.hmrc.objectstore.client.ObjectSummaryWithMd5
import uk.gov.hmrc.objectstore.client.Path
import uk.gov.hmrc.objectstore.client.play.PlayObjectStoreClient
import uk.gov.hmrc.senioraccountingofficer.connectors.NotificationConnector
import uk.gov.hmrc.senioraccountingofficer.models.dps.NotificationDpsRequest
import uk.gov.hmrc.senioraccountingofficer.services.NotificationService.DownstreamService.DPS

import scala.concurrent.{ExecutionContext, Future}

import java.time.Instant

import NotificationService.PostNotificationResponse.*
import NotificationServiceSpec.*
import uk.gov.hmrc.objectstore.client.play.PlayObjectStoreClient
import uk.gov.hmrc.objectstore.client.ObjectSummaryWithMd5
import uk.gov.hmrc.objectstore.client.Path
import uk.gov.hmrc.objectstore.client.Md5Hash
import java.time.Instant

class NotificationServiceSpec extends AnyWordSpec with Matchers with MockitoSugar with ScalaFutures {

  given ExecutionContext = ExecutionContext.global
  given HeaderCarrier    = HeaderCarrier()

  val mockConnector: NotificationConnector         = mock[NotificationConnector]
  val mockObjectStoreClient: PlayObjectStoreClient = mock[PlayObjectStoreClient]
  val service                                      = new NotificationService(mockConnector, mockObjectStoreClient)

  "postNotification" must {
    "return Success if everything was orchestrated successfully" in {
      val mockResponse = HttpResponse(201, validDpsResponseBody)
      when(mockConnector.postNotification(any(), any())(using any())).thenReturn(Future.successful(mockResponse))

      when(
        mockObjectStoreClient.putObject(
          path = meq(
            Path
              .Directory(objectStorePath)
              .file(objectStoreFilename)
          ),
          content = meq(objectStoreFileContent),
          retentionPeriod = isNull,
          contentType = isNull,
          contentMd5 = isNull,
          owner = meq(objectStoreOwner)
        )(using any(), any())
      )
        .thenReturn(
          Future.successful(ObjectSummaryWithMd5(Path.File(objectStoreFilename), 0, Md5Hash("hash"), Instant.now))
        )

      val result = service.postNotification(requestId, testRequest).futureValue

      result mustBe Success(notificationReference, true)
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
  val objectStorePath: String             = s"/senior-accounting-officer/${notificationReference}/"
  val objectStoreFilename: String         = s"${notificationReference}_SAO_notification.pdf"
  val objectStoreOwner                    = "senior-accounting-officer"
  val objectStoreFileContent              = "dummy file content"
}
