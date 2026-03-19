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

import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.mockito.MockitoSugar.mock
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Application
import play.api.http.Status
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.{JsObject, Json}
import play.api.mvc.{AnyContentAsText, Result}
import play.api.test.FakeRequest
import play.api.test.Helpers.*
import uk.gov.hmrc.http.HttpResponse
import uk.gov.hmrc.senioraccountingofficer.services.NotificationService

import scala.concurrent.Future

class NotificationControllerSpec extends AnyWordSpec with Matchers with GuiceOneAppPerSuite {

  val mockNotificationService: NotificationService = mock[NotificationService]

  override def fakeApplication(): Application =
    GuiceApplicationBuilder()
      .overrides(
        bind[NotificationService].toInstance(mockNotificationService)
      )
      .build()

  private val validPayload: JsObject = Json.obj(
    "any" -> "body"
  )

  private def routeResult(request: FakeRequest[AnyContentAsText]): Future[Result] =
    route(app, request) match {
      case Some(value) => value
      case None        => fail("Expected route to be defined")
    }

  "POST /notification" should {

    "return the status and body from the downstream service" in {
      val mockResponse = HttpResponse(Status.ACCEPTED, "Accepted Payload")
      when(mockNotificationService.postNotification(any(), any())(any())).thenReturn(Future.successful(mockResponse))

      val url     = routes.NotificationController.postNotification("123").url
      val request =
        FakeRequest("POST", url)
          .withTextBody(validPayload.toString())
          .withHeaders("Content-Type" -> "text/plain")
      val result = routeResult(request)

      status(result) shouldBe Status.ACCEPTED
      contentAsString(result) shouldBe "Accepted Payload"
    }

    "return the status and body from the downstream service for 5xx" in {
      val mockResponse = HttpResponse(Status.INTERNAL_SERVER_ERROR, "some raw error body")
      when(mockNotificationService.postNotification(any(), any())(any())).thenReturn(Future.successful(mockResponse))

      val url     = routes.NotificationController.postNotification("123").url
      val request =
        FakeRequest("POST", url)
          .withTextBody(validPayload.toString())
          .withHeaders("Content-Type" -> "text/plain")
      val result = routeResult(request)

      status(result) shouldBe Status.INTERNAL_SERVER_ERROR
      contentAsString(result) shouldBe "some raw error body"
    }

  }

}
