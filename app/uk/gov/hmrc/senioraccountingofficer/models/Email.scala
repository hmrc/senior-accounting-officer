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

import play.api.libs.json.*

sealed trait Email {
  def to: List[String]
}

final case class NotificationEmail(
    to: List[String],
    templateId: EmailTemplate,
    parameters: NotificationEmailParameters
) extends Email
final case class SubmitterCertificateEmail(
    to: List[String],
    templateId: EmailTemplate = EmailTemplate.CertificateConfirmationSubmitter,
    parameters: SubmitterCertificateEmailParameters
) extends Email
final case class SaoCertificateEmail(
    to: List[String],
    templateId: EmailTemplate = EmailTemplate.CertificateConfirmationSAO,
    parameters: SaoCertificateEmailParameters
) extends Email

final case class NotificationEmailParameters(
    recipientName: String,
    companyName: String,
    submittedDateTime: String,
    referenceId: String
)

final case class SubmitterCertificateEmailParameters(
    recipientName: String,
    companyName: String,
    submitterName: Option[String],
    saoName: String,
    submittedDateTime: String,
    referenceId: String
)

final case class SaoCertificateEmailParameters(
    recipientName: String,
    companyName: String,
    saoName: String,
    submittedDateTime: String,
    referenceId: String
)

object Email {
  given OWrites[Email] = OWrites {
    case notificationEmail: NotificationEmail                 => Json.toJson(notificationEmail).as[JsObject]
    case submitterCertificateEmail: SubmitterCertificateEmail =>
      Json.toJson(submitterCertificateEmail).as[JsObject]
    case saoCertificateEmail: SaoCertificateEmail => Json.toJson(saoCertificateEmail).as[JsObject]
  }
}

object NotificationEmail {
  given OFormat[NotificationEmail] = Json.format[NotificationEmail]
}

object SubmitterCertificateEmail {
  given OFormat[SubmitterCertificateEmail] = Json.format[SubmitterCertificateEmail]
}

object NotificationEmailParameters {
  given OFormat[NotificationEmailParameters] = Json.format[NotificationEmailParameters]
}

object SubmitterCertificateEmailParameters {
  given OFormat[SubmitterCertificateEmailParameters] = Json.format[SubmitterCertificateEmailParameters]
}

object SaoCertificateEmail {
  given OFormat[SaoCertificateEmail] = Json.format[SaoCertificateEmail]
}

object SaoCertificateEmailParameters {
  given OFormat[SaoCertificateEmailParameters] = Json.format[SaoCertificateEmailParameters]
}
