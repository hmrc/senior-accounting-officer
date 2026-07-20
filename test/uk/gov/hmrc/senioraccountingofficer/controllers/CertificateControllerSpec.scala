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
import uk.gov.hmrc.senioraccountingofficer.services.CertificateService
import uk.gov.hmrc.senioraccountingofficer.services.CertificateService.DownstreamService.DPS
import uk.gov.hmrc.senioraccountingofficer.services.CertificateService.PostCertificateResponse.*
import uk.gov.hmrc.senioraccountingofficer.utils.TestDataGenerator.*

import scala.concurrent.Future

class CertificateControllerSpec extends AnyWordSpec with Matchers with GuiceOneAppPerSuite {

  private val mockCertificateService = mock[CertificateService]
  private val saoSubscriptionId      = "123"
  private def certificateUrl         = routes.CertificateController.postCertificate().url

  override def fakeApplication(): Application =
    GuiceApplicationBuilder()
      .overrides(
        bind[CertificateService].toInstance(mockCertificateService)
      )
      .build()

  private val validPayload: JsObject = Json.obj(
    "subscriptionId" -> saoSubscriptionId,
    "saoName"        -> "Firstname Lastname",
    "saoEmail"       -> "Firstname.Lastname@example.com",
    "companies"      -> Json.arr(
      Json.obj(
        "crn"                            -> generateCertificateCrn,
        "utr"                            -> generateUtr,
        "name"                           -> "Example Subsidiary Ltd",
        "accPeriodEnd"                   -> "2025-03-31",
        "status"                         -> "COMPLIANT",
        "type"                           -> "LTD",
        "isCorporationTaxQualified"      -> true,
        "isVatQualified"                 -> true,
        "isPayeQualified"                -> true,
        "isInsurancePremiumTaxQualified" -> false,
        "isStampDutyLandTaxQualified"    -> false,
        "isStampDutyReserveTaxQualified" -> false,
        "isPetroleumRevenueTaxQualified" -> false,
        "isCustomsDutiesQualified"       -> false,
        "isExciseDutiesQualified"        -> false,
        "isBankLevyQualified"            -> false
      )
    )
  )

  private def routeResult(request: FakeRequest[AnyContentAsText]): Future[Result] =
    route(app, request) match {
      case Some(result) => result
      case None         => fail("Expected route to be defined")
    }

  private def certificateRequest(payload: String): FakeRequest[AnyContentAsText] =
    FakeRequest("POST", certificateUrl)
      .withHeaders("Content-Type" -> "text/plain")
      .withTextBody(payload)

  private def assertValidationError(payload: String, expectedError: play.api.libs.json.JsValue): Unit = {
    reset(mockCertificateService)

    val result = routeResult(certificateRequest(payload))

    status(result) shouldBe Status.BAD_REQUEST
    contentAsJson(result) shouldBe Json.arr(expectedError)
    verify(mockCertificateService, never()).postCertificate(any(), any())(using any())
  }

