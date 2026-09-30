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
import uk.gov.hmrc.senioraccountingofficer.models.dps.{GetSubscriptionDpsResponse, NominatedCompany}
import uk.gov.hmrc.senioraccountingofficer.utils.TestDataGenerator.{generateAlphanumeric, generateCrn, generateUtr}

import scala.util.Random

import java.time.{LocalDate, LocalDateTime}

import CertificateRequestSpec.*
import CertificateCompanySpec.genUnqualifiedCompany

class CertificateRequestSpec extends AnyWordSpec with Matchers {

  "Json reader for CertificateRequest" must {

    "return a CertificateRequest when all of the fields exist" in {
      val testCompany = CertificateCompany(
        crn = Some(Crn(generateCrn)),
        utr = Utr(generateUtr),
        name = CompanyName(generateAlphanumeric(CompanyName.maxCompanyNameLength)),
        accPeriodEnd = LocalDate.now(),
        status = CompanyStatus.Active,
        `type` = CompanyType.PLC,
        isCorporationTaxQualified = Random.nextBoolean,
        isVatQualified = Random.nextBoolean,
        isPayeQualified = Random.nextBoolean,
        isInsurancePremiumTaxQualified = Random.nextBoolean,
        isStampDutyLandTaxQualified = Random.nextBoolean,
        isStampDutyReserveTaxQualified = Random.nextBoolean,
        isPetroleumRevenueTaxQualified = Random.nextBoolean,
        isCustomsDutiesQualified = Random.nextBoolean,
        isExciseDutiesQualified = Random.nextBoolean,
        isBankLevyQualified = true,
        qualificationStatement = Some(FreeText(generateAlphanumeric(FreeText.maxFreeTextLength)))
      )

      val testCompanies      = CertificateCompanies(List(testCompany))
      val submitterName      = generateAlphanumeric(PersonName.maxPersonNameLength)
      val saoName            = generateAlphanumeric(PersonName.maxPersonNameLength)
      val saoDeclarationName = generateAlphanumeric(PersonName.maxPersonNameLength)
      val saoEmail           = s"${generateAlphanumeric(Email.maxEmailLength - 2)}@a"
      val staffPid           = generateAlphanumeric(StaffId.maxStaffIdLength)
      val remarks            = generateAlphanumeric(FreeText.maxFreeTextLength)

      val result = Json
        .parse(s"""
             |{
             | "submitterName": "$submitterName",
             | "saoName": "$saoName",
             | "saoDeclarationName": "$saoDeclarationName",
             | "saoEmail": "$saoEmail",
             | "staffPid": "$staffPid",
             | "companies" : ${Json.toJson(testCompanies)},
             | "remarks": "$remarks"
             |}""".stripMargin)
        .validate[CertificateRequest]

      result.asEither mustBe Right(
        CertificateRequest(
          submitterName = Some(PersonName(submitterName)),
          saoName = PersonName(saoName),
          saoDeclarationName = PersonName(saoDeclarationName),
          saoEmail = Email(saoEmail),
          staffPid = Some(StaffId(staffPid)),
          companies = testCompanies,
          remarks = Some(FreeText(remarks))
        )
      )
    }

    "return a CertificateRequest when the optional fields are empty" in {
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

      val testCompanies = CertificateCompanies(List(testCompany))
      val saoName       = generateAlphanumeric(PersonName.maxPersonNameLength)
      val saoEmail      = s"${generateAlphanumeric(Email.maxEmailLength - 2)}@a"

      val result = Json
        .parse(s"""
             |{
             | "saoName": "$saoName",
             | "saoDeclarationName": "$saoName",
             | "saoEmail": "$saoEmail",
             | "companies" : ${Json.toJson(testCompanies)}
             |}""".stripMargin)
        .validate[CertificateRequest]

      result.asEither mustBe Right(
        CertificateRequest(
          submitterName = None,
          saoName = PersonName(saoName),
          saoDeclarationName = PersonName(saoName),
          saoEmail = Email(saoEmail),
          staffPid = None,
          companies = testCompanies,
          remarks = None
        )
      )
    }

    "return an QUALIFICATION_STATEMENT_MISSING error for a qualified certificate" in {
      val qualifiedCompany = genUnqualifiedCompany.copy(
        isCorporationTaxQualified = true,
        isVatQualified = true,
        isPayeQualified = true,
        isInsurancePremiumTaxQualified = true,
        isStampDutyLandTaxQualified = true,
        isStampDutyReserveTaxQualified = true,
        isPetroleumRevenueTaxQualified = true,
        isCustomsDutiesQualified = true,
        isExciseDutiesQualified = true,
        isBankLevyQualified = true
      )
      val request = genUnqualifiedRequest(qualifiedCompany)

      val result = Json
        .parse(Json.toJson(request).toString)
        .validate[CertificateRequest]

      result.asEither mustBe Left(
        List((JsPath.\("companies")(0), List(JsonValidationError("QUALIFICATION_STATEMENT_MISSING"))))
      )

    }

    "return an QUALIFICATION_STATEMENT_PROHIBITED error for a qualified certificate" when {
      s"there is a qualification statement but the entity is unqualified" in {
        val testCompany = genUnqualifiedCompany.copy(qualificationStatement = Some(FreeText(generateAlphanumeric(1))))
        val request     = genUnqualifiedRequest(testCompany)

        val result = Json
          .parse(Json.toJson(request).toString)
          .validate[CertificateRequest]

        result.asEither mustBe Left(
          List((JsPath.\("companies")(0), List(JsonValidationError("QUALIFICATION_STATEMENT_PROHIBITED"))))
        )
      }
    }
    "toPdfCertificate" should {
      import CertificateRequestSpec.*

      "format PDF dates without leading zeroes and use 12-hour time" in {
        val subscription = GetSubscriptionDpsResponse(
          etmpSafeId = "safe-id",
          nominatedCompany = NominatedCompany(Some(crn), companyName, utr),
          contacts = Nil,
          created = LocalDateTime.of(2025, 6, 1, 9, 5),
          updated = LocalDateTime.of(2025, 6, 1, 9, 5)
        )
        val request = CertificateRequest(
          submitterName = None,
          saoName = PersonName("SAO name"),
          saoDeclarationName = PersonName("SAO name"),
          saoEmail = Email("sao@example.com"),
          staffPid = None,
          remarks = None,
          companies = CertificateCompanies(List(CertificateRequestSpec.certificateCompanyWithOptionalFields))
        )

        val result = request.toPdfCertificate(
          subscriptionId = "subscription-id",
          subscription = subscription,
          certificateReference = "certificate-reference",
          submissionDateTime = LocalDateTime.of(2025, 6, 2, 14, 42)
        )

        result.subscriptionCreationDateTime mustBe "1 June 2025 9:05am"
        result.submissionDateTime mustBe "2 June 2025 2:42pm"
        result.companies.head.financialYearEndDate mustBe "31 December 2020"
      }
    }
  }

}

