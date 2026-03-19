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
import org.mockito.Mockito.{reset, when}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.mockito.MockitoSugar.mock
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Application
import play.api.http.Status
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.JsObject
import play.api.libs.json.Json
import play.api.mvc.AnyContentAsText
import play.api.mvc.Result
import play.api.test.FakeRequest
import play.api.test.Helpers.*
import uk.gov.hmrc.http.HttpResponse
import uk.gov.hmrc.senioraccountingofficer.connectors.ContactDetailsConnector

import scala.concurrent.Future

class ContactDetailsControllerSpec extends AnyWordSpec with Matchers with GuiceOneAppPerSuite {

  private val mockContactDetailsConnector = mock[ContactDetailsConnector]

  override def fakeApplication(): Application =
    GuiceApplicationBuilder()
      .overrides(
        bind[ContactDetailsConnector].toInstance(mockContactDetailsConnector)
      )
      .build()

  private val validPayload =
    """{
      |  "safeId": "XE000123456789",
      |  "company": {
      |    "companyName": "Acme Manufacturing Ltd",
      |    "uniqueTaxReference": "1234567890",
      |    "companyRegistrationNumber": "OC123456"
      |  },
      |  "contacts": [
      |    {
      |      "name": "Jane Doe",
      |      "email": "jane.doe@example.com"
      |    }
      |  ]
      |}""".stripMargin

  "PUT /contact-details" should {
    "return 204 when the downstream connector succeeds without a body" in {
      reset(mockContactDetailsConnector)
      when(mockContactDetailsConnector.putContactDetails(any(), any())(using any()))
        .thenReturn(Future.successful(HttpResponse(status = Status.NO_CONTENT)))

      val request = FakeRequest("PUT", "/senior-accounting-officer/contact-details/123")
        .withHeaders(CONTENT_TYPE -> "application/json")
        .withTextBody(validPayload)

      val maybeResult = route(app, request)

      maybeResult shouldBe defined
      val result = maybeResult match {
        case Some(value) => value
        case None        => fail("Expected route to be defined")
      }

      status(result) shouldBe Status.NO_CONTENT
    }

    "return the downstream JSON body for validation errors" in {
      val downstreamBody = """[{"path":"safeId","reason":"INVALID_FORMAT"}]"""
      reset(mockContactDetailsConnector)
      when(mockContactDetailsConnector.putContactDetails(any(), any())(using any()))
        .thenReturn(Future.successful(HttpResponse(status = Status.BAD_REQUEST, body = downstreamBody)))

      val request = FakeRequest("PUT", "/senior-accounting-officer/contact-details/123")
        .withHeaders(CONTENT_TYPE -> "application/json")
        .withTextBody(validPayload)

      val maybeResult = route(app, request)

      maybeResult shouldBe defined
      val result = maybeResult match {
        case Some(value) => value
        case None        => fail("Expected route to be defined")
      }

      status(result) shouldBe Status.BAD_REQUEST
      contentAsString(result) shouldBe downstreamBody
    }
  }

  "GET /contact-details" should {
    "return a 200 response with the body from the connector" in {
      val saoSubscriptionId = "123"

      val expectedStatus = 200
      val expectedBody   = "{}"

      when(mockContactDetailsConnector.getContactDetails(meq(saoSubscriptionId))(using any())) thenReturn Future
        .successful(
          HttpResponse(expectedStatus, expectedBody)
        )

      val url = routes.ContactDetailsController.getContactDetails(saoSubscriptionId).url

      val request = FakeRequest(GET, url)

      val maybeResult = route(app, request)

      maybeResult shouldBe defined
      val result = maybeResult match {
        case Some(value) => value
        case None        => fail("Expected route to be defined")
      }

      status(result) shouldBe expectedStatus
      contentAsString(result) shouldBe expectedBody
    }

    "return a 404 response with the body from the connector" in {
      val invalidSaoSubscriptionId = "456"

      val expectedStatus = 404
      val expectedBody   = "Entity not found"

      when(mockContactDetailsConnector.getContactDetails(meq(invalidSaoSubscriptionId))(using any())) thenReturn Future
        .successful(
          HttpResponse(expectedStatus, expectedBody)
        )

      val url = routes.ContactDetailsController.getContactDetails(invalidSaoSubscriptionId).url

      val request = FakeRequest(GET, url)

      val maybeResult = route(app, request)

      maybeResult shouldBe defined
      val result = maybeResult match {
        case Some(value) => value
        case None        => fail("Expected route to be defined")
      }

      status(result) shouldBe expectedStatus
      contentAsString(result) shouldBe expectedBody
    }
  }

  private def routeResult(request: FakeRequest[AnyContentAsText]): Future[Result] =
    route(app, request) match {
      case Some(result) => result
      case None         => fail("Expected route to be defined")
    }

  "PUT /contactDetails" should {
    "return 204 when the downstream connector succeeds without a body" in {
      reset(mockContactDetailsConnector)
      when(mockContactDetailsConnector.putContactDetails(any(), any())(using any()))
        .thenReturn(Future.successful(HttpResponse(status = Status.NO_CONTENT)))

      val request = FakeRequest("PUT", "/senior-accounting-officer/contact-details/123")
        .withHeaders(CONTENT_TYPE -> "application/json")
        .withTextBody(validPayload.toString())

      val result = routeResult(request)

      status(result) shouldBe Status.NO_CONTENT
    }

    "return the downstream JSON body for validation errors" in {
      val downstreamBody = """[{"path":"safeId","reason":"INVALID_FORMAT"}]"""
      reset(mockContactDetailsConnector)
      when(mockContactDetailsConnector.putContactDetails(any(), any())(using any()))
        .thenReturn(Future.successful(HttpResponse(status = Status.BAD_REQUEST, body = downstreamBody)))

      val request = FakeRequest("PUT", "/senior-accounting-officer/contact-details/123")
        .withHeaders(CONTENT_TYPE -> "application/json")
        .withTextBody(validPayload.toString())

      val result = routeResult(request)

      status(result) shouldBe Status.BAD_REQUEST
      contentAsString(result) shouldBe downstreamBody
    }

    "return 400 for malformed JSON without calling the connector" in {
      reset(mockContactDetailsConnector)

      val request = FakeRequest("PUT", "/senior-accounting-officer/contact-details/123")
        .withHeaders(CONTENT_TYPE -> "application/json")
        .withTextBody("""{"safeId":""")

      val result = routeResult(request)

      status(result) shouldBe Status.BAD_REQUEST
      contentAsJson(result) shouldBe Json.arr(Json.obj("reason" -> "MALFORMED_REQUEST"))
      verify(mockContactDetailsConnector, never()).putContactDetails(any(), any())(using any())
    }

    "return 400 for schema-invalid JSON without calling the connector" in {
      reset(mockContactDetailsConnector)

      val invalidPayload = Json.parse(validPayload).as[JsObject] - "safeId"

      val request = FakeRequest("PUT", "/senior-accounting-officer/contact-details/123")
        .withHeaders(CONTENT_TYPE -> "application/json")
        .withTextBody(invalidPayload.toString())

      val result = routeResult(request)

      status(result) shouldBe Status.BAD_REQUEST
      contentAsJson(result) shouldBe Json.arr(Json.obj("path" -> "safeId", "reason" -> "MISSING_REQUIRED_FIELD"))
      verify(mockContactDetailsConnector, never()).putContactDetails(any(), any())(using any())
    }
  }
}
