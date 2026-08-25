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
import uk.gov.hmrc.senioraccountingofficer.models.EmailTemplate.{
  NotificationConfirmation
}
import uk.gov.hmrc.senioraccountingofficer.models.{
  NotificationEmail,
  NotificationEmailParameters,
  SaoCertificateEmail,
  SaoCertificateEmailParameters,
  SubmitterCertificateEmail,
  SubmitterCertificateEmailParameters
}

import java.util.UUID

class EmailConnectorIntegrationSpec extends ISpecBase {

  private val connector     = app.injector.instanceOf[EmailConnector]
  private val correlationId = UUID.randomUUID().toString

  implicit val hc: HeaderCarrier = HeaderCarrier(extraHeaders = Seq("correlationId" -> correlationId))

  override def additionalConfigs: Map[String, Any] = Map(
    "microservice.services.email.protocol" -> "http",
    "microservice.services.email.host"     -> wireMockHost,
    "microservice.services.email.port"     -> wireMockPort
  )

  "postEmail" must {
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

      stubFor(
        post(urlEqualTo("/hmrc/email"))
          .withHeader(HeaderNames.CONTENT_TYPE, containing(MimeTypes.JSON))
          .withHeader("CorrelationId", equalTo(correlationId))
          .withRequestBody(equalToJson(expectedRequestBody))
          .willReturn(aResponse().withStatus(Status.ACCEPTED))
      )

      connector.postEmail(request).futureValue.status mustBe Status.ACCEPTED
    }

    "post the certificate email to the HMRC domain" in {
      val parameters = SubmitterCertificateEmailParameters(
        recipientName = "recipient name",
        companyName = "companyName",
        submitterName = Some("submitter name"),
        saoName = "sao name",
        submittedDateTime = "17 January 2025 at 11:45am",
        referenceId = "abc"
      )
      val request = SubmitterCertificateEmail(
        to = List("email@example.com"),
        parameters = parameters
      )

      val expectedRequestBody = """{
          |  "to": ["email@example.com"],
          |  "templateId": "dsao_certificate_confirmation_for_submitter",
          |  "parameters": {
          |    "recipientName": "recipient name",
          |    "companyName": "companyName",
          |    "submitterName": "submitter name",
          |    "saoName": "sao name",
          |    "submittedDateTime": "17 January 2025 at 11:45am",
          |    "referenceId": "abc"
          |  }
          |}""".stripMargin

      stubFor(
        post(urlEqualTo("/hmrc/email"))
          .withHeader(HeaderNames.CONTENT_TYPE, containing(MimeTypes.JSON))
          .withHeader("CorrelationId", equalTo(correlationId))
          .withRequestBody(equalToJson(expectedRequestBody))
          .willReturn(aResponse().withStatus(Status.ACCEPTED))
      )

      connector.postEmail(request).futureValue.status mustBe Status.ACCEPTED
    }

    "post the SAO certificate email to the HMRC domain without a submitter name" in {
      val parameters = SaoCertificateEmailParameters(
        recipientName = "recipient name",
        companyName = "companyName",
        saoName = "sao name",
        submittedDateTime = "17 January 2025 at 11:45am",
        referenceId = "abc"
      )
      val request = SaoCertificateEmail(
        to = List("email@example.com"),
        parameters = parameters
      )

      val expectedRequestBody = """{
          |  "to": ["email@example.com"],
          |  "templateId": "dsao_certificate_confirmation_for_sao",
          |  "parameters": {
          |    "recipientName": "recipient name",
          |    "companyName": "companyName",
          |    "saoName": "sao name",
          |    "submittedDateTime": "17 January 2025 at 11:45am",
          |    "referenceId": "abc"
          |  }
          |}""".stripMargin

      stubFor(
        post(urlEqualTo("/hmrc/email"))
          .withHeader(HeaderNames.CONTENT_TYPE, containing(MimeTypes.JSON))
          .withHeader("CorrelationId", equalTo(correlationId))
          .withRequestBody(equalToJson(expectedRequestBody))
          .willReturn(aResponse().withStatus(Status.ACCEPTED))
      )

      connector.postEmail(request).futureValue.status mustBe Status.ACCEPTED
    }

    "return the raw response without throwing on a non-202 status" in {
      val request = NotificationEmail(
        to = List("email@example.com"),
        templateId = NotificationConfirmation,
        parameters = NotificationEmailParameters(
          recipientName = "name",
          companyName = "companyName",
          submittedDateTime = "17 January 2025 at 11:45am",
          referenceId = "abc"
        )
      )

      stubFor(
        post(urlEqualTo("/hmrc/email"))
          .willReturn(aResponse().withStatus(Status.BAD_REQUEST).withBody("bad request"))
      )

      val result = connector.postEmail(request).futureValue
      result.status mustBe Status.BAD_REQUEST
      result.body mustBe "bad request"
    }
  }
}
