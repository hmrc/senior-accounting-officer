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

import play.api.libs.json.{Json, OFormat}
import uk.gov.hmrc.senioraccountingofficer.services.PdfService.*

import java.time.LocalDate

final case class CertificateDpsRequest(
    submitterName: Option[String],
    saoName: String,
    saoEmail: String,
    companies: List[CertificateDpsCompany],
    remarks: Option[String] = None,
    staffPid: Option[String] = None,
    customerId: Option[String] = None
)

object CertificateDpsRequest {
  given OFormat[CertificateDpsRequest] = Json.format[CertificateDpsRequest]

  def toPdfCertificateCompany(certificateCompany: List[CertificateDpsCompany]): Seq[Certificate.Row] = {
    certificateCompany.map(company => {

      val taxRegimes = TaxRegimes(
        corporationTax = company.isCorporationTaxQualified,
        vat = company.isVatQualified,
        paye = company.isPayeQualified,
        insurancePremiumTax = company.isInsurancePremiumTaxQualified,
        stampDutyLandTax = company.isStampDutyLandTaxQualified,
        stampDutyReserveTax = company.isStampDutyReserveTaxQualified,
        petroleumRevenueTax = company.isPetroleumRevenueTaxQualified,
        customsDuties = company.isCustomsDutiesQualified,
        exciseDuties = company.isExciseDutiesQualified,
        bankLevy = company.isBankLevyQualified
      )

      Certificate.Row(
        companyName = company.name,
        utr = company.utr,
        crn = company.crn.fold("")(identity),
        companyType = company.`type`,
        status = company.status,
        financialYearEndDate = company.accPeriodEnd,
        qualifiedRegimes = taxRegimes,
        additionalInformation = company.qualificationStatement
      )
    })
  }

  def toPdfCertificate(certificateReference: String, request: CertificateDpsRequest): Certificate = {
    val companies = toPdfCertificateCompany(request.companies)
    Certificate(
      saoName = request.saoName,
      saoEmail = request.saoEmail,
      submitterName = request.submitterName,
      submissionDate = LocalDate.now().format(dateFormatter),
      submissionId = certificateReference,
      companies = companies,
      additionalInformation = request.remarks
    )
  }
}
