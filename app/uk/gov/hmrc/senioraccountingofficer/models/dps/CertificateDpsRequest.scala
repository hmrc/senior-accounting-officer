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
import uk.gov.hmrc.senioraccountingofficer.models.CertificateCompany
import uk.gov.hmrc.senioraccountingofficer.services.PdfService.{Certificate, TaxRegimes}

final case class CertificateDpsRequest(
    submitterName: String,
    saoName: String,
    saoEmail: String,
    companies: List[CertificateCompany],
    remarks: Option[String] = None,
    staffPid: Option[String] = None,
    customerId: Option[String] = None
)

object CertificateDpsRequest {
  given OFormat[CertificateDpsRequest] = Json.format[CertificateDpsRequest]
  
  def toPdfCertificateCompany(certificateCompany: List[CertificateCompany]): Seq[Certificate.Row] = {
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
        bankLevy = company.isBankLevyQualified)

      val `type`: "Plc" | "Ltd" = company.`type` match {
        case "Plc" => "Plc"
        case "Ltd" => "Ltd"
        case _ => throw Exception(s"could not parse company type into 'Plc' or 'Ltd', found ${company.`type`}")
      }
      val status: "Active" | "Dormant" | "Administration" | "Liquidation" = company.status match {
        case "Active" => "Active"
        case "Dormant" => "Dormant"
        case "Administration" => "Administration"
        case "Liquidation" => "Liquidation"
        case _ => throw Exception(s"could not parse company status into 'Active', 'Dormant', 'Administration', or 'Liquidation, found ${company.status}")
      }
      Certificate.Row(
        companyName = company.name,
        utr = company.utr,
        crn = company.crn.getOrElse(""),
        companyType = `type`,
        status = status, 
        financialYearEndDate = company.accPeriodEnd, 
        qualifiedRegimes = taxRegimes, 
        additionalInformation = company.qualificationStatement
      )
    })
  }
  def toCertificate(request: CertificateDpsRequest): Certificate = {
    val companies = toPdfCertificateCompany(request.companies)
    Certificate(
      saoName = request.saoName,
      saoEmail = request.saoEmail,
      submitterName = Some(request.submitterName),
      submissionDate = "date",
      submissionId = request.staffPid.getOrElse(""),
      companies = companies,
      additionalInformation = request.remarks,
    )
  }
}
