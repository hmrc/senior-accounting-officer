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
import org.mockito.Mockito.{reset, when, *}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.mockito.MockitoSugar.mock
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Application
import play.api.http.Status
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.{JsArray, JsObject, Json}
import play.api.mvc.*
import play.api.test.FakeRequest
import play.api.test.Helpers.*
import uk.gov.hmrc.http.HttpResponse
import uk.gov.hmrc.senioraccountingofficer.connectors.ContactDetailsConnector

import scala.concurrent.Future
import play.api.libs.json.JsValue

class ContactDetailsControllerSpec extends AnyWordSpec with Matchers with GuiceOneAppPerSuite {

  private val mockContactDetailsConnector = mock[ContactDetailsConnector]

  override def fakeApplication(): Application =
    GuiceApplicationBuilder()
      .overrides(
        bind[ContactDetailsConnector].toInstance(mockContactDetailsConnector)
      )
      .build()

  private val validPayload: JsArray = Json.arr(
    Json.obj(
      "name"  -> "Jane Doe",
      "email" -> "jane.doe@acme.com"
    ),
    Json.obj(
      "name"  -> "John Smith",
      "email" -> "john.smith@acme.com"
    )
  )

  private def routeJsonRequest(request: FakeRequest[AnyContentAsJson]): Future[Result] =
    route(app, request) match {
      case Some(result) => result
      case None         => fail("Expected route to be defined")
    }

  private def routeTextRequest(request: FakeRequest[AnyContentAsText]): Future[Result] =
    route(app, request) match {
      case Some(result) => result
      case None         => fail("Expected route to be defined")
    }

  private def routeEmptyRequest(request: FakeRequest[AnyContentAsEmpty.type]): Future[Result] =
    route(app, request) match {
      case Some(result) => result
      case None         => fail("Expected route to be defined")
    }

  private def createUpdateContactDetailsRequest(payload: JsValue): FakeRequest[AnyContentAsJson] = {
    FakeRequest("PUT", "/senior-accounting-officer/contact-details/123")
      .withHeaders(CONTENT_TYPE -> "application/json")
      .withJsonBody(payload)
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

      val result = routeEmptyRequest(FakeRequest(GET, url))

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

      val result = routeEmptyRequest(FakeRequest(GET, url))

      status(result) shouldBe expectedStatus
      contentAsString(result) shouldBe expectedBody
    }
  }

  "PUT /contactDetails" should {
    "return 204 when the downstream connector succeeds without a body" in {
      reset(mockContactDetailsConnector)
      when(mockContactDetailsConnector.putContactDetails(any(), any())(using any()))
        .thenReturn(Future.successful(HttpResponse(status = Status.NO_CONTENT)))

      val request = createUpdateContactDetailsRequest(validPayload)

      val result = routeJsonRequest(request)

      status(result) shouldBe Status.NO_CONTENT
    }

    "return the downstream JSON body for validation errors" in {
      val downstreamBody = """[{"path":"safeId","reason":"INVALID_FORMAT"}]"""
      reset(mockContactDetailsConnector)
      when(mockContactDetailsConnector.putContactDetails(any(), any())(using any()))
        .thenReturn(Future.successful(HttpResponse(status = Status.BAD_REQUEST, body = downstreamBody)))

      val result = routeJsonRequest(createUpdateContactDetailsRequest(validPayload))

      status(result) shouldBe Status.BAD_REQUEST
      contentAsString(result) shouldBe downstreamBody
    }

    "return 400 for malformed JSON without calling the connector" in {
      reset(mockContactDetailsConnector)

      val request = FakeRequest("PUT", "/senior-accounting-officer/contact-details/123")
        .withHeaders(CONTENT_TYPE -> "application/json")
        .withTextBody("""{"safeId":""")

      val result = routeTextRequest(request)

      status(result) shouldBe Status.BAD_REQUEST
      contentAsJson(result) shouldBe Json.arr(Json.obj("reason" -> "MALFORMED_REQUEST"))
      verify(mockContactDetailsConnector, never()).putContactDetails(any(), any())(using any())
    }

    "return 400 for schema-invalid JSON without calling the connector" in {
      reset(mockContactDetailsConnector)

      val invalidPayload = validPayload ++ Json.arr(
        Json.obj(
          "name"  -> "",
          "email" -> "jane.doe@example.com"
        )
      )

      val result = routeJsonRequest(createUpdateContactDetailsRequest(invalidPayload))

      status(result) shouldBe Status.BAD_REQUEST
      contentAsJson(result) shouldBe Json.arr(
        Json.obj("path" -> "[2].name", "reason" -> "CANNOT_BE_EMPTY"),
        Json.obj("path" -> "body", "reason"     -> "LENGTH_OUT_OF_BOUNDS")
      )
      verify(mockContactDetailsConnector, never()).putContactDetails(any(), any())(using any())
    }
  }
}
