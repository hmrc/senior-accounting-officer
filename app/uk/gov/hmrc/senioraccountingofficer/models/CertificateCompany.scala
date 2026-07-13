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

import play.api.libs.json.{Json, OFormat}
import uk.gov.hmrc.senioraccountingofficer.models.dps.CertificateDpsCompany

final case class CertificateCompany(
    crn: String,
    utr: String,
    name: String,
    accPeriodEnd: String,
    status: String,
    `type`: String,
    isCorporationTaxQualified: Boolean,
    isVatQualified: Boolean,
    isPayeQualified: Boolean,
    isInsurancePremiumTaxQualified: Boolean,
    isStampDutyLandTaxQualified: Boolean,
    isStampDutyReserveTaxQualified: Boolean,
    isPetroleumRevenueTaxQualified: Boolean,
    isCustomsDutiesQualified: Boolean,
    isExciseDutiesQualified: Boolean,
    isBankLevyQualified: Boolean,
    qualificationStatement: Option[String]
)

object CertificateCompany {
  given OFormat[CertificateCompany] = Json.format[CertificateCompany]

  extension (certificateCompany: CertificateCompany) {
    def toDpsCertificateCompany: CertificateDpsCompany = {
      CertificateDpsCompany(
        crn = certificateCompany.crn,
        utr = certificateCompany.utr,
        name = certificateCompany.name,
        accPeriodEnd = certificateCompany.accPeriodEnd,
        status = certificateCompany.status,
        `type` = certificateCompany.`type`,
        isCorporationTaxQualified = certificateCompany.isCorporationTaxQualified,
        isVatQualified = certificateCompany.isVatQualified,
        isPayeQualified = certificateCompany.isPayeQualified,
        isInsurancePremiumTaxQualified = certificateCompany.isInsurancePremiumTaxQualified,
        isStampDutyLandTaxQualified = certificateCompany.isStampDutyLandTaxQualified,
        isStampDutyReserveTaxQualified = certificateCompany.isStampDutyReserveTaxQualified,
        isPetroleumRevenueTaxQualified = certificateCompany.isPetroleumRevenueTaxQualified,
        isCustomsDutiesQualified = certificateCompany.isCustomsDutiesQualified,
        isExciseDutiesQualified = certificateCompany.isExciseDutiesQualified,
        isBankLevyQualified = certificateCompany.isBankLevyQualified,
        qualificationStatement = certificateCompany.qualificationStatement
      )
    }

    def toCertificateCompany: CertificateCompany = {
      CertificateCompany(
        crn = certificateCompany.crn,
        utr = certificateCompany.utr,
        name = certificateCompany.name,
        accPeriodEnd = certificateCompany.accPeriodEnd,
        status = certificateCompany.status,
        `type` = certificateCompany.`type`,
        isCorporationTaxQualified = certificateCompany.isCorporationTaxQualified,
        isVatQualified = certificateCompany.isVatQualified,
        isPayeQualified = certificateCompany.isPayeQualified,
        isInsurancePremiumTaxQualified = certificateCompany.isInsurancePremiumTaxQualified,
        isStampDutyLandTaxQualified = certificateCompany.isStampDutyLandTaxQualified,
        isStampDutyReserveTaxQualified = certificateCompany.isStampDutyReserveTaxQualified,
        isPetroleumRevenueTaxQualified = certificateCompany.isPetroleumRevenueTaxQualified,
        isCustomsDutiesQualified = certificateCompany.isCustomsDutiesQualified,
        isExciseDutiesQualified = certificateCompany.isExciseDutiesQualified,
        isBankLevyQualified = certificateCompany.isBankLevyQualified,
        qualificationStatement = certificateCompany.qualificationStatement
      )
    }
  }
}
