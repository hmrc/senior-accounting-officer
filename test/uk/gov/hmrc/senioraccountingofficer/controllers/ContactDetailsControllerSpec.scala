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
import uk.gov.hmrc.senioraccountingofficer.controllers.ContactDetailsControllerSpec.createUpdateContactDetailsRequest
import play.api.http.Writeable

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

  private def setupMocks(expectedId: String, expectedStatus: Int, expectedBody: String): Unit = {
    reset(mockContactDetailsConnector)
    when(mockContactDetailsConnector.getContactDetails(meq(expectedId))(using any())) thenReturn Future
      .successful(
        HttpResponse(expectedStatus, expectedBody)
      )
    when(mockContactDetailsConnector.putContactDetails(meq(expectedId), any())(using any()))
      .thenReturn(Future.successful(HttpResponse(expectedStatus, expectedBody)))
  }

  def routeRequest[A: Writeable](request: FakeRequest[A]): Future[Result] =
    route(app, request) match {
      case Some(result) => result
      case None         => fail("Expected route to be defined")
    }

  val saoSubscriptionId        = "123"
  val invalidSaoSubscriptionId = "456"

  "GET /contact-details" should {
    "return a 200 response with the body from the connector" in {
      val expectedStatus = 200
      val expectedBody   = "{}"

      setupMocks(saoSubscriptionId, expectedStatus, expectedBody)

      val url = routes.ContactDetailsController.getContactDetails(saoSubscriptionId).url

      val result = routeRequest(FakeRequest(GET, url))

      status(result) shouldBe expectedStatus
      contentAsString(result) shouldBe expectedBody
    }

    "return a 404 response with the body from the connector" in {
      val expectedStatus = 404
      val expectedBody   = "Entity not found"

      setupMocks(invalidSaoSubscriptionId, expectedStatus, expectedBody)

      val url = routes.ContactDetailsController.getContactDetails(invalidSaoSubscriptionId).url

      val result = routeRequest(FakeRequest(GET, url))

      status(result) shouldBe expectedStatus
      contentAsString(result) shouldBe expectedBody
    }
  }

  "PUT /contactDetails" should {
    "return 204 when the downstream connector succeeds without a body" in {
      setupMocks(saoSubscriptionId, Status.NO_CONTENT, "")

      val request = createUpdateContactDetailsRequest(saoSubscriptionId, validPayload.toString)

      val result = routeRequest(request)

      status(result) shouldBe Status.NO_CONTENT
    }

    "return the downstream JSON body for validation errors" in {
      val downstreamBody = """[{"path":"safeId","reason":"INVALID_FORMAT"}]"""
      setupMocks(saoSubscriptionId, Status.BAD_REQUEST, downstreamBody)

      val result = routeRequest(createUpdateContactDetailsRequest(saoSubscriptionId, validPayload.toString))

      status(result) shouldBe Status.BAD_REQUEST
      contentAsString(result) shouldBe downstreamBody
    }

    "return 400 with MALFORMED_REQUEST for malformed JSON without calling the connector" in {
      reset(mockContactDetailsConnector)

      val request = createUpdateContactDetailsRequest(saoSubscriptionId, """{"safeId":""")

      val result = routeRequest(request)

      status(result) shouldBe Status.BAD_REQUEST
      contentAsJson(result) shouldBe Json.arr(Json.obj("reason" -> "MALFORMED_REQUEST"))
      verify(mockContactDetailsConnector, never()).putContactDetails(any(), any())(using any())
    }

    "return 400 with MISSING_REQUIRED_FIELD for json without a required field without calling the connector" in {
      reset(mockContactDetailsConnector)

      val invalidPayload = Json.arr(
        Json.obj(
          "email" -> "jane.doe@example.com"
        )
      )

      val result = routeRequest(createUpdateContactDetailsRequest(saoSubscriptionId, invalidPayload.toString))

      status(result) shouldBe Status.BAD_REQUEST
      contentAsJson(result) shouldBe Json.arr(
        Json.obj("path" -> "[0].name", "reason" -> "MISSING_REQUIRED_FIELD")
      )
      verify(mockContactDetailsConnector, never()).putContactDetails(any(), any())(using any())
    }

    "return 400 with CANNOT_BE_EMPTY for empty contact name without calling the connector" in {
      reset(mockContactDetailsConnector)

      val invalidPayload = Json.arr(
        Json.obj(
          "name"  -> "",
          "email" -> "jane.doe@example.com"
        )
      )

      val result = routeRequest(createUpdateContactDetailsRequest(saoSubscriptionId, invalidPayload.toString))

      status(result) shouldBe Status.BAD_REQUEST
      contentAsJson(result) shouldBe Json.arr(
        Json.obj("path" -> "[0].name", "reason" -> "CANNOT_BE_EMPTY")
      )
      verify(mockContactDetailsConnector, never()).putContactDetails(any(), any())(using any())
    }

    "return 400 with INVALID_DATA_TYPE for a non-string contact name without calling the connector" in {
      reset(mockContactDetailsConnector)

      val invalidPayload = Json.arr(
        Json.obj(
          "name"  -> 123,
          "email" -> "jane.doe@example.com"
        )
      )

      val result = routeRequest(createUpdateContactDetailsRequest(saoSubscriptionId, invalidPayload.toString))

      status(result) shouldBe Status.BAD_REQUEST
      contentAsJson(result) shouldBe Json.arr(
        Json.obj("path" -> "[0].name", "reason" -> "INVALID_DATA_TYPE")
      )
      verify(mockContactDetailsConnector, never()).putContactDetails(any(), any())(using any())
    }

    "return 400 with ARRAY_MIN_ITEMS_NOT_MET for empty contacts without calling the connector" in {
      reset(mockContactDetailsConnector)

      val invalidPayload = Json.arr()

      val result = routeRequest(createUpdateContactDetailsRequest(saoSubscriptionId, invalidPayload.toString))

      status(result) shouldBe Status.BAD_REQUEST
      contentAsJson(result) shouldBe Json.arr(
        Json.obj("path" -> "body", "reason" -> "ARRAY_MIN_ITEMS_NOT_MET")
      )
      verify(mockContactDetailsConnector, never()).putContactDetails(any(), any())(using any())
    }

    "return 400 with LENGTH_OUT_OF_BOUNDS for too many contacts without calling the connector" in {
      reset(mockContactDetailsConnector)

      val invalidPayload = Json.arr(
        Json.obj(
          "name"  -> "jane",
          "email" -> "jane.doe@example.com"
        ),
        Json.obj(
          "name"  -> "jane",
          "email" -> "jane.doe@example.com"
        ),
        Json.obj(
          "name"  -> "jane",
          "email" -> "jane.doe@example.com"
        )
      )

      val result = routeRequest(createUpdateContactDetailsRequest(saoSubscriptionId, invalidPayload.toString))

      status(result) shouldBe Status.BAD_REQUEST
      contentAsJson(result) shouldBe Json.arr(
        Json.obj("path" -> "body", "reason" -> "LENGTH_OUT_OF_BOUNDS")
      )
      verify(mockContactDetailsConnector, never()).putContactDetails(any(), any())(using any())
    }

    "return 400 with LENGTH_OUT_OF_BOUNDS for a name over 160 chars without calling the connector" in {
      reset(mockContactDetailsConnector)

      val invalidPayload = Json.arr(
        Json.obj(
          "name"  -> ("A" * 161),
          "email" -> "jane.doe@example.com"
        )
      )

      val result = routeRequest(createUpdateContactDetailsRequest(saoSubscriptionId, invalidPayload.toString))

      status(result) shouldBe Status.BAD_REQUEST
      contentAsJson(result) shouldBe Json.arr(
        Json.obj("path" -> "[0].name", "reason" -> "LENGTH_OUT_OF_BOUNDS")
      )
      verify(mockContactDetailsConnector, never()).putContactDetails(any(), any())(using any())
    }
  }
}

object ContactDetailsControllerSpec {
  def createUpdateContactDetailsRequest(id: String, payload: String): FakeRequest[AnyContentAsText] = {
    FakeRequest("PUT", s"/senior-accounting-officer/contact-details/$id")
      .withHeaders(CONTENT_TYPE -> "application/json")
      .withTextBody(payload.toString())
  }
}
