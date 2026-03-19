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
import org.mockito.Mockito.*
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
import uk.gov.hmrc.senioraccountingofficer.connectors.SubscriptionsConnector

import scala.concurrent.Future

class SubscriptionsControllerSpec extends AnyWordSpec with Matchers with GuiceOneAppPerSuite {

  private val mockSubscriptionsConnector = mock[SubscriptionsConnector]

  override def fakeApplication(): Application =
    GuiceApplicationBuilder()
      .overrides(
        bind[SubscriptionsConnector].toInstance(mockSubscriptionsConnector)
      )
      .build()

  private val validPayload: JsObject = Json.obj(
    "safeId"  -> "XE000123456789",
    "company" -> Json.obj(
      "companyName"               -> "Acme Manufacturing Ltd",
      "uniqueTaxReference"        -> "1234567890",
      "companyRegistrationNumber" -> "OC123456"
    ),
    "contacts" -> Json.arr(
      Json.obj(
        "name"  -> "Jane Doe",
        "email" -> "jane.doe@example.com"
      )
    )
  )

  private val subscriptionsUrl = "/senior-accounting-officer/subscriptions"

  private def routeResult(request: FakeRequest[AnyContentAsText]): Future[Result] = {
    route(app, request) match {
      case Some(result) => result
      case None         => fail("Expected route to be defined")
    }
  }

  private def subscriptionRequest(payload: JsObject): FakeRequest[AnyContentAsText] = {
    FakeRequest("PUT", subscriptionsUrl)
      .withHeaders(CONTENT_TYPE -> "application/json")
      .withTextBody(payload.toString())
  }

  private def assertValidationError(payload: JsObject, expectedError: play.api.libs.json.JsValue): Unit = {
    reset(mockSubscriptionsConnector)

    val result = routeResult(subscriptionRequest(payload))

    status(result) shouldBe Status.BAD_REQUEST
    contentAsJson(result) shouldBe Json.arr(expectedError)
    verify(mockSubscriptionsConnector, never()).putSubscription(any())(using any())
  }

  "PUT /subscriptions" should {
    "return 204 when the downstream connector succeeds without a body" in {
      reset(mockSubscriptionsConnector)
      when(mockSubscriptionsConnector.putSubscription(any())(using any()))
        .thenReturn(Future.successful(HttpResponse(status = Status.NO_CONTENT)))

      val result = routeResult(subscriptionRequest(validPayload))

      status(result) shouldBe Status.NO_CONTENT
    }

    "return the downstream JSON body for validation errors" in {
      val downstreamBody = """[{"path":"safeId","reason":"INVALID_FORMAT"}]"""
      reset(mockSubscriptionsConnector)
      when(mockSubscriptionsConnector.putSubscription(any())(using any()))
        .thenReturn(Future.successful(HttpResponse(status = Status.BAD_REQUEST, body = downstreamBody)))

      val result = routeResult(subscriptionRequest(validPayload))

      status(result) shouldBe Status.BAD_REQUEST
      contentAsString(result) shouldBe downstreamBody
    }

    "return 400 with MALFORMED_REQUEST for malformed JSON without calling the connector" in {
      reset(mockSubscriptionsConnector)

      val request = FakeRequest("PUT", subscriptionsUrl)
        .withHeaders(CONTENT_TYPE -> "application/json")
        .withTextBody("""{"safeId":""")

      val result = routeResult(request)

      status(result) shouldBe Status.BAD_REQUEST
      contentAsJson(result) shouldBe Json.arr(Json.obj("reason" -> "MALFORMED_REQUEST"))
      verify(mockSubscriptionsConnector, never()).putSubscription(any())(using any())
    }

    "return 400 with MISSING_REQUIRED_FIELD for schema-invalid JSON without calling the connector" in {
      assertValidationError(
        validPayload - "safeId",
        Json.obj("path" -> "safeId", "reason" -> "MISSING_REQUIRED_FIELD")
      )
    }

    "return 400 with CANNOT_BE_EMPTY for an empty contact name without calling the connector" in {
      val invalidPayload = validPayload ++ Json.obj(
        "contacts" -> Json.arr(
          Json.obj(
            "name"  -> "",
            "email" -> "jane.doe@example.com"
          )
        )
      )

      assertValidationError(
        invalidPayload,
        Json.obj("path" -> "contacts[0].name", "reason" -> "CANNOT_BE_EMPTY")
      )
    }

    "return 400 with INVALID_DATA_TYPE for a non-string contact name without calling the connector" in {
      val invalidPayload = validPayload ++ Json.obj(
        "contacts" -> Json.arr(
          Json.obj(
            "name"  -> 123,
            "email" -> "jane.doe@example.com"
          )
        )
      )

      assertValidationError(
        invalidPayload,
        Json.obj("path" -> "contacts[0].name", "reason" -> "INVALID_DATA_TYPE")
      )
    }

    "return 400 with ARRAY_MIN_ITEMS_NOT_MET for empty contacts without calling the connector" in {
      assertValidationError(
        validPayload ++ Json.obj("contacts" -> Json.arr()),
        Json.obj("path" -> "contacts", "reason" -> "ARRAY_MIN_ITEMS_NOT_MET")
      )
    }

    "return 400 with LENGTH_OUT_OF_BOUNDS for too many contacts without calling the connector" in {
      val invalidPayload = validPayload ++ Json.obj(
        "contacts" -> Json.arr(
          Json.obj("name" -> "Jane Doe", "email" -> "jane.doe@example.com"),
          Json.obj("name" -> "John Doe", "email" -> "john.doe@example.com"),
          Json.obj("name" -> "Jack Doe", "email" -> "jack.doe@example.com")
        )
      )

      assertValidationError(
        invalidPayload,
        Json.obj("path" -> "contacts", "reason" -> "LENGTH_OUT_OF_BOUNDS")
      )
    }

    "return 400 with LENGTH_OUT_OF_BOUNDS for a companyName over 160 chars without calling the connector" in {
      val invalidPayload = validPayload ++ Json.obj(
        "company" -> Json.obj(
          "companyName"               -> ("A" * 161),
          "uniqueTaxReference"        -> "1234567890",
          "companyRegistrationNumber" -> "OC123456"
        )
      )

      assertValidationError(
        invalidPayload,
        Json.obj("path" -> "company.companyName", "reason" -> "LENGTH_OUT_OF_BOUNDS")
      )
    }
  }
}
