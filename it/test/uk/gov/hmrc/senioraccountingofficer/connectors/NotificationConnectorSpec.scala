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
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.Json
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.senioraccountingofficer.models.{Company, NotificationRequest, SeniorAccountingOfficer}
import uk.gov.hmrc.http.test.WireMockSupport

import java.time.LocalDate

class NotificationConnectorSpec
  extends AnyWordSpec
    with Matchers
    with GuiceOneAppPerSuite
    with WireMockSupport
    with ScalaFutures {

  override lazy val app: Application = new GuiceApplicationBuilder()
    .configure(
      "microservice.services.notification.port" -> wireMockServer.port()
    )
    .build()

  lazy val connector: NotificationConnector = app.injector.instanceOf[NotificationConnector]

  "postNotification" should {

    "return an HttpResponse with status 200 when downstream returns 200" in {
      val request = NotificationRequest(
        companies = Seq(
          Company(
            companyName = "Example Ltd",
            uniqueTaxReference = "1234567890",
            companyReferenceNumber = "AB123456",
            companyType = "LTD",
            financialYearEndDate = LocalDate.parse("2024-12-31"),
            seniorAccountingOfficers = Seq(
              SeniorAccountingOfficer(
                name = "Firstname Lastname",
                email = "Firstname.Lastname@example.com",
                startDate = LocalDate.parse("2024-04-01"),
                endDate = LocalDate.parse("2025-03-31")
              )
            )
          )
        ),
        additionalInformation = Some("non-empty string")
      )

      wireMockServer.stubFor(
        post(urlEqualTo("/notification/123"))
          .withRequestBody(equalToJson(Json.toJson(request).toString()))
          .willReturn(
            aResponse()
              .withStatus(200)
              .withBody("Success")
          )
      )

      implicit val hc: HeaderCarrier = HeaderCarrier()
      val result  = connector.postNotification("123", request).futureValue

      result.status shouldBe 200
      result.body shouldBe "Success"
    }
  }
}