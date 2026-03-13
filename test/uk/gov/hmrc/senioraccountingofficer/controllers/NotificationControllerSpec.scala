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
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.http.Status
import play.api.mvc.ControllerComponents
import play.api.test.FakeRequest
import play.api.test.Helpers.*
import uk.gov.hmrc.http.HttpResponse
import uk.gov.hmrc.senioraccountingofficer.services.NotificationService

import scala.concurrent.{ExecutionContext, Future}

class NotificationControllerSpec extends AnyWordSpec with Matchers with GuiceOneAppPerSuite with MockitoSugar {

  implicit val ec: ExecutionContext = app.injector.instanceOf[ExecutionContext]
  implicit val mat: Materializer    = app.injector.instanceOf[Materializer]
  val cc: ControllerComponents      = app.injector.instanceOf[ControllerComponents]

  val mockNotificationService: NotificationService = mock[NotificationService]
  val controller                                   = new NotificationController(cc, mockNotificationService)

  "POST /notification" should {

    "return the status and body from the downstream service" in {
      val anyBody = """{"any":"body"}"""

      val mockResponse = HttpResponse(Status.ACCEPTED, "Accepted Payload")
      when(mockNotificationService.postNotification(any(), any())(any())).thenReturn(Future.successful(mockResponse))

      val request =
        FakeRequest("POST", "/notification").withBody(anyBody).withHeaders("Content-Type" -> "text/plain")
      val result = controller.postNotification("123")(request)

      status(result) shouldBe Status.ACCEPTED
      contentAsString(result) shouldBe "Accepted Payload"
    }

    "return the status and body from the downstream service for 5xx" in {
      val anyBody = """{"any":"body"}"""

      val mockResponse = HttpResponse(Status.INTERNAL_SERVER_ERROR, "some raw error body")
      when(mockNotificationService.postNotification(any(), any())(any())).thenReturn(Future.successful(mockResponse))

      val request =
        FakeRequest("POST", "/notification").withBody(anyBody).withHeaders("Content-Type" -> "text/plain")
      val result = controller.postNotification("123")(request)

      status(result) shouldBe Status.INTERNAL_SERVER_ERROR
      contentAsString(result) shouldBe "some raw error body"
    }

  }

}
