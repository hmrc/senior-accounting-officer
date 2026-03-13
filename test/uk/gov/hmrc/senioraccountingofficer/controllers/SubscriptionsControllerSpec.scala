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
import org.mockito.Mockito.{reset, when}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.mockito.MockitoSugar.mock
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Application
import play.api.http.Status
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
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

  "PUT /subscriptions" should {
    "return 204 when the downstream connector succeeds without a body" in {
      reset(mockSubscriptionsConnector)
      when(mockSubscriptionsConnector.putSubscription(any())(using any()))
        .thenReturn(Future.successful(HttpResponse(status = Status.NO_CONTENT)))

      val request = FakeRequest("PUT", "/senior-accounting-officer/subscriptions")
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
      reset(mockSubscriptionsConnector)
      when(mockSubscriptionsConnector.putSubscription(any())(using any()))
        .thenReturn(Future.successful(HttpResponse(status = Status.BAD_REQUEST, body = downstreamBody)))

      val request = FakeRequest("PUT", "/senior-accounting-officer/subscriptions")
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
}
