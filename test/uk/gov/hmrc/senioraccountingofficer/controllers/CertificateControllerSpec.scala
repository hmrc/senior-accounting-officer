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

package uk.gov.hmrc.senioraccountingofficer.controllers

import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.*
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.mockito.MockitoSugar.mock
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Application
import play.api.http.Status
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.{JsObject, Json}
import play.api.mvc.{AnyContentAsText, Result}
import play.api.test.FakeRequest
import play.api.test.Helpers.*
import uk.gov.hmrc.http.HttpResponse
import uk.gov.hmrc.senioraccountingofficer.connectors.CertificateConnector

import scala.concurrent.Future

class CertificateControllerSpec extends AnyWordSpec with Matchers with GuiceOneAppPerSuite {

  private val mockCertificateConnector = mock[CertificateConnector]
  private val saoSubscriptionId        = "123"
  private val certificateUrl           = s"/senior-accounting-officer/certificate/$saoSubscriptionId"

  override def fakeApplication(): Application =
    GuiceApplicationBuilder()
      .overrides(
        bind[CertificateConnector].toInstance(mockCertificateConnector)
      )
      .build()

  private val validPayload: JsObject = Json.obj(
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
        "companyName"              -> "Example Ltd",
        "uniqueTaxReference"       -> "1234567890",
        "companyReferenceNumber"   -> "AB123456",
        "companyType"              -> "LTD",
        "financialYearEndDate"     -> "2024-12-31",
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

  private def routeResult(request: FakeRequest[AnyContentAsText]): Future[Result] =
    route(app, request) match {
      case Some(result) => result
      case None         => fail("Expected route to be defined")
    }

  private def certificateRequest(payload: String): FakeRequest[AnyContentAsText] =
    FakeRequest("POST", certificateUrl)
      .withHeaders(CONTENT_TYPE -> "application/json")
      .withTextBody(payload)

  private def assertValidationError(payload: String, expectedError: play.api.libs.json.JsValue): Unit = {
    reset(mockCertificateConnector)

    val result = routeResult(certificateRequest(payload))

    status(result) shouldBe Status.BAD_REQUEST
    contentAsJson(result) shouldBe Json.arr(expectedError)
    verify(mockCertificateConnector, never()).postCertificate(any(), any())(using any())
  }

  "POST /certificate/:saoSubscriptionId" should {
    "return 204 when the downstream connector succeeds without a body" in {
      reset(mockCertificateConnector)
      when(mockCertificateConnector.postCertificate(any(), any())(using any()))
        .thenReturn(Future.successful(HttpResponse(status = Status.NO_CONTENT)))

      val result = routeResult(certificateRequest(validPayload.toString()))

      status(result) shouldBe Status.NO_CONTENT
    }

    "return the downstream JSON body for validation errors" in {
      val downstreamBody = """[{"path":"companies[0].companyType","reason":"INVALID_ENUM_VALUE"}]"""
      reset(mockCertificateConnector)
      when(mockCertificateConnector.postCertificate(any(), any())(using any()))
        .thenReturn(Future.successful(HttpResponse(status = Status.BAD_REQUEST, body = downstreamBody)))

      val result = routeResult(certificateRequest(validPayload.toString()))

      status(result) shouldBe Status.BAD_REQUEST
      contentAsString(result) shouldBe downstreamBody
    }

    "return 400 with MALFORMED_REQUEST for malformed JSON without calling the connector" in {
      assertValidationError(
        """{"declaration":""",
        Json.obj("reason" -> "MALFORMED_REQUEST")
      )
    }

    "return 400 with MISSING_REQUIRED_FIELD for schema-invalid JSON without calling the connector" in {
      assertValidationError(
        (validPayload - "companies").toString(),
        Json.obj("path" -> "companies", "reason" -> "MISSING_REQUIRED_FIELD")
      )
    }

    "return 400 with MISSING_REQUIRED_FIELD for a nested missing field without calling the connector" in {
      val invalidPayload = validPayload ++ Json.obj(
        "declaration" -> Json.obj(
          "seniorAccountingOfficer" -> Json.obj(
            "name" -> "John Doe"
          )
        )
      )

      assertValidationError(
        invalidPayload.toString(),
        Json.obj("path" -> "declaration.seniorAccountingOfficer.email", "reason" -> "MISSING_REQUIRED_FIELD")
      )
    }

    "return 400 with CANNOT_BE_EMPTY for an empty declaration name without calling the connector" in {
      val invalidPayload = validPayload ++ Json.obj(
        "declaration" -> Json.obj(
          "seniorAccountingOfficer" -> Json.obj(
            "name"  -> "",
            "email" -> "john.doe@example.com"
          )
        )
      )

      assertValidationError(
        invalidPayload.toString(),
        Json.obj("path" -> "declaration.seniorAccountingOfficer.name", "reason" -> "CANNOT_BE_EMPTY")
      )
    }

    "return 400 with INVALID_DATA_TYPE for a non-string SAO name without calling the connector" in {
      val invalidPayload = validPayload ++ Json.obj(
        "declaration" -> Json.obj(
          "seniorAccountingOfficer" -> Json.obj(
            "name"  -> 123,
            "email" -> "john.doe@example.com"
          )
        )
      )

      assertValidationError(
        invalidPayload.toString(),
        Json.obj("path" -> "declaration.seniorAccountingOfficer.name", "reason" -> "INVALID_DATA_TYPE")
      )
    }

    "return 400 with INVALID_DATA_TYPE for additional properties without calling the connector" in {
      val invalidPayload = validPayload ++ Json.obj(
        "unexpectedField" -> "value"
      )

      assertValidationError(
        invalidPayload.toString(),
        Json.obj("path" -> "unexpectedField", "reason" -> "INVALID_DATA_TYPE")
      )
    }

    "return 400 with INVALID_FORMAT for an invalid declaration email without calling the connector" in {
      val invalidPayload = validPayload ++ Json.obj(
        "declaration" -> Json.obj(
          "seniorAccountingOfficer" -> Json.obj(
            "name"  -> "John Doe",
            "email" -> "not-an-email"
          )
        )
      )

      assertValidationError(
        invalidPayload.toString(),
        Json.obj("path" -> "declaration.seniorAccountingOfficer.email", "reason" -> "INVALID_FORMAT")
      )
    }

    "return 400 with INVALID_ENUM_VALUE for an unsupported company type without calling the connector" in {
      val invalidPayload = validPayload ++ Json.obj(
        "companies" -> Json.arr(
          Json.obj(
            "companyName"              -> "Example Ltd",
            "uniqueTaxReference"       -> "1234567890",
            "companyReferenceNumber"   -> "AB123456",
            "companyType"              -> "LLP",
            "financialYearEndDate"     -> "2024-12-31",
            "seniorAccountingOfficers" -> Json.arr(
              Json.obj(
                "name"      -> "Firstname Lastname",
                "email"     -> "firstname.lastname@example.com",
                "startDate" -> "2024-04-01",
                "endDate"   -> "2025-03-31"
              )
            )
          )
        )
      )

      assertValidationError(
        invalidPayload.toString(),
        Json.obj("path" -> "companies[0].companyType", "reason" -> "INVALID_ENUM_VALUE")
      )
    }

    "return 400 with ARRAY_MIN_ITEMS_NOT_MET for empty companies without calling the connector" in {
      val invalidPayload = validPayload ++ Json.obj("companies" -> Json.arr())

      assertValidationError(
        invalidPayload.toString(),
        Json.obj("path" -> "companies", "reason" -> "ARRAY_MIN_ITEMS_NOT_MET")
      )
    }

    "return 400 with LENGTH_OUT_OF_BOUNDS for overlong additional information without calling the connector" in {
      val invalidPayload = validPayload ++ Json.obj(
        "additionalInformation" -> ("A" * 5001)
      )

      assertValidationError(
        invalidPayload.toString(),
        Json.obj("path" -> "additionalInformation", "reason" -> "LENGTH_OUT_OF_BOUNDS")
      )
    }
  }
}
