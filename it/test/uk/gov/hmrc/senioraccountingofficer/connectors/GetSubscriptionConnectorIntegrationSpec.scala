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

package uk.gov.hmrc.senioraccountingofficer.connectors

import com.github.tomakehurst.wiremock.client.WireMock.*
import play.api.http.HeaderNames
import support.ISpecBase
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.senioraccountingofficer.config.AppConfig
import uk.gov.hmrc.senioraccountingofficer.connectors.GetSubscriptionConnectorIntegrationSpec.*

class GetSubscriptionConnectorIntegrationSpec extends ISpecBase {

  private val appConfig = app.injector.instanceOf[AppConfig]
  private val connector = app.injector.instanceOf[GetSubscriptionConnector]

  implicit val hc: HeaderCarrier = HeaderCarrier()

  override def additionalConfigs: Map[String, Any] = Map(
    "microservice.services.hip.host" -> wireMockHost,
    "microservice.services.hip.port" -> wireMockPort
  )

  "GetSubscriptionConnector" must {
    Seq(200, 204, 400, 401, 403, 500, 503).foreach { expectedStatus =>
      s"return a Future.successful(HttpResponse) for a $expectedStatus response from HIP" in {
        stubFor(
          get(urlEqualTo(s"/iv_subscriptions/$testSaoSubscriptionId"))
            .willReturn(
              aResponse()
                .withStatus(expectedStatus)
                .withBody(testResponse)
            )
        )

        val result = connector.getSubscription(testSaoSubscriptionId).futureValue

        result.status mustBe expectedStatus
        if expectedStatus == 204 then result.body mustBe ""
        else result.body mustBe testResponse

        verify(
          1,
          getRequestedFor(urlEqualTo(s"/iv_subscriptions/$testSaoSubscriptionId"))
            .withHeader(HeaderNames.AUTHORIZATION, equalTo(appConfig.hipAuthorisationCredentials))
        )
      }
    }

  }
}

object GetSubscriptionConnectorIntegrationSpec {
  val testSaoSubscriptionId = "testSaoSubscriptionId"
  val testResponse          = "{}"
}