  "POST /certificate" should {
    "return 201 with the certificateRef when the service returns Success" in {
      reset(mockCertificateService)
      when(mockCertificateService.postCertificate(any(), any())(using any()))
        .thenReturn(Future.successful(Success("CRT0001234567")))

      val result = routeResult(certificateRequest(validPayload.toString()))
      status(result) shouldBe Status.CREATED
      contentAsJson(result) shouldBe Json.obj("certificateRef" -> "CRT0001234567")
    }

    "return 502 when the service returns MalformedResponse" in {
      reset(mockCertificateService)
      when(mockCertificateService.postCertificate(any(), any())(using any()))
        .thenReturn(Future.successful(MalformedResponse(DPS)))

      val result = routeResult(certificateRequest(validPayload.toString()))

      status(result) shouldBe Status.BAD_GATEWAY
      contentAsString(result) should include("DOWNSTREAM_SERVICE_MISALIGNMENT")
    }

    "return 500 when the service returns BadRequestFailure" in {
      reset(mockCertificateService)
      when(mockCertificateService.postCertificate(any(), any())(using any()))
        .thenReturn(Future.successful(BadRequestFailure(DPS)))

      val result = routeResult(certificateRequest(validPayload.toString()))

      status(result) shouldBe Status.INTERNAL_SERVER_ERROR
      contentAsString(result) should include("DOWNSTREAM_SERVICE_MISALIGNMENT")
    }

    "return 502 when the service returns InternalServerFailure" in {
      reset(mockCertificateService)
      when(mockCertificateService.postCertificate(any(), any())(using any()))
        .thenReturn(Future.successful(InternalServerFailure(DPS)))

      val result = routeResult(certificateRequest(validPayload.toString()))

      status(result) shouldBe Status.BAD_GATEWAY
      contentAsString(result) should include("DOWNSTREAM_SERVICE_ERROR")
    }

    "return 502 when the service returns ServiceUnavailableFailure" in {
      reset(mockCertificateService)
      when(mockCertificateService.postCertificate(any(), any())(using any()))
        .thenReturn(Future.successful(ServiceUnavailableFailure(DPS)))

      val result = routeResult(certificateRequest(validPayload.toString()))

      status(result) shouldBe Status.BAD_GATEWAY
      contentAsString(result) should include("DOWNSTREAM_SERVICE_UNAVAILABLE")
    }

    "return 502 when the service returns UnknownFailure" in {
      reset(mockCertificateService)
      when(mockCertificateService.postCertificate(any(), any())(using any()))
        .thenReturn(Future.successful(UnknownFailure(DPS, 1)))

      val result = routeResult(certificateRequest(validPayload.toString()))

      status(result) shouldBe Status.BAD_GATEWAY
      contentAsString(result) should include("DOWNSTREAM_SERVICE_MISALIGNMENT")
    }

    "return 400 with MISSING_REQUIRED_FIELD for schema-invalid JSON without calling the service" in {
      assertValidationError(
        (validPayload - "companies").toString(),
        Json.obj("path" -> "companies", "reason" -> "MISSING_REQUIRED_FIELD")
      )
    }

    "return 400 with CANNOT_BE_EMPTY for an empty SAO name without calling the service" in {
      val invalidPayload = validPayload ++ Json.obj(
        "saoName" -> ""
      )

      assertValidationError(
        invalidPayload.toString(),
        Json.obj("path" -> "saoName", "reason" -> "CANNOT_BE_EMPTY")
      )
    }

    "return 400 with INVALID_DATA_TYPE for a non-string SAO name without calling the service" in {
      val invalidPayload = validPayload ++ Json.obj(
        "saoName" -> 123
      )

      assertValidationError(
        invalidPayload.toString(),
        Json.obj("path" -> "saoName", "reason" -> "INVALID_DATA_TYPE")
      )
    }

    "return 400 with INVALID_DATA_TYPE for additional properties without calling the service" in {
      val invalidPayload = validPayload ++ Json.obj(
        "unexpectedField" -> "value"
      )

      assertValidationError(
        invalidPayload.toString(),
        Json.obj("path" -> "unexpectedField", "reason" -> "INVALID_DATA_TYPE")
      )
    }

    "return 400 with INVALID_FORMAT for an invalid sao email without calling the service" in {
      val invalidPayload = validPayload ++ Json.obj(
        "saoEmail" -> "not-an-email"
      )

      assertValidationError(
        invalidPayload.toString(),
        Json.obj("path" -> "saoEmail", "reason" -> "INVALID_FORMAT")
      )
    }

    "return 400 with INVALID_ENUM_VALUE for an unsupported company type without calling the service" in {
      val invalidPayload = validPayload ++ Json.obj(
        "companies" -> Json.arr(
          Json.obj(
            "crn"                            -> generateCertificateCrn,
            "utr"                            -> generateUtr,
            "name"                           -> "Example Subsidiary Ltd",
            "accPeriodEnd"                   -> "2025-03-31",
            "status"                         -> "COMPLIANT",
            "type"                           -> "NOT VALID",
            "isCorporationTaxQualified"      -> true,
            "isVatQualified"                 -> true,
            "isPayeQualified"                -> true,
            "isInsurancePremiumTaxQualified" -> false,
            "isStampDutyLandTaxQualified"    -> false,
            "isStampDutyReserveTaxQualified" -> false,
            "isPetroleumRevenueTaxQualified" -> false,
            "isCustomsDutiesQualified"       -> false,
            "isExciseDutiesQualified"        -> false,
            "isBankLevyQualified"            -> false
          )
        )
      )

      assertValidationError(
        invalidPayload.toString(),
        Json.obj("path" -> "companies[0].type", "reason" -> "INVALID_ENUM_VALUE")
      )
    }

    "return 400 with ARRAY_MIN_ITEMS_NOT_MET for empty companies without calling the service" in {
      val invalidPayload = validPayload ++ Json.obj("companies" -> Json.arr())

      assertValidationError(
        invalidPayload.toString(),
        Json.obj("path" -> "companies", "reason" -> "ARRAY_MIN_ITEMS_NOT_MET")
      )
    }

    "return BAD_REQUEST when the payload is not valid JSON" in {
      reset(mockCertificateService)
      val request =
        FakeRequest("POST", certificateUrl)
          .withTextBody("this is not json")
          .withHeaders("Content-Type" -> "text/plain")
      val result = routeResult(request)
      status(result) shouldBe Status.BAD_REQUEST
      contentAsString(result) should include("MALFORMED_REQUEST")
      verify(mockCertificateService, never()).postCertificate(any(), any())(using any())
    }
  }
}
