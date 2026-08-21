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

package uk.gov.hmrc.senioraccountingofficer.models.requests

import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.*
import uk.gov.hmrc.senioraccountingofficer.utils.TestDataGenerator.{generateAlphanumeric, generateCrn, generateUtr}

import scala.util.Random

import java.time.LocalDate

import CertificateCompanySpec.*

class CertificateCompanySpec extends AnyWordSpec with Matchers {

  case class Test(company: CertificateCompany)

  object Test {
    given OFormat[Test] = Json.format
  }

  "Json reader for CertificateCompany" must {
    "return a CertificateCompany when all of the fields exist" in {
      val accPeriodEnd = LocalDate.of(1, 12, 31)
      val testCompany  = CertificateCompany(
        crn = Some(Crn(generateCrn)),
        utr = Utr(generateUtr),
        name = CompanyName(generateAlphanumeric(CompanyName.maxCompanyNameLength)),
        accPeriodEnd = accPeriodEnd,
        status = CompanyStatus.Active,
        `type` = CompanyType.PLC,
        isCorporationTaxQualified = true,
        isVatQualified = Random.nextBoolean,
        isPayeQualified = Random.nextBoolean,
        isInsurancePremiumTaxQualified = Random.nextBoolean,
        isStampDutyLandTaxQualified = Random.nextBoolean,
        isStampDutyReserveTaxQualified = Random.nextBoolean,
        isPetroleumRevenueTaxQualified = Random.nextBoolean,
        isCustomsDutiesQualified = Random.nextBoolean,
        isExciseDutiesQualified = Random.nextBoolean,
        isBankLevyQualified = Random.nextBoolean,
        qualificationStatement = Some(FreeText(generateAlphanumeric(FreeText.maxFreeTextLength)))
      )
      val testCompanyAsJson = s"""
          |{
          | "crn": "${testCompany.crn.get.value}",
          | "utr": "${testCompany.utr.value}",
          | "name": "${testCompany.name.value}",
          | "accPeriodEnd": "0001-12-31",
          | "status": "${testCompany.status}",
          | "type": "${testCompany.`type`}",
          | "isCorporationTaxQualified": ${testCompany.isCorporationTaxQualified},
          | "isVatQualified": ${testCompany.isVatQualified},
          | "isPayeQualified": ${testCompany.isPayeQualified},
          | "isInsurancePremiumTaxQualified": ${testCompany.isInsurancePremiumTaxQualified},
          | "isStampDutyLandTaxQualified": ${testCompany.isStampDutyLandTaxQualified},
          | "isStampDutyReserveTaxQualified": ${testCompany.isStampDutyReserveTaxQualified},
          | "isPetroleumRevenueTaxQualified": ${testCompany.isPetroleumRevenueTaxQualified},
          | "isCustomsDutiesQualified": ${testCompany.isCustomsDutiesQualified},
          | "isExciseDutiesQualified": ${testCompany.isExciseDutiesQualified},
          | "isBankLevyQualified": ${testCompany.isBankLevyQualified},
          | "qualificationStatement": "${testCompany.qualificationStatement.get.value}"
          |}""".stripMargin

      val result = Json
        .parse(s"""
             |{
             | "company" : $testCompanyAsJson
             |}""".stripMargin)
        .validate[Test]

      result.asEither mustBe Right(Test(testCompany))
    }

    "return a CertificateCompany when the optional fields are empty" in {
      val testCompany = CertificateCompany(
        crn = None,
        utr = Utr(generateUtr),
        name = CompanyName(generateAlphanumeric(CompanyName.maxCompanyNameLength)),
        accPeriodEnd = LocalDate.now(),
        status = CompanyStatus.Active,
        `type` = CompanyType.PLC,
        isCorporationTaxQualified = false,
        isVatQualified = false,
        isPayeQualified = false,
        isInsurancePremiumTaxQualified = false,
        isStampDutyLandTaxQualified = false,
        isStampDutyReserveTaxQualified = false,
        isPetroleumRevenueTaxQualified = false,
        isCustomsDutiesQualified = false,
        isExciseDutiesQualified = false,
        isBankLevyQualified = false,
        qualificationStatement = None
      )

      val testCompanyAsJson =
        s"""
           |{
           | "utr": "${testCompany.utr.value}",
           | "name": "${testCompany.name.value}",
           | "accPeriodEnd": "${testCompany.accPeriodEnd}",
           | "status": "${testCompany.status}",
           | "type": "${testCompany.`type`}",
           | "isCorporationTaxQualified": ${testCompany.isCorporationTaxQualified},
           | "isVatQualified": ${testCompany.isVatQualified},
           | "isPayeQualified": ${testCompany.isPayeQualified},
           | "isInsurancePremiumTaxQualified": ${testCompany.isInsurancePremiumTaxQualified},
           | "isStampDutyLandTaxQualified": ${testCompany.isStampDutyLandTaxQualified},
           | "isStampDutyReserveTaxQualified": ${testCompany.isStampDutyReserveTaxQualified},
           | "isPetroleumRevenueTaxQualified": ${testCompany.isPetroleumRevenueTaxQualified},
           | "isCustomsDutiesQualified": ${testCompany.isCustomsDutiesQualified},
           | "isExciseDutiesQualified": ${testCompany.isExciseDutiesQualified},
           | "isBankLevyQualified": ${testCompany.isBankLevyQualified}
           |}""".stripMargin

      val result = Json
        .parse(s"""
             |{
             | "company" : $testCompanyAsJson
             |}""".stripMargin)
        .validate[Test]

      result.asEither mustBe Right(Test(testCompany))
    }

    "return an QUALIFICATION_STATEMENT_MISSING error for a qualified certificate" when {
      Map(
        "isCorporationTaxQualified"      -> genUnqualifiedCompany.copy(isCorporationTaxQualified = true),
        "isVatQualified"                 -> genUnqualifiedCompany.copy(isVatQualified = true),
        "isPayeQualified"                -> genUnqualifiedCompany.copy(isPayeQualified = true),
        "isInsurancePremiumTaxQualified" -> genUnqualifiedCompany.copy(isInsurancePremiumTaxQualified = true),
        "isStampDutyLandTaxQualified"    -> genUnqualifiedCompany.copy(isStampDutyLandTaxQualified = true),
        "isStampDutyReserveTaxQualified" -> genUnqualifiedCompany.copy(isStampDutyReserveTaxQualified = true),
        "isPetroleumRevenueTaxQualified" -> genUnqualifiedCompany.copy(isPetroleumRevenueTaxQualified = true),
        "isCustomsDutiesQualified"       -> genUnqualifiedCompany.copy(isCustomsDutiesQualified = true),
        "isExciseDutiesQualified"        -> genUnqualifiedCompany.copy(isExciseDutiesQualified = true),
        "isBankLevyQualified"            -> genUnqualifiedCompany.copy(isBankLevyQualified = true)
      ).foreach { (field, company) =>
        s"$field is qualified" in {
          val result = Json
            .parse(s"""
                   |{
                   | "company" : ${Json.toJson(company)}
                   |}""".stripMargin)
            .validate[Test]

          result.asEither mustBe Left(
            List((JsPath.\("company"), List(JsonValidationError("QUALIFICATION_STATEMENT_MISSING"))))
          )
        }
      }
    }

    "return an QUALIFICATION_STATEMENT_PROHIBITED error for a qualified certificate" when {
      s"there is a qualification statement but the entity is unqualified" in {
        val company = genUnqualifiedCompany.copy(qualificationStatement = Some(FreeText(generateAlphanumeric(1))))
        val result  = Json
          .parse(s"""
                   |{
                   | "company" : ${Json.toJson(company)}
                   |}""".stripMargin)
          .validate[Test]

        result.asEither mustBe Left(
          List((JsPath.\("company"), List(JsonValidationError("QUALIFICATION_STATEMENT_PROHIBITED"))))
        )
      }
    }
  }

}

object CertificateCompanySpec {

  def genUnqualifiedCompany: CertificateCompany = CertificateCompany(
    crn = Some(Crn(generateCrn)),
    utr = Utr(generateUtr),
    name = CompanyName(generateAlphanumeric(CompanyName.maxCompanyNameLength)),
    accPeriodEnd = LocalDate.now(),
    status = CompanyStatus.Active,
    `type` = CompanyType.PLC,
    isCorporationTaxQualified = false,
    isVatQualified = false,
    isPayeQualified = false,
    isInsurancePremiumTaxQualified = false,
    isStampDutyLandTaxQualified = false,
    isStampDutyReserveTaxQualified = false,
    isPetroleumRevenueTaxQualified = false,
    isCustomsDutiesQualified = false,
    isExciseDutiesQualified = false,
    isBankLevyQualified = false,
    qualificationStatement = None
  )

}
