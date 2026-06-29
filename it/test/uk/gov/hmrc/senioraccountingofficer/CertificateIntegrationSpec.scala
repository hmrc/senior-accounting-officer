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
import play.api.http.{HeaderNames, MimeTypes}
import play.api.libs.json.Json
import play.api.libs.ws.readableAsString
import play.api.libs.ws.writeableOf_JsValue
import support.ISpecBase
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.senioraccountingofficer.config.AppConfig
import uk.gov.hmrc.senioraccountingofficer.connectors.CertificateConnector

class CertificateIntegrationSpec extends ISpecBase {

  private val appConfig         = app.injector.instanceOf[AppConfig]
  private val connector = app.injector.instanceOf[CertificateConnector]
  private val saoSubscriptionId = "123"

  implicit val hc: HeaderCarrier = HeaderCarrier()


  override def additionalConfigs: Map[String, Any] = Map(
    "microservice.services.senior-accounting-officer-stubs.host" -> wireMockHost,
    "microservice.services.senior-accounting-officer-stubs.port" -> wireMockPort
  )

  private val validPayload = """{
                                |  "submitterName": "Jane Smith",
                                |  "SAOName": "Jane Smith",
                                |  "SAOEmail": "jane.smith@example.com",
                                |  "companies": [
                                |    {
                                |      "crn": "1234567890",
                                |      "utr": "AB123456",
                                |      "name": "Example Subsidiary Ltd",
                                |      "accPeriodEnd": "2025-03-31",
                                |      "status": "COMPLIANT",
                                |      "type": "LTD",
                                |      "isCorporationTaxQualified": true,
                                |      "isVatQualified": true,
                                |      "isPayeQualified": true,
                                |      "isInsurancePremiumTaxQualified": false,
                                |      "isStampDutyLandTaxQualified": false,
                                |      "isStampDutyReserveTaxQualified": false,
                                |      "isPetroleumRevenueTaxQualified": false,
                                |      "isCustomsDutiesQualified": false,
                                |      "isExciseDutiesQualified": false,
                                |      "isBankLevyQualified": false
                                |    }
                                |  ],
                                |  "remarks": "non-empty string"
                                |}""".stripMargin


  "POST /certificate" must {
    "pass through a successful downstream response" in {
      stubFor(
        post(urlEqualTo(s"/subscriptions/$saoSubscriptionId/certificates"))
          .willReturn(
            aResponse()
              .withStatus(200)
          )
      )

      val result = connector.postCertificate("123", validPayload).futureValue

      result.status mustBe 200

      verify(
        1,
        postRequestedFor(urlEqualTo("/subscriptions/123/certificates"))
          .withHeader(HeaderNames.AUTHORIZATION, equalTo(appConfig.hipAuthorisationCredentials))
          .withHeader(HeaderNames.CONTENT_TYPE, equalTo(MimeTypes.JSON))
          .withRequestBody(equalToJson(validPayload))
      )
    }

    "pass through a downstream validation error body" in {
      val downstreamBody = """[{"path":"companies[0].utr","reason":"INVALID_FORMAT"}]"""

      stubFor(
        post(urlEqualTo(s"/subscriptions/$saoSubscriptionId/certificates"))
          .willReturn(
            aResponse()
              .withStatus(400)
              .withBody(downstreamBody)
          )
      )

      val result = connector.postCertificate("123", validPayload).futureValue

      result.status mustBe 400
      result.body mustBe downstreamBody

      verify(
        1,
        postRequestedFor(urlEqualTo("/subscriptions/123/certificates"))
          .withHeader(HeaderNames.AUTHORIZATION, equalTo(appConfig.hipAuthorisationCredentials))
          .withHeader(HeaderNames.CONTENT_TYPE, equalTo(MimeTypes.JSON))
          .withRequestBody(equalToJson(validPayload))
      )
    }
  }
}
