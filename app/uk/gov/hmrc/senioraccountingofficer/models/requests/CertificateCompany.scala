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

import play.api.libs.json.*
import uk.gov.hmrc.senioraccountingofficer.models.ApiError.Reason
import uk.gov.hmrc.senioraccountingofficer.models.dps.CertificateDpsCompany

import java.time.LocalDate

final case class CertificateCompany(
    crn: Option[Crn],
    utr: Utr,
    name: CompanyName,
    accPeriodEnd: LocalDate,
    status: CompanyStatus,
    `type`: CompanyType,
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
    qualificationStatement: Option[FreeText]
)

object CertificateCompany {
  given Reads[CertificateCompany] = Json
    .reads[CertificateCompany]
    .flatMapResult { cert =>
      if cert.isQualified && cert.qualificationStatement.isEmpty then
        JsError(Reason.QUALIFICATION_STATEMENT_MISSING.toString)
      else if cert.isUnqualified && cert.qualificationStatement.isDefined then
        JsError(Reason.QUALIFICATION_STATEMENT_PROHIBITED.toString)
      else JsSuccess(cert)
    }

  given OWrites[CertificateCompany] = Json.writes[CertificateCompany]

  extension (certificateCompany: CertificateCompany) {

    def isQualified: Boolean = certificateCompany.isCorporationTaxQualified ||
      certificateCompany.isVatQualified ||
      certificateCompany.isPayeQualified ||
      certificateCompany.isInsurancePremiumTaxQualified ||
      certificateCompany.isStampDutyLandTaxQualified ||
      certificateCompany.isStampDutyReserveTaxQualified ||
      certificateCompany.isPetroleumRevenueTaxQualified ||
      certificateCompany.isCustomsDutiesQualified ||
      certificateCompany.isExciseDutiesQualified ||
      certificateCompany.isBankLevyQualified

    def isUnqualified: Boolean = !isQualified

    def toDpsCertificateCompany: CertificateDpsCompany = {
      CertificateDpsCompany(
        crn = certificateCompany.crn.map(_.value),
        utr = certificateCompany.utr.value,
        name = certificateCompany.name.value,
        accPeriodEnd = certificateCompany.accPeriodEnd.toString,
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
        qualificationStatement = certificateCompany.qualificationStatement.map(_.value)
      )
    }
  }

}
