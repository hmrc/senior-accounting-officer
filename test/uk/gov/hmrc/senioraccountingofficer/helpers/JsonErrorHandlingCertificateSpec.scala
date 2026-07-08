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

package uk.gov.hmrc.senioraccountingofficer.helpers

import org.scalactic.Prettifier.default
import org.scalatest.OptionValues
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.*
import uk.gov.hmrc.domain.SaUtrGenerator
import uk.gov.hmrc.senioraccountingofficer.models.ApiError
import uk.gov.hmrc.senioraccountingofficer.models.ApiError.*

import scala.util.Random

class JsonErrorHandlingCertificateSpec extends AnyWordSpec with Matchers with OptionValues {

  private def certificateErrors(json: String): Seq[ApiError] =
    JsonErrorHandling.Validators.validateCertificate(Json.parse(json))

  private val saoSubscriptionId = "123"

  private def generateUtr = {
    val seed = Random.nextInt(1000000)
    SaUtrGenerator(seed).nextSaUtr
  }

  private val validCertificate = Json
    .obj(
      "subscriptionId" -> saoSubscriptionId,
      "saoName"        -> "Jane Smith",
      "saoEmail"       -> "Firstname.Lastname@example.com",
      "companies"      -> Json.arr(
        Json.obj(
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
    .toString

  "Certificate validation" when {

    "given a fully valid payload" should {
      "return no errors" in {
        certificateErrors(validCertificate) shouldBe empty
      }
    }

    "given a certificate is missing saoName" should {
      "return MISSING_REQUIRED_FIELD pointing at saoName" in {
        val remover        = (__ \ "saoName").json.prune
        val updatedJsonStr = Json.parse(validCertificate).transform(remover).asOpt.value.toString
        val errors         = certificateErrors(updatedJsonStr)
        errors.map(_.reason) should contain(Reason.MISSING_REQUIRED_FIELD)
        errors.flatMap(_.path) should contain("saoName")
        errors.size shouldBe 1
      }
    }

    "given a certificate is missing saoEmail" should {
      "return MISSING_REQUIRED_FIELD pointing at saoEmail" in {
        val remover        = (__ \ "saoEmail").json.prune
        val updatedJsonStr = Json.parse(validCertificate).transform(remover).asOpt.value.toString
        val errors         = certificateErrors(updatedJsonStr)
        errors.map(_.reason) should contain(Reason.MISSING_REQUIRED_FIELD)
        errors.flatMap(_.path) should contain("saoEmail")
        errors.size shouldBe 1
      }
    }

    "given a certificate is missing companies" should {
      "return MISSING_REQUIRED_FIELD pointing at companies" in {
        val remover        = (__ \ "companies").json.prune
        val updatedJsonStr = Json.parse(validCertificate).transform(remover).asOpt.value.toString
        val errors         = certificateErrors(updatedJsonStr)
        errors.map(_.reason) should contain(Reason.MISSING_REQUIRED_FIELD)
        errors.flatMap(_.path) should contain("companies")
        errors.size shouldBe 1
      }
    }

    "given that fields are nested in the companies object" should {
      "return MISSING_REQUIRED_FIELD pointing at companies[0].utr when no utr is given" in {
        val json             = Json.parse(validCertificate)
        val companies        = (json \ "companies").as[JsArray]
        val updatedCompanies = JsArray(companies.value.map(_.as[JsObject] - "utr"))
        val updatedJsonStr   = (json.as[JsObject] + ("companies" -> updatedCompanies)).toString

        val errors = certificateErrors(updatedJsonStr)
        errors.map(_.reason) should contain(Reason.MISSING_REQUIRED_FIELD)
        errors.flatMap(_.path) should contain("companies[0].utr")
        errors.size shouldBe 1
      }

      "return MISSING_REQUIRED_FIELD pointing at companies[0].name when no name is given" in {
        val json             = Json.parse(validCertificate)
        val companies        = (json \ "companies").as[JsArray]
        val updatedCompanies = JsArray(companies.value.map(_.as[JsObject] - "name"))
        val updatedJsonStr   = (json.as[JsObject] + ("companies" -> updatedCompanies)).toString

        val errors = certificateErrors(updatedJsonStr)
        errors.map(_.reason) should contain(Reason.MISSING_REQUIRED_FIELD)
        errors.flatMap(_.path) should contain("companies[0].name")
        errors.size shouldBe 1
      }

      "return MISSING_REQUIRED_FIELD pointing at companies[0].accPeriodEnd when no accPeriodEnd is given" in {
        val json             = Json.parse(validCertificate)
        val companies        = (json \ "companies").as[JsArray]
        val updatedCompanies = JsArray(companies.value.map(_.as[JsObject] - "accPeriodEnd"))
        val updatedJsonStr   = (json.as[JsObject] + ("companies" -> updatedCompanies)).toString

        val errors = certificateErrors(updatedJsonStr)
        errors.map(_.reason) should contain(Reason.MISSING_REQUIRED_FIELD)
        errors.flatMap(_.path) should contain("companies[0].accPeriodEnd")
        errors.size shouldBe 1
      }

      "return MISSING_REQUIRED_FIELD pointing at companies[0].status when no status is given" in {
        val json             = Json.parse(validCertificate)
        val companies        = (json \ "companies").as[JsArray]
        val updatedCompanies = JsArray(companies.value.map(_.as[JsObject] - "status"))
        val updatedJsonStr   = (json.as[JsObject] + ("companies" -> updatedCompanies)).toString

        val errors = certificateErrors(updatedJsonStr)
        errors.map(_.reason) should contain(Reason.MISSING_REQUIRED_FIELD)
        errors.flatMap(_.path) should contain("companies[0].status")
        errors.size shouldBe 1
      }

      "return MISSING_REQUIRED_FIELD pointing at companies[0].type when no type is given" in {
        val json             = Json.parse(validCertificate)
        val companies        = (json \ "companies").as[JsArray]
        val updatedCompanies = JsArray(companies.value.map(_.as[JsObject] - "type"))
        val updatedJsonStr   = (json.as[JsObject] + ("companies" -> updatedCompanies)).toString

        val errors = certificateErrors(updatedJsonStr)
        errors.map(_.reason) should contain(Reason.MISSING_REQUIRED_FIELD)
        errors.flatMap(_.path) should contain("companies[0].type")
        errors.size shouldBe 1
      }

      "return MISSING_REQUIRED_FIELD pointing at companies[0].isCorporationTaxQualified when no isCorporationTaxQualified boolean is given" in {
        val json             = Json.parse(validCertificate)
        val companies        = (json \ "companies").as[JsArray]
        val updatedCompanies = JsArray(companies.value.map(_.as[JsObject] - "isCorporationTaxQualified"))
        val updatedJsonStr   = (json.as[JsObject] + ("companies" -> updatedCompanies)).toString

        val errors = certificateErrors(updatedJsonStr)
        errors.map(_.reason) should contain(Reason.MISSING_REQUIRED_FIELD)
        errors.flatMap(_.path) should contain("companies[0].isCorporationTaxQualified")
        errors.size shouldBe 1
      }
    }

    "given a invalid email format for the declaration" should {
      "return INVALID_FORMAT pointing at email" in {
        val updatedJsonStr = validCertificate.replace("Firstname.Lastname@example.com", "not-an-email")
        val errors         = certificateErrors(updatedJsonStr)
        errors.map(_.reason) should contain(Reason.INVALID_FORMAT)
        errors.flatMap(_.path) should contain("saoEmail")
        errors.size shouldBe 1
      }
    }
  }
}
