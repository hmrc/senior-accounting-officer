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
final case class CertificateEmail(
    to: List[String],
    templateId: EmailTemplate,
    parameters: CertificateEmailParameters
) extends Email

final case class NotificationEmailParameters(
    recipientName: String,
    companyName: String,
    submittedDateTime: String,
    referenceId: String
)

final case class CertificateEmailParameters(
    companyName: String,
    submitterName: Option[String],
    saoName: String,
    submittedDateTime: String,
    referenceId: String
)

object Email {
  given OWrites[Email] = OWrites {
    case notificationEmail: NotificationEmail => Json.toJson(notificationEmail).as[JsObject]
    case certificateEmail: CertificateEmail   => Json.toJson(certificateEmail).as[JsObject]
  }
}

object NotificationEmail {
  given OFormat[NotificationEmail] = Json.format[NotificationEmail]
}

object CertificateEmail {
  given OFormat[CertificateEmail] = Json.format[CertificateEmail]
}

object NotificationEmailParameters {
  given OFormat[NotificationEmailParameters] = Json.format[NotificationEmailParameters]
}

object CertificateEmailParameters {
  given OFormat[CertificateEmailParameters] = Json.format[CertificateEmailParameters]
}
