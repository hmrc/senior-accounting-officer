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
import uk.gov.hmrc.senioraccountingofficer.models.EmailTemplate.{CertificateConfirmationSAO, NotificationConfirmation}

enum EmailTemplate(val templateId: String) {
  case CertificateConfirmationSubmitter extends EmailTemplate("dsao_certificate_confirmation_for_submitter")
  case CertificateConfirmationSAO       extends EmailTemplate("dsao_certificate_confirmation_for_sao")
  case NotificationConfirmation         extends EmailTemplate("dsao_notification_confirmation")
}

given Writes[EmailTemplate] = Writes {
  case EmailTemplate.CertificateConfirmationSubmitter => JsString("dsao_certificate_confirmation_for_submitter")
  case CertificateConfirmationSAO                     => JsString("dsao_certificate_confirmation_for_sao")
  case NotificationConfirmation                       => JsString("dsao_notification_confirmation")
}

given Reads[EmailTemplate] = Reads {
  case JsString("dsao_certificate_confirmation_for_submitter") =>
    JsSuccess(EmailTemplate.CertificateConfirmationSubmitter)
  case JsString("dsao_certificate_confirmation_for_sao") => JsSuccess(CertificateConfirmationSAO)
  case JsString("dsao_notification_confirmation")        => JsSuccess(NotificationConfirmation)
}
