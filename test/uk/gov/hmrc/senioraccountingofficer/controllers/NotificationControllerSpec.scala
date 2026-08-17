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

import org.mockito.ArgumentMatchers.{any, eq as meq}
import org.mockito.Mockito.*
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.mockito.MockitoSugar.mock
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Application
import play.api.http.{MimeTypes, Status}
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.{JsObject, Json}
import play.api.mvc.{AnyContentAsText, Result}
import play.api.test.FakeRequest
import play.api.test.Helpers.*
import uk.gov.hmrc.senioraccountingofficer.controllers.NotificationControllerSpec.*
import uk.gov.hmrc.senioraccountingofficer.controllers.actions.FakeIdentifierAction.testSaoSubscriptionId
import uk.gov.hmrc.senioraccountingofficer.controllers.actions.{FakeIdentifierAction, IdentifierAction}
import uk.gov.hmrc.senioraccountingofficer.models.requests.*
import uk.gov.hmrc.senioraccountingofficer.services.NotificationService
import uk.gov.hmrc.senioraccountingofficer.services.NotificationService.DownstreamService.DPS
import uk.gov.hmrc.senioraccountingofficer.services.NotificationService.PostNotificationResponse.*
import uk.gov.hmrc.senioraccountingofficer.utils.TestDataGenerator.*

import scala.concurrent.Future

import java.time.LocalDate
import java.util.UUID

class NotificationControllerSpec extends AnyWordSpec with Matchers with GuiceOneAppPerSuite {

  val mockNotificationService: NotificationService = mock[NotificationService]

  override def fakeApplication(): Application =
    GuiceApplicationBuilder()
      .overrides(
        bind[NotificationService].toInstance(mockNotificationService),
        bind[IdentifierAction].to[FakeIdentifierAction]
      )
      .build()

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
          .withHeaders(
            "Content-Type"  -> MimeTypes.JSON,
            "correlationId" -> UUID.randomUUID().toString
          )
      val result = routeResult(request)

      status(result) mustBe Status.BAD_REQUEST
      contentAsString(result) must include("MALFORMED_REQUEST")

