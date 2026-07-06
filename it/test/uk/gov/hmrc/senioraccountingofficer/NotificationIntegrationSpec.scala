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
import support.ISpecBase
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.senioraccountingofficer.config.AppConfig
import uk.gov.hmrc.senioraccountingofficer.connectors.NotificationConnector
import uk.gov.hmrc.senioraccountingofficer.models.dps.{Company, NotificationDpsRequest, Sao}

class NotificationIntegrationSpec extends ISpecBase {

  private val appConfig = app.injector.instanceOf[AppConfig]
  private val connector = app.injector.instanceOf[NotificationConnector]

  implicit val hc: HeaderCarrier = HeaderCarrier()

  override def additionalConfigs: Map[String, Any] = Map(
    "microservice.services.senior-accounting-officer-stubs.host" -> wireMockHost,
    "microservice.services.senior-accounting-officer-stubs.port" -> wireMockPort
  )

  private val request =
    NotificationDpsRequest(
      companies = List(
        Company(
          crn = Some("AB123456"),
          utr = "1234567890",
          name = "Example Ltd",
          accPeriodEnd = "2024-12-31",
          status = "Active",
          `type` = "LTD"
        )
      ),
      saos = List(
        Sao(
          name = "Firstname Lastname",
          email = Some("Firstname.Lastname@example.com"),
          fromDate = Some("2024-04-01"),
          toDate = Some("2025-03-31")
        )
      ),
      remarks = Some("non-empty string")
    )

  private val requestJson = """{
                               |  "companies": [
                               |    {
                               |      "name": "Example Ltd",
                               |      "utr": "1234567890",
                               |      "crn": "AB123456",
                               |      "type": "LTD",
                               |      "status": "Active",
                               |      "accPeriodEnd": "2024-12-31"
                               |     }
                               |    ],
                               |    "saos": [
                               |        {
                               |          "name": "Firstname Lastname",
                               |          "email": "Firstname.Lastname@example.com",
                               |          "fromDate": "2024-04-01",
                               |          "toDate": "2025-03-31"
                               |        }
                               |  ],
                               |  "remarks": "non-empty string"
                               |}""".stripMargin

  "postNotification" must {

    "pass through a successful downstream response" in {
      stubFor(
        post(urlEqualTo("/subscriptions/123/notifications"))
          .willReturn(
            aResponse()
              .withStatus(200)
          )
      )

      val result = connector.postNotification("123", request).futureValue

      result.status mustBe 200

      verify(
        1,
        postRequestedFor(urlEqualTo("/subscriptions/123/notifications"))
          .withHeader(HeaderNames.AUTHORIZATION, equalTo(appConfig.hipAuthorisationCredentials))
          .withHeader(HeaderNames.CONTENT_TYPE, equalTo(MimeTypes.JSON))
          .withRequestBody(equalToJson(requestJson))
      )
    }

    "pass through a downstream validation error body" in {
      val downstreamBody = """[{"path":"companies[0].utr","reason":"INVALID_FORMAT"}]"""

      stubFor(
        post(urlEqualTo("/subscriptions/123/notifications"))
          .willReturn(
            aResponse()
              .withStatus(400)
              .withBody(downstreamBody)
          )
      )

      val result = connector.postNotification("123", request).futureValue

      result.status mustBe 400
      result.body mustBe downstreamBody

      verify(
        1,
        postRequestedFor(urlEqualTo("/subscriptions/123/notifications"))
          .withHeader(HeaderNames.AUTHORIZATION, equalTo(appConfig.hipAuthorisationCredentials))
          .withHeader(HeaderNames.CONTENT_TYPE, equalTo(MimeTypes.JSON))
          .withRequestBody(equalToJson(requestJson))
      )
    }
  }
}
