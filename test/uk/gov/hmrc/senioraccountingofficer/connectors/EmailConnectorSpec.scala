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

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.*
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.options
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Application
import play.api.http.{HeaderNames, MimeTypes, Status}
import play.api.inject.guice.GuiceApplicationBuilder
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.senioraccountingofficer.models.EmailTemplate.NotificationConfirmation
import uk.gov.hmrc.senioraccountingofficer.models.{NotificationEmail, NotificationEmailParameters}

class EmailConnectorSpec
    extends AnyWordSpec
    with Matchers
    with ScalaFutures
    with IntegrationPatience
    with GuiceOneAppPerSuite
    with BeforeAndAfterAll {

  private val wireMockServer = WireMockServer(options().dynamicPort())

  override def fakeApplication(): Application = {
    wireMockServer.start()

    GuiceApplicationBuilder()
      .configure(
        "microservice.services.email.protocol" -> "http",
        "microservice.services.email.host"     -> "localhost",
        "microservice.services.email.port"     -> wireMockServer.port()
      )
      .build()
  }

  override def afterAll(): Unit = {
    wireMockServer.stop()
    super.afterAll()
  }

  private given HeaderCarrier = HeaderCarrier()
  private lazy val connector  = app.injector.instanceOf[EmailConnector]

  "postEmail" should {
    "post the notification email to the HMRC domain" in {
      val parameters = NotificationEmailParameters(
        recipientName = "name",
        companyName = "companyName",
        submittedDateTime = "17 January 2025 at 11:45am",
        referenceId = "abc"
      )
      val request = NotificationEmail(
        to = List("email@example.com"),
        templateId = NotificationConfirmation,
        parameters = parameters
      )

      val expectedRequestBody = """{
          |  "to": ["email@example.com"],
          |  "templateId": "dsao_notification_confirmation",
          |  "parameters": {
          |    "recipientName": "name",
          |    "companyName": "companyName",
          |    "submittedDateTime": "17 January 2025 at 11:45am",
          |    "referenceId": "abc"
          |  }
          |}""".stripMargin

      wireMockServer.stubFor(
        post(urlEqualTo("/hmrc/email"))
          .withHeader(HeaderNames.CONTENT_TYPE, containing(MimeTypes.JSON))
          .withRequestBody(equalToJson(expectedRequestBody))
          .willReturn(aResponse().withStatus(Status.ACCEPTED))
      )
      connector.postEmail(request).futureValue.status shouldBe Status.ACCEPTED
    }
  }
}