      verify(mockNotificationService, never()).postNotification(any(), any())(using any())
    }

    "NotificationService returns Success must return 200" in {
      val mockResponse = Success("ID")
      when(mockNotificationService.postNotification(any(), any())(using any()))
        .thenReturn(Future.successful(mockResponse))

      val url     = routes.NotificationController.postNotification().url
      val request =
        FakeRequest("POST", url)
          .withTextBody(validPayload.toString())
          .withHeaders(
            "Content-Type"  -> MimeTypes.JSON,
            "correlationId" -> UUID.randomUUID().toString
          )
      val result = routeResult(request)

      status(result) mustBe Status.OK
      contentAsJson(result) mustBe Json.parse("""{"notificationRef": "ID"}""")

      val expectedDpsRequest =
        NotificationRequest(
          companies = NotificationCompanies(
            List(
              NotificationCompany(
                Some(Crn(crn)),
                Utr(utr),
                CompanyName("Example Ltd"),
                LocalDate.parse("2024-12-31"),
                CompanyStatus.Active,
                CompanyType.LTD
              )
            )
          ),
          saos = Saos(
            List(
              Sao(
                PersonName("Firstname Lastname"),
                Some(LocalDate.parse("2024-04-01")),
                Some(LocalDate.parse("2025-03-31"))
              )
            )
          ),
          remarks = Some(FreeText("non-empty string"))
        )

      verify(mockNotificationService, times(1))
        .postNotification(meq(testSaoSubscriptionId), meq(expectedDpsRequest))(using any())
    }

    "NotificationService returns MalformedResponse; controller must return 500" in {
      val mockResponse = MalformedResponse(DPS)
      when(mockNotificationService.postNotification(any(), any())(using any()))
        .thenReturn(Future.successful(mockResponse))

      val url     = routes.NotificationController.postNotification().url
      val request =
        FakeRequest("POST", url)
          .withTextBody(validPayload.toString())
          .withHeaders(
            "Content-Type"  -> MimeTypes.JSON,
            "correlationId" -> UUID.randomUUID().toString
          )
      val result = routeResult(request)

      status(result) mustBe Status.INTERNAL_SERVER_ERROR
    }

    "NotificationService returns InternalServerFailure must return 502" in {
      val mockResponse = DownstreamServiceError(DPS)
      when(mockNotificationService.postNotification(any(), any())(using any()))
        .thenReturn(Future.successful(mockResponse))

      val url     = routes.NotificationController.postNotification().url
      val request =
        FakeRequest("POST", url)
          .withTextBody(validPayload.toString())
          .withHeaders(
            "Content-Type"  -> MimeTypes.JSON,
            "correlationId" -> UUID.randomUUID().toString
          )
      val result = routeResult(request)

      status(result) mustBe Status.BAD_GATEWAY
    }

    "NotificationService returns ServiceUnavailableFailure must return 502" in {
      val mockResponse = DownstreamServiceUnavailable(DPS)
      when(mockNotificationService.postNotification(any(), any())(using any()))
        .thenReturn(Future.successful(mockResponse))

      val url     = routes.NotificationController.postNotification().url
      val request =
        FakeRequest("POST", url)
          .withTextBody(validPayload.toString())
          .withHeaders(
            "Content-Type"  -> MimeTypes.JSON,
            "correlationId" -> UUID.randomUUID().toString
          )
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
          .withHeaders(
            "Content-Type"  -> MimeTypes.JSON,
            "correlationId" -> UUID.randomUUID().toString
          )
      val result = routeResult(request)

      status(result) mustBe Status.BAD_GATEWAY
    }

    "return BAD_REQUEST when the payload does not match the schema" in {
      val url     = routes.NotificationController.postNotification().url
      val request =
        FakeRequest("POST", url)
          .withTextBody(invalidPayload.toString())
          .withHeaders(
            "Content-Type"  -> MimeTypes.JSON,
            "correlationId" -> UUID.randomUUID().toString
          )
      val result = routeResult(request)

      status(result) mustBe Status.BAD_REQUEST
      contentAsString(result) must include("MISSING_REQUIRED_FIELD")
    }

    "NotificationService returns Misalignment; controller must return 500" in {
      val mockResponse = Misalignment(DPS)
      when(mockNotificationService.postNotification(any(), any())(using any()))
        .thenReturn(Future.successful(mockResponse))

      val url     = routes.NotificationController.postNotification().url
      val request =
        FakeRequest("POST", url)
          .withTextBody(validPayload.toString())
          .withHeaders(
            "Content-Type"  -> MimeTypes.JSON,
            "correlationId" -> UUID.randomUUID().toString
          )
      val result = routeResult(request)

      status(result) mustBe Status.INTERNAL_SERVER_ERROR
    }

    "NotificationService returns DownstreamUnauthorised; controller must return 500" in {
      val mockResponse = DownstreamUnauthorised(DPS)
      when(mockNotificationService.postNotification(any(), any())(using any()))
        .thenReturn(Future.successful(mockResponse))

      val url     = routes.NotificationController.postNotification().url
      val request =
        FakeRequest("POST", url)
          .withTextBody(validPayload.toString())
          .withHeaders(
            "Content-Type"  -> MimeTypes.JSON,
            "correlationId" -> UUID.randomUUID().toString
          )
      val result = routeResult(request)

      status(result) mustBe Status.INTERNAL_SERVER_ERROR
    }

    "NotificationService returns DownstreamForbidden; controller must return 500" in {
      val mockResponse = DownstreamForbidden(DPS)
      when(mockNotificationService.postNotification(any(), any())(using any()))
        .thenReturn(Future.successful(mockResponse))

      val url     = routes.NotificationController.postNotification().url
      val request =
        FakeRequest("POST", url)
          .withTextBody(validPayload.toString())
          .withHeaders(
            "Content-Type"  -> MimeTypes.JSON,
            "correlationId" -> UUID.randomUUID().toString
          )
      val result = routeResult(request)

      status(result) mustBe Status.INTERNAL_SERVER_ERROR
    }
  }
}

object NotificationControllerSpec {
  private val crn = generateCrn
  private val utr = generateUtr

  private val validPayload: JsObject = Json.obj(
    "companies" -> Json.arr(
      Json.obj(
        "name"         -> "Example Ltd",
        "utr"          -> utr,
        "crn"          -> crn,
        "type"         -> "LTD",
        "accPeriodEnd" -> "2024-12-31",
        "status"       -> "Active"
      )
    ),
    "saos" -> Json.arr(
      Json.obj(
        "name"     -> "Firstname Lastname",
        "fromDate" -> "2024-04-01",
        "toDate"   -> "2025-03-31"
      )
    ),
    "remarks" -> "non-empty string"
  )

  private val invalidPayload: JsObject = Json.obj(
    "any" -> "body"
  )
}
