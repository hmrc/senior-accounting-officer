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
import uk.gov.hmrc.senioraccountingofficer.connectors.CrmmConnectorIntegrationSpec.*
import uk.gov.hmrc.senioraccountingofficer.models.crmm.RetrieveCustomerRequest
import uk.gov.hmrc.senioraccountingofficer.utils.TestDataGenerator.*
import play.api.libs.json.Json

class CrmmConnectorIntegrationSpec extends ISpecBase {

  private val appConfig = app.injector.instanceOf[AppConfig]
  private val connector = app.injector.instanceOf[CrmmConnector]

  implicit val hc: HeaderCarrier = HeaderCarrier()

  override def additionalConfigs: Map[String, Any] = Map(
    "microservice.services.hip.host" -> wireMockHost,
    "microservice.services.hip.port" -> wireMockPort
  )

  "CrmmConnector" must {
    "return a stubbed response when the crmm feature toggle is disabled" in {
      AppConfig.setValue("feature-toggles.crmm", "false")

      val request = RetrieveCustomerRequest(Some(generateCrn), Some(generateUtr))

      val result = connector.retrieveCustomer(request).futureValue

      result.status mustBe 200
      result.body mustBe Json
        .obj(
          "existingCustomer" -> false,
          "errorDescription" -> "Customer not found (stubbed)",
          "status"           -> "Failure"
        )
        .toString

      verify(
        0,
        postRequestedFor(urlEqualTo("/compliance/civil-investigation-and-avoidance/api/customer/v1/retrievecustomer"))
          .withHeader(HeaderNames.AUTHORIZATION, equalTo(appConfig.hipAuthorisationCredentials))
      )
    }

    Seq(200, 400, 401, 403, 500, 503).foreach { expectedStatus =>
      s"return a $expectedStatus response from the service when the crmm feature toggle is enabled" in {
        AppConfig.setValue("feature-toggles.crmm", "true")

        stubFor(
          post(urlEqualTo("/compliance/civil-investigation-and-avoidance/api/customer/v1/retrievecustomer"))
            .willReturn(
              aResponse()
                .withStatus(expectedStatus)
                .withBody(testResponse)
            )
        )

        val request = RetrieveCustomerRequest(Some(generateCrn), Some(generateUtr))

        val result = connector.retrieveCustomer(request).futureValue

        result.status mustBe expectedStatus
        result.body mustBe testResponse

        verify(
          1,
          postRequestedFor(urlEqualTo("/compliance/civil-investigation-and-avoidance/api/customer/v1/retrievecustomer"))
            .withHeader(HeaderNames.AUTHORIZATION, equalTo(appConfig.hipAuthorisationCredentials))
        )
      }
    }
  }
}

object CrmmConnectorIntegrationSpec {
  val testSaoSubscriptionId = "testSaoSubscriptionId"
  val testResponse          = Json
    .obj(
      "customerId"       -> "customer id",
      "existingCustomer" -> true,
      "status"           -> "Success"
    )
    .toString
}
