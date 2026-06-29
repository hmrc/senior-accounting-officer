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
import uk.gov.hmrc.senioraccountingofficer.models.dps.CertificateDpsRequest

final case class CertificateRequest(
    assistantName: Option[String],
    SAOName: String,
    SAOEmail: String,
    subscriptionId: String,
    companies: List[Company],
    remarks: Option[String]
)

extension (certificateRequest: CertificateRequest) {

  def toCertificateDpsRequest: CertificateDpsRequest = {
    CertificateDpsRequest(
      submitterName = certificateRequest.assistantName.fold(certificateRequest.SAOName)(name => name),
      saoName = certificateRequest.SAOName,
      saoEmail = certificateRequest.SAOEmail,
      companies = certificateRequest.companies.map(_.toDpsCompany),
      remarks = certificateRequest.remarks,
      staffPID = None
    )
  }
}

object CertificateRequest {
  given Format[CertificateRequest] = Json.format[CertificateRequest]
}
