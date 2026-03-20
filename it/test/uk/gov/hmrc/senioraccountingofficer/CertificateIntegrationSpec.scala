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

class CertificateIntegrationSpec extends ISpecBase {

  private val appConfig         = app.injector.instanceOf[AppConfig]
  private val saoSubscriptionId = "123"

  override def additionalConfigs: Map[String, Any] = Map(
    "microservice.services.senior-accounting-officer-stubs.host" -> wireMockHost,
    "microservice.services.senior-accounting-officer-stubs.port" -> wireMockPort
  )

  private val validPayload = Json.obj(
    "declaration" -> Json.obj(
      "seniorAccountingOfficer" -> Json.obj(
        "name"  -> "John Doe",
        "email" -> "john.doe@example.com"
      ),
      "proxy" -> Json.obj(
        "name" -> "Jane Smith"
      )
    ),
    "companies" -> Json.arr(
      Json.obj(
        "companyName"            -> "Example Ltd",
        "uniqueTaxReference"     -> "1234567890",
        "companyReferenceNumber" -> "AB123456",
        "companyType"            -> "LTD",
        "financialYearEndDate"   -> "2024-12-31",
        "seniorAccountingOfficers" -> Json.arr(
          Json.obj(
            "name"      -> "Firstname Lastname",
            "email"     -> "firstname.lastname@example.com",
            "startDate" -> "2024-04-01",
            "endDate"   -> "2025-03-31"
          )
        )
      )
    ),
    "additionalInformation" -> "non-empty string"
  )

  "POST /certificate/:saoSubscriptionId" must {
    "pass through a successful downstream response" in {
      stubFor(
        post(urlEqualTo(s"/certificate/$saoSubscriptionId"))
          .willReturn(
            aResponse()
              .withStatus(204)
          )
      )

      val response =
        wsClient
          .url(s"$baseUrl/senior-accounting-officer/certificate/$saoSubscriptionId")
          .post(validPayload)
          .futureValue

      response.status mustBe 204
      response.body mustBe ""

      verify(
        1,
        postRequestedFor(urlEqualTo(s"/certificate/$saoSubscriptionId"))
          .withHeader(HeaderNames.AUTHORIZATION, equalTo(appConfig.hipAuthorisationCredentials))
          .withHeader(HeaderNames.CONTENT_TYPE, containing("application/json"))
          .withRequestBody(equalToJson(validPayload.toString))
      )
    }

    "pass through a downstream validation error body" in {
      val downstreamBody = """[{"path":"companies[0].companyType","reason":"INVALID_ENUM_VALUE"}]"""

      stubFor(
        post(urlEqualTo(s"/certificate/$saoSubscriptionId"))
          .willReturn(
            aResponse()
              .withStatus(400)
              .withBody(downstreamBody)
          )
      )

      val response =
        wsClient
          .url(s"$baseUrl/senior-accounting-officer/certificate/$saoSubscriptionId")
          .post(validPayload)
          .futureValue

      response.status mustBe 400
      response.body mustBe downstreamBody

      verify(
        1,
        postRequestedFor(urlEqualTo(s"/certificate/$saoSubscriptionId"))
          .withHeader(HeaderNames.AUTHORIZATION, equalTo(appConfig.hipAuthorisationCredentials))
          .withRequestBody(equalToJson(validPayload.toString))
      )
    }
  }
}
