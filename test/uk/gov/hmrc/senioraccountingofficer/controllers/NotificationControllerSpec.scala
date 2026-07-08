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
import org.scalatest.matchers.must.Matchers
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
import uk.gov.hmrc.domain.SaUtrGenerator
import uk.gov.hmrc.senioraccountingofficer.services.NotificationService
import uk.gov.hmrc.senioraccountingofficer.services.NotificationService.DownstreamService.DPS
import uk.gov.hmrc.senioraccountingofficer.services.NotificationService.PostNotificationResponse.*

import scala.concurrent.Future
import scala.util.Random

class NotificationControllerSpec extends AnyWordSpec with Matchers with GuiceOneAppPerSuite {

  val mockNotificationService: NotificationService = mock[NotificationService]
  val subscriptionId                               = "123"

  override def fakeApplication(): Application =
    GuiceApplicationBuilder()
      .overrides(
        bind[NotificationService].toInstance(mockNotificationService)
      )
      .build()

  private val validPayload: JsObject = Json.obj(
    "subscriptionId" -> subscriptionId,
    "companies"      -> Json.arr(
      Json.obj(
        "name"         -> "Example Ltd",
        "utr"          -> generateUtr,
        "crn"          -> generateCrn,
        "type"         -> "LTD",
        "accPeriodEnd" -> "2024-12-31",
        "status"       -> "COMPLIANT"
      )
    ),
    "saos" -> Json.arr(
      Json.obj(
        "name"     -> "Firstname Lastname",
        "email"    -> "Firstname.Lastname@example.com",
        "fromDate" -> "2024-04-01",
        "toDate"   -> "2025-03-31"
      )
    ),
    "remarks" -> "non-empty string"
  )

  private val invalidPayload: JsObject = Json.obj(
    "any" -> "body"
  )

  private def generateCrn = {
    val num = Random.nextInt(1000000)
    f"$num%08d"
  }

  private def generateUtr = {
    val seed = Random.nextInt(1000000)
    SaUtrGenerator(seed).nextSaUtr
  }

  private def routeResult(request: FakeRequest[AnyContentAsText]): Future[Result] =
    route(app, request) match {
      case Some(value) => value
      case None        => fail("Expected route to be defined")
    }

  "POST /notification" when {

    "the payload is not valid JSON return 400" in {
      val url     = routes.NotificationController.postNotification().url
      val request =
        FakeRequest("POST", url)
          .withTextBody("this is not json")
          .withHeaders("Content-Type" -> "text/plain")
      val result = routeResult(request)

      status(result) mustBe Status.BAD_REQUEST
      contentAsString(result) must include("MALFORMED_REQUEST")
    }

    "NotificationService returns Success must return 200" in {
      val mockResponse = Success("ID", true)
      when(mockNotificationService.postNotification(any(), any())(using any()))
        .thenReturn(Future.successful(mockResponse))

      val url     = routes.NotificationController.postNotification().url
      val request =
        FakeRequest("POST", url)
          .withTextBody(validPayload.toString())
          .withHeaders("Content-Type" -> "text/plain")
      val result = routeResult(request)

      status(result) mustBe Status.OK
      contentAsJson(result) mustBe Json.parse("""{"notificationRef":"ID","isPdfAvailable":true}""")
    }

    "NotificationService returns MalformedResponse must return 502" in {
      val mockResponse = MalformedResponse(DPS)
      when(mockNotificationService.postNotification(any(), any())(using any()))
        .thenReturn(Future.successful(mockResponse))

      val url     = routes.NotificationController.postNotification().url
      val request =
        FakeRequest("POST", url)
          .withTextBody(validPayload.toString())
          .withHeaders("Content-Type" -> "text/plain")
      val result = routeResult(request)

      status(result) mustBe Status.BAD_GATEWAY
    }

    "NotificationService returns BadRequestFailure must return 502" in {
      val mockResponse = BadRequestFailure(DPS)
      when(mockNotificationService.postNotification(any(), any())(using any()))
        .thenReturn(Future.successful(mockResponse))

      val url     = routes.NotificationController.postNotification().url
      val request =
        FakeRequest("POST", url)
          .withTextBody(validPayload.toString())
          .withHeaders("Content-Type" -> "text/plain")
      val result = routeResult(request)

      status(result) mustBe Status.INTERNAL_SERVER_ERROR
    }

    "NotificationService returns InternalServerFailure must return 502" in {
      val mockResponse = InternalServerFailure(DPS)
      when(mockNotificationService.postNotification(any(), any())(using any()))
        .thenReturn(Future.successful(mockResponse))

      val url     = routes.NotificationController.postNotification().url
      val request =
        FakeRequest("POST", url)
          .withTextBody(validPayload.toString())
          .withHeaders("Content-Type" -> "text/plain")
      val result = routeResult(request)

      status(result) mustBe Status.BAD_GATEWAY
    }

    "NotificationService returns ServiceUnavailableFailure must return 502" in {
      val mockResponse = ServiceUnavailableFailure(DPS)
      when(mockNotificationService.postNotification(any(), any())(using any()))
        .thenReturn(Future.successful(mockResponse))

      val url     = routes.NotificationController.postNotification().url
      val request =
        FakeRequest("POST", url)
          .withTextBody(validPayload.toString())
          .withHeaders("Content-Type" -> "text/plain")
      val result = routeResult(request)

      status(result) mustBe Status.BAD_GATEWAY
    }

    "NotificationService returns UnknownFailure must return 502" in {
      val mockResponse = UnknownFailure(DPS, 1)
      when(mockNotificationService.postNotification(any(), any())(using any()))
        .thenReturn(Future.successful(mockResponse))

      val url     = routes.NotificationController.postNotification().url
      val request =
        FakeRequest("POST", url)
          .withTextBody(validPayload.toString())
          .withHeaders("Content-Type" -> "text/plain")
      val result = routeResult(request)

      status(result) mustBe Status.BAD_GATEWAY
    }

    "return BAD_REQUEST when the payload does not match the schema" in {
      val url     = routes.NotificationController.postNotification().url
      val request =
        FakeRequest("POST", url)
          .withTextBody(invalidPayload.toString())
          .withHeaders("Content-Type" -> "text/plain")
      val result = routeResult(request)

      status(result) mustBe Status.BAD_REQUEST
      contentAsString(result) must include("MISSING_REQUIRED_FIELD")
    }

  }

}
