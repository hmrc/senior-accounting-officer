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
        isBankLevyQualified = Random.nextBoolean,
        qualificationStatement = Some(FreeText(generateAlphanumeric(FreeText.maxFreeTextLength)))
      )

      val testCompanies = CertificateCompanies(List(testCompany))
      val submitterName = generateAlphanumeric(PersonName.maxPersonNameLength)
      val saoName       = generateAlphanumeric(PersonName.maxPersonNameLength)
      val saoEmail      = s"${generateAlphanumeric(Email.maxEmailLength - 2)}@a"
      val staffPid      = generateAlphanumeric(StaffId.maxStaffIdLength)
      val remarks       = generateAlphanumeric(FreeText.maxFreeTextLength)

      val result = Json
        .parse(s"""
             |{
             | "submitterName": "$submitterName",
             | "saoName": "$saoName",
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
        isCorporationTaxQualified = Random.nextBoolean,
        isVatQualified = Random.nextBoolean,
        isPayeQualified = Random.nextBoolean,
        isInsurancePremiumTaxQualified = Random.nextBoolean,
        isStampDutyLandTaxQualified = Random.nextBoolean,
        isStampDutyReserveTaxQualified = Random.nextBoolean,
        isPetroleumRevenueTaxQualified = Random.nextBoolean,
        isCustomsDutiesQualified = Random.nextBoolean,
        isExciseDutiesQualified = Random.nextBoolean,
        isBankLevyQualified = Random.nextBoolean,
        qualificationStatement = None
      )

      val testCompanies = CertificateCompanies(List(testCompany))
      val saoName       = generateAlphanumeric(PersonName.maxPersonNameLength)
      val saoEmail      = s"${generateAlphanumeric(Email.maxEmailLength - 2)}@a"

      val result = Json
        .parse(s"""
             |{
             | "saoName": "$saoName",
             | "saoEmail": "$saoEmail",
             | "companies" : ${Json.toJson(testCompanies)}
             |}""".stripMargin)
        .validate[CertificateRequest]

      result.asEither mustBe Right(
        CertificateRequest(
          submitterName = None,
          saoName = PersonName(saoName),
          saoEmail = Email(saoEmail),
          staffPid = None,
          companies = testCompanies,
          remarks = None
        )
      )
    }

  }

}
