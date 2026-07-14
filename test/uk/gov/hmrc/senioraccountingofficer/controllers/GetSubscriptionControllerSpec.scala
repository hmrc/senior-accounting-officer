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
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.Json
import play.api.mvc.{AnyContentAsEmpty, Result}
import play.api.test.FakeRequest
import play.api.test.Helpers.*
import uk.gov.hmrc.http.HttpResponse
import uk.gov.hmrc.senioraccountingofficer.connectors.GetSubscriptionConnector
import uk.gov.hmrc.senioraccountingofficer.controllers.actions.FakeIdentifierAction.*
import uk.gov.hmrc.senioraccountingofficer.controllers.actions.{FakeIdentifierAction, IdentifierAction}
import uk.gov.hmrc.senioraccountingofficer.utils.TestDataGenerator.*

import scala.concurrent.Future
import scala.util.Random

import java.util.UUID

class GetSubscriptionControllerSpec extends AnyWordSpec with Matchers with GuiceOneAppPerSuite {

  val mockConnector: GetSubscriptionConnector = mock[GetSubscriptionConnector]
  val mockIdentifierAction: IdentifierAction  = mock[IdentifierAction]

  override def fakeApplication(): Application = new GuiceApplicationBuilder()
    .overrides(
      bind[GetSubscriptionConnector].to(mockConnector),
      bind[IdentifierAction].to[FakeIdentifierAction]
    )
    .build()

  private def routeResult(request: FakeRequest[AnyContentAsEmpty.type]): Future[Result] =
    route(app, request) match {
      case Some(value) => value
      case None        => fail("Expected route to be defined")
    }

  def testRequest: FakeRequest[AnyContentAsEmpty.type] =
    FakeRequest(GET, routes.GetSubscriptionController.getSubscription().url)
      .withHeaders("correlationId" -> UUID.randomUUID().toString)

