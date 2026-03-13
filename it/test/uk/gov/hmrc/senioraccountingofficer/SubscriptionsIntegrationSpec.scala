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

import com.github.tomakehurst.wiremock.client.WireMock.*
import play.api.http.HeaderNames
import play.api.libs.json.Json
import play.api.libs.ws.readableAsString
import play.api.libs.ws.writeableOf_JsValue
import support.ISpecBase
import uk.gov.hmrc.senioraccountingofficer.config.AppConfig

class SubscriptionsIntegrationSpec extends ISpecBase {

  private val appConfig = app.injector.instanceOf[AppConfig]

  override def additionalConfigs: Map[String, Any] = Map(
    "microservice.services.senior-accounting-officer-stubs.host" -> wireMockHost,
    "microservice.services.senior-accounting-officer-stubs.port" -> wireMockPort
  )

  private val validPayload = Json.obj(
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

  "PUT /subscriptions" must {
    "pass through a successful downstream response" in {
      stubFor(
        put(urlEqualTo("/subscriptions"))
          .willReturn(
            aResponse()
              .withStatus(204)
          )
      )

      val response =
        wsClient
          .url(s"$baseUrl/senior-accounting-officer/subscriptions")
          .put(validPayload)
          .futureValue

      response.status mustBe 204
      response.body mustBe ""

      verify(
        1,
        putRequestedFor(urlEqualTo("/subscriptions"))
          .withHeader(HeaderNames.AUTHORIZATION, equalTo(appConfig.hipAuthorisationCredentials))
          .withHeader(HeaderNames.CONTENT_TYPE, containing("application/json"))
          .withRequestBody(equalToJson(validPayload.toString))
      )
    }

    "pass through a downstream validation error body" in {
      val downstreamBody = """[{"path":"safeId","reason":"INVALID_FORMAT"}]"""

      stubFor(
        put(urlEqualTo("/subscriptions"))
          .willReturn(
            aResponse()
              .withStatus(400)
              .withBody(downstreamBody)
          )
      )

      val response =
        wsClient
          .url(s"$baseUrl/senior-accounting-officer/subscriptions")
          .put(validPayload)
          .futureValue

      response.status mustBe 400
      response.body mustBe downstreamBody

      verify(
        1,
        putRequestedFor(urlEqualTo("/subscriptions"))
          .withHeader(HeaderNames.AUTHORIZATION, equalTo(appConfig.hipAuthorisationCredentials))
          .withRequestBody(equalToJson(validPayload.toString))
      )
    }
  }
}
