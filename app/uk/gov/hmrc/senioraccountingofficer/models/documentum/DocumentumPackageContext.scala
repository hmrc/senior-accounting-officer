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

package uk.gov.hmrc.senioraccountingofficer.models.documentum

import uk.gov.hmrc.senioraccountingofficer.models.dps.{CertificateDpsRequest, NominatedCompany}
import uk.gov.hmrc.senioraccountingofficer.models.requests.NotificationRequest

enum SubmissionType(val documentumName: String) {
  case Notification extends SubmissionType("Notification")
  case Certificate  extends SubmissionType("Certificate")
}

final case class DocumentumCompany(
    utr: String,
    name: String,
    crn: Option[String]
)

final case class DocumentumPackageContext(
    submissionId: String,
    submissionType: SubmissionType,
    saoSubscriptionId: String,
    nominatedCompany: DocumentumCompany,
    customerId: Option[String]
)

object DocumentumPackageContext {

  def notification(
      submissionId: String,
      customerId: Option[String],
      saoSubscriptionId: String,
      nominatedCompany: NominatedCompany,
      request: NotificationRequest
  ): DocumentumPackageContext =
    DocumentumPackageContext(
      submissionId = submissionId,
      submissionType = SubmissionType.Notification,
      saoSubscriptionId = saoSubscriptionId,
      nominatedCompany =
        DocumentumCompany(name = nominatedCompany.name, utr = nominatedCompany.utr, crn = nominatedCompany.crn),
      customerId = customerId
    )

  def certificate(
      submissionId: String,
      saoSubscriptionId: String,
      nominatedCompany: NominatedCompany,
      request: CertificateDpsRequest
  ): DocumentumPackageContext =
    DocumentumPackageContext(
      submissionId = submissionId,
      submissionType = SubmissionType.Certificate,
      saoSubscriptionId = saoSubscriptionId,
      nominatedCompany =
        DocumentumCompany(name = nominatedCompany.name, utr = nominatedCompany.utr, crn = nominatedCompany.crn),
      customerId = request.customerId
    )
}
