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

package uk.gov.hmrc.senioraccountingofficer.models

import org.scalatest.OptionValues
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import uk.gov.hmrc.senioraccountingofficer.models.CertificateCompany as CertificateDpsCompany
import uk.gov.hmrc.senioraccountingofficer.models.CertificateRequestSpec.*
import uk.gov.hmrc.senioraccountingofficer.models.dps.CertificateDpsRequest

import java.time.LocalDate

class CertificateRequestSpec extends AnyWordSpec with Matchers with OptionValues {
  val subscriptionId = "example subscription id"

  val certificateRequestWithSubmitterName: CertificateRequest = CertificateRequest(
    subscriptionId = subscriptionId,
    submitterName = Some(submitterName),
    saoName = saoName,
    saoEmail = email,
    companies = List(
      CertificateCompany(
        crn = Some(crn),
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
        isBankLevyQualified = false
      )
    ),
    remarks = Some(remarks)
  )

  val certificateRequestWithoutSubmitterName: CertificateRequest = CertificateRequest(
    subscriptionId = subscriptionId,
    submitterName = None,
    saoName = saoName,
    saoEmail = email,
    companies = List(
      CertificateCompany(
        crn = Some(crn),
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
        isBankLevyQualified = false
      )
    ),
    remarks = Some(remarks)
  )

  "toCertificateDpsRequest" should {
    "map from CertificateRequest to CertificateDpsRequest when there is a submitterName given" in {
      val sut = certificateRequestWithSubmitterName

      val expected = CertificateDpsRequest(
        submitterName = submitterName,
        saoName = saoName,
        saoEmail = email,
        companies = List(
          CertificateDpsCompany(
            crn = Some(crn),
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
            isBankLevyQualified = false
          )
        ),
        remarks = Some(remarks),
        staffPID = None
      )

      sut.toCertificateDpsRequest shouldBe expected
    }

    "map from CertificateRequest to CertificateDpsRequest when there is not a submitterName given, the SAO name should be mapped" in {
      val sut = certificateRequestWithoutSubmitterName

      val expected = CertificateDpsRequest(
        submitterName = saoName,
        saoName = saoName,
        saoEmail = email,
        companies = List(
          CertificateDpsCompany(
            crn = Some(crn),
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
            isBankLevyQualified = false
          )
        ),
        remarks = Some(remarks),
        staffPID = None
      )

      sut.toCertificateDpsRequest shouldBe expected
    }
  }
}

object CertificateRequestSpec {
  val subscriptionId = "example subscription id"
  val submitterName  = "example submitter name"
  val remarks        = "example additional information"

  val crn          = "example crn"
  val utr          = "example utr"
  val companyName  = "example company name"
  val accPeriodEnd = "example accPeriodEnd"
  val status       = "example status"
  val companyType  = "example type"

  val saoName          = "example sao name"
  val fromDate: String = LocalDate.parse("2026-01-01").toString()
  val email            = "example email"
  val toDate: String   = LocalDate.parse("2026-03-01").toString()
}
