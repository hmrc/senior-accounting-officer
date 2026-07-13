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

package uk.gov.hmrc.senioraccountingofficer.models.dps

import org.scalatest.OptionValues
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import uk.gov.hmrc.senioraccountingofficer.models.CertificateCompany
import uk.gov.hmrc.senioraccountingofficer.models.CertificateRequestSpec.*

class CertificateDpsRequestSpec extends AnyWordSpec with Matchers with OptionValues {

  val certificateCompanyWithQualificationStatement: CertificateCompany =
    CertificateCompany(
      crn = crn,
      utr = utr,
      name = companyName,
      accPeriodEnd = accPeriodEnd,
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
      qualificationStatement = Some(qualificationStatement)
    )

  val certificateCompanyWithoutQualificationStatement: CertificateCompany =
    CertificateCompany(
      crn = crn,
      utr = utr,
      name = companyName,
      accPeriodEnd = accPeriodEnd,
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
      qualificationStatement = None
    )

  "toCertificateDpsCompany" should {
    "map from CertificateCompany to CertificateDpsCompany mapped with qualification statement" in {
      val sut = certificateCompanyWithQualificationStatement

      val expected =
        CertificateDpsCompany(
          crn = crn,
          utr = utr,
          name = companyName,
          accPeriodEnd = accPeriodEnd,
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
          qualificationStatement = Some(qualificationStatement)
        )

      sut.toDpsCertificateCompany shouldBe expected
    }

    "map from CertificateCompany to CertificateDpsCompany mapped without qualification statement" in {
      val sut = certificateCompanyWithoutQualificationStatement

      val expected =
        CertificateDpsCompany(
          crn = crn,
          utr = utr,
          name = companyName,
          accPeriodEnd = accPeriodEnd,
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
          qualificationStatement = None
        )

      sut.toDpsCertificateCompany shouldBe expected
    }
  }
}

object CertificateDpsRequestSpec {
  val crn                    = "example crn"
  val utr                    = "example utr"
  val companyName            = "example company name"
  val accPeriodEnd           = "example accPeriodEnd"
  val status                 = "example status"
  val companyType            = "example type"
  val qualificationStatement = "example qualification statement"

}