object CertificateRequestSpec {

  val crn                    = "example crn"
  val utr                    = "example utr"
  val companyName            = "example company name"
  val status                 = CompanyStatus.Active
  val accPeriodEnd           = "2020-12-31"
  val companyType            = CompanyType.LTD
  val qualificationStatement = "example qualification statement"

  val certificateCompanyWithOptionalFields: CertificateCompany =
    CertificateCompany(
      crn = Some(Crn(crn)),
      utr = Utr(utr),
      name = CompanyName(companyName),
      accPeriodEnd = LocalDate.parse(accPeriodEnd),
      status = status,
      `type` = companyType,
      isCorporationTaxQualified = true,
      isVatQualified = true,
      isPayeQualified = false,
      isInsurancePremiumTaxQualified = false,
      isStampDutyLandTaxQualified = false,
      isStampDutyReserveTaxQualified = false,
      isPetroleumRevenueTaxQualified = false,
      isCustomsDutiesQualified = false,
      isExciseDutiesQualified = false,
      isBankLevyQualified = false,
      qualificationStatement = Some(FreeText(qualificationStatement))
    )

  def genUnqualifiedRequest(testCompany: CertificateCompany): CertificateRequest = {

    val testCompanies = CertificateCompanies(List(testCompany))
    val saoName       = generateAlphanumeric(PersonName.maxPersonNameLength)
    val saoEmail      = s"${generateAlphanumeric(Email.maxEmailLength - 2)}@a"

    CertificateRequest(
      submitterName = None,
      saoName = PersonName(saoName),
      saoDeclarationName = PersonName(saoName),
      saoEmail = Email(saoEmail),
      staffPid = None,
      companies = testCompanies,
      remarks = None
    )
  }

}
