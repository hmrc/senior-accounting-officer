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

package uk.gov.hmrc.senioraccountingofficer

import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{reset, when}
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.mockito.MockitoSugar.mock
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import play.api.Application
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.Json
import play.api.libs.ws.WSClient
import play.api.libs.ws.readableAsString
import play.api.libs.ws.writeableOf_JsValue
import uk.gov.hmrc.http.HttpResponse
import uk.gov.hmrc.senioraccountingofficer.connectors.SubscriptionsConnector

import scala.concurrent.Future

class SubscriptionsIntegrationSpec
    extends AnyWordSpec
    with Matchers
    with ScalaFutures
    with IntegrationPatience
    with GuiceOneServerPerSuite {

  private val mockSubscriptionsConnector = mock[SubscriptionsConnector]
  private val wsClient                   = app.injector.instanceOf[WSClient]
  private val baseUrl                    = s"http://localhost:$port"
  private val validPayload               = Json.obj(
    "safeId"  -> "XE000123456789",
    "company" -> Json.obj(
      "companyName"               -> "Acme Manufacturing Ltd",
      "uniqueTaxReference"        -> "1234567890",
      "companyRegistrationNumber" -> "OC123456"
    ),
    "contacts" -> Json.arr(
      Json.obj("name" -> "Jane Doe", "email" -> "jane.doe@example.com")
    )
  )

  override def fakeApplication(): Application =
    GuiceApplicationBuilder()
      .overrides(
        bind[SubscriptionsConnector].toInstance(mockSubscriptionsConnector)
      )
      .build()

  "PUT /subscriptions" should {
    "return 204 when the downstream connector succeeds without a body" in {
      reset(mockSubscriptionsConnector)
      when(mockSubscriptionsConnector.putSubscription(any())(using any()))
        .thenReturn(Future.successful(HttpResponse(status = 204)))

      val response =
        wsClient
          .url(s"$baseUrl/senior-accounting-officer/subscriptions")
          .put(validPayload)
          .futureValue

      response.status shouldBe 204
      response.body shouldBe ""
    }

    "return the downstream JSON error body for a validation failure" in {
      val downstreamBody = """[{"path":"safeId","reason":"INVALID_FORMAT"}]"""
      reset(mockSubscriptionsConnector)
      when(mockSubscriptionsConnector.putSubscription(any())(using any()))
        .thenReturn(Future.successful(HttpResponse(status = 400, body = downstreamBody)))

      val response =
        wsClient
          .url(s"$baseUrl/senior-accounting-officer/subscriptions")
          .put(validPayload)
          .futureValue

      response.status shouldBe 400
      response.body shouldBe downstreamBody
    }
  }
}