  "GetSubscriptionController" when {
    "correlationId is not sent" must {
      "return a 400 response" in {
        val request = FakeRequest(GET, routes.GetSubscriptionController.getSubscription().url)

        val result = routeResult(request)

        status(result) mustBe BAD_REQUEST
        contentAsString(result) mustBe """[{"reason":"MISSING_CORRELATION_ID"}]"""
      }
    }

    "GetSubscriptionConnector returns a 200 with a valid payload" must {
      "return a 200 response with the response payload" in {

        val dpsStatus = 200
        val dpsBody   =
          s"""{
             |  "etmpSafeId": "${Random.alphanumeric.take(10).mkString}",
             |  "nominatedCompany": {
             |    "crn": "$generateCrn",
             |    "name": "${Random.alphanumeric.take(30).mkString}",
             |    "utr": "$generateUtr"
             |  },
             |  "contacts": [
             |    {
             |      "name": "${Random.alphanumeric.take(30).mkString}",
             |      "email": "${Random.alphanumeric.take(30).mkString}@test.com",
             |      "language": "en",
             |      "status": "valid"
             |    },
             |    {
             |      "name": "${Random.alphanumeric.take(30).mkString}",
             |      "email": "${Random.alphanumeric.take(30).mkString}@test.com",
             |      "language": "cy",
             |      "status": "valid"
             |    }
             |  ]
             |}""".stripMargin

        when(mockConnector.getSubscription(meq(testSaoSubscriptionId))(using any())) thenReturn Future.successful(
          HttpResponse(dpsStatus, dpsBody)
        )

        val request = testRequest

        val result = routeResult(request)

        status(result) mustBe OK
        contentAsJson(result) mustBe Json.parse(dpsBody)
      }
    }

    "GetSubscriptionConnector returns a 200 with an invalid payload" must {
      "return a 502 response" in {

        val dpsStatus = 200
        val dpsBody   = "{}"

        when(mockConnector.getSubscription(meq(testSaoSubscriptionId))(using any())) thenReturn Future.successful(
          HttpResponse(dpsStatus, dpsBody)
        )

        val request = testRequest

        val result = routeResult(request)

        status(result) mustBe BAD_GATEWAY
        contentAsString(result) mustBe """{"reason":"DOWNSTREAM_SERVICE_MISALIGNMENT"}"""
      }
    }

    "GetSubscriptionConnector returns a 204" must {
      "return a 502 response" in {

        val dpsStatus = 204

        when(mockConnector.getSubscription(meq(testSaoSubscriptionId))(using any())) thenReturn Future.successful(
          HttpResponse(dpsStatus, "")
        )

        val request = testRequest

        val result = routeResult(request)

        status(result) mustBe BAD_GATEWAY
        contentAsString(result) mustBe """{"reason":"SUBSCRIPTION_NOT_FOUND"}"""
      }
    }

    "GetSubscriptionConnector returns a 400" must {
      "return a 500 response" in {

        val dpsStatus = 400
        val dpsBody   = "{}"

        when(mockConnector.getSubscription(meq(testSaoSubscriptionId))(using any())) thenReturn Future.successful(
          HttpResponse(dpsStatus, dpsBody)
        )

        val request = testRequest

        val result = routeResult(request)

        status(result) mustBe INTERNAL_SERVER_ERROR
        contentAsString(result) mustBe """{"reason":"DOWNSTREAM_SERVICE_MISALIGNMENT"}"""
      }
    }

    "GetSubscriptionConnector returns a 401" must {
      "return a 500 response" in {

        val dpsStatus = 401
        val dpsBody   = "{}"

        when(mockConnector.getSubscription(meq(testSaoSubscriptionId))(using any())) thenReturn Future.successful(
          HttpResponse(dpsStatus, dpsBody)
        )

        val request = testRequest

        val result = routeResult(request)

        status(result) mustBe INTERNAL_SERVER_ERROR
        contentAsString(result) mustBe """{"reason":"SERVICE_MISCONFIGURATION"}"""
      }
    }

    "GetSubscriptionConnector returns a 403" must {
      "return a 500 response" in {

        val dpsStatus = 403
        val dpsBody   = "{}"

        when(mockConnector.getSubscription(meq(testSaoSubscriptionId))(using any())) thenReturn Future.successful(
          HttpResponse(dpsStatus, dpsBody)
        )

        val request = testRequest

        val result = routeResult(request)

        status(result) mustBe INTERNAL_SERVER_ERROR
        contentAsString(result) mustBe """{"reason":"SERVICE_MISCONFIGURATION"}"""
      }
    }

    "GetSubscriptionConnector returns a 500" must {
      "return a 502 response" in {

        val dpsStatus = 500
        val dpsBody   = "{}"

        when(mockConnector.getSubscription(meq(testSaoSubscriptionId))(using any())) thenReturn Future.successful(
          HttpResponse(dpsStatus, dpsBody)
        )

        val request = testRequest

        val result = routeResult(request)

        status(result) mustBe BAD_GATEWAY
        contentAsString(result) mustBe """{"reason":"DOWNSTREAM_SERVICE_ERROR"}"""
      }
    }

    "GetSubscriptionConnector returns a 503" must {
      "return a 502 response" in {

        val dpsStatus = 503
        val dpsBody   = "{}"

        when(mockConnector.getSubscription(meq(testSaoSubscriptionId))(using any())) thenReturn Future.successful(
          HttpResponse(dpsStatus, dpsBody)
        )

        val request = testRequest

        val result = routeResult(request)

        status(result) mustBe BAD_GATEWAY
        contentAsString(result) mustBe """{"reason":"DOWNSTREAM_SERVICE_UNAVAILABLE"}"""
      }
    }

    "GetSubscriptionConnector returns an unknown status code" must {
      "return a 502 response" in {

        val dpsStatus = 600
        val dpsBody   = "{}"

        when(mockConnector.getSubscription(meq(testSaoSubscriptionId))(using any())) thenReturn Future.successful(
          HttpResponse(dpsStatus, dpsBody)
        )

        val request = testRequest

        val result = routeResult(request)

        status(result) mustBe BAD_GATEWAY
        contentAsString(result) mustBe """{"reason":"DOWNSTREAM_SERVICE_MISALIGNMENT"}"""
      }
    }

  }
}
