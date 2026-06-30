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

import play.api.libs.json.{Format, Json}
import uk.gov.hmrc.senioraccountingofficer.models.dps.{CertificateDpsRequest, toDpsCertificateCompany}

final case class CertificateRequest(
    subscriptionId: String,
    submitterName: Option[String],
    saoName: String,
    saoEmail: String,
    companies: List[CertificateCompany],
    remarks: Option[String]
)

final case class CertificateCompany(
    crn: Option[String],
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
    isBankLevyQualified: Boolean
)

extension (certificateRequest: CertificateRequest) {
  def toCertificateDpsRequest: CertificateDpsRequest = {
    CertificateDpsRequest(
      submitterName = certificateRequest.submitterName.fold(certificateRequest.saoName)(name => name),
      saoName = certificateRequest.saoName,
      saoEmail = certificateRequest.saoEmail,
      companies = certificateRequest.companies.map(_.toDpsCertificateCompany),
      remarks = certificateRequest.remarks,
      staffPID = None
    )
  }
}

object CertificateRequest {
  given Format[CertificateRequest] = Json.format[CertificateRequest]
}

object CertificateCompany {
  given Format[CertificateCompany] = Json.format[CertificateCompany]
}
