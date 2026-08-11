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
import play.api.http.{HeaderNames, MimeTypes, Status}
import support.ISpecBase
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.senioraccountingofficer.config.AppConfig
import uk.gov.hmrc.senioraccountingofficer.models.dps.CertificateDpsRequest
import uk.gov.hmrc.senioraccountingofficer.models.CertificateCompany
import play.api.libs.json.Json

class CertificateConnectorIntegrationSpec extends ISpecBase {

  private val appConfig = app.injector.instanceOf[AppConfig]
  private val connector = app.injector.instanceOf[CertificateConnector]

  given HeaderCarrier = HeaderCarrier()

  override def additionalConfigs: Map[String, Any] = Map(
    "microservice.services.hip.host" -> wireMockHost,
    "microservice.services.hip.port" -> wireMockPort
  )

  val testSubscriptionId = "testSubscriptionId"

  private val validPayload = CertificateDpsRequest(
    submitterName = Some("Firstname Lastname"),
    saoName = "Firstname Lastname",
    saoEmail = "firstname.lastname@example.com",
    staffPid = Some("non-empty string"),
    customerId = Some("non-empty string"),
    companies = List(
      CertificateCompany(
        crn = Some("crn"),
        utr = "utr",
        name = "Example Subsidiary Ltd",
        accPeriodEnd = "2025-03-31",
        status = "COMPLIANT",
        `type` = "LTD",
        isCorporationTaxQualified = true,
        isVatQualified = true,
        isPayeQualified = true,
        isInsurancePremiumTaxQualified = false,
        isStampDutyLandTaxQualified = false,
        isStampDutyReserveTaxQualified = false,
        isPetroleumRevenueTaxQualified = false,
        isCustomsDutiesQualified = false,
        isExciseDutiesQualified = false,
        isBankLevyQualified = false,
        qualificationStatement = Some("non-empty string")
      )
    ),
    remarks = Some("non-empty string")
  )

  "postCertificate" must {
    "pass through a successful downstream response" in {
      stubFor(
        post(urlEqualTo(s"/dapm/subscriptions/$testSubscriptionId/certificates"))
          .willReturn(
            aResponse()
              .withStatus(Status.CREATED)
          )
      )

      val result = connector.postCertificate(testSubscriptionId, validPayload).futureValue

      result.status mustBe Status.CREATED

      verify(
        1,
        postRequestedFor(urlEqualTo(s"/dapm/subscriptions/$testSubscriptionId/certificates"))
          .withHeader(HeaderNames.AUTHORIZATION, equalTo(appConfig.hipAuthorisationCredentials))
          .withHeader(HeaderNames.CONTENT_TYPE, equalTo(MimeTypes.JSON))
          .withRequestBody(equalTo(Json.stringify(Json.toJson(validPayload))))
      )
    }

    "pass through a downstream validation error body" in {
      val downstreamBody = """[{"path":"companies[0].utr","reason":"INVALID_FORMAT"}]"""

      stubFor(
        post(urlEqualTo(s"/dapm/subscriptions/$testSubscriptionId/certificates"))
          .willReturn(
            aResponse()
              .withStatus(Status.BAD_REQUEST)
              .withBody(downstreamBody)
          )
      )

      val result = connector.postCertificate(testSubscriptionId, validPayload).futureValue

      result.status mustBe Status.BAD_REQUEST
      result.body mustBe downstreamBody

      verify(
        1,
        postRequestedFor(urlEqualTo(s"/dapm/subscriptions/$testSubscriptionId/certificates"))
          .withHeader(HeaderNames.AUTHORIZATION, equalTo(appConfig.hipAuthorisationCredentials))
          .withHeader(HeaderNames.CONTENT_TYPE, equalTo(MimeTypes.JSON))
          .withRequestBody(equalToJson(Json.stringify(Json.toJson(validPayload))))
      )
    }
  }
}
