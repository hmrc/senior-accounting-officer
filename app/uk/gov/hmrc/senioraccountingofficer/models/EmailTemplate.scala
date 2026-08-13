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
import uk.gov.hmrc.senioraccountingofficer.models.EmailTemplate.{
  CertificateConfirmationSAO,
  CertificateConfirmationSubmitter,
  NotificationConfirmation
}

enum EmailTemplate(val templateId: JsString) {
  case CertificateConfirmationSubmitter extends EmailTemplate(JsString("dsao_certificate_confirmation_for_submitter"))
  case CertificateConfirmationSAO       extends EmailTemplate(JsString("dsao_certificate_confirmation_for_sao"))
  case NotificationConfirmation         extends EmailTemplate(JsString("dsao_notification_confirmation"))

}

given Writes[EmailTemplate] = Writes {
  case CertificateConfirmationSubmitter => CertificateConfirmationSubmitter.templateId
  case CertificateConfirmationSAO       => CertificateConfirmationSAO.templateId
  case NotificationConfirmation         => NotificationConfirmation.templateId
}

given Reads[EmailTemplate] = Reads {
  case CertificateConfirmationSubmitter.templateId => JsSuccess(CertificateConfirmationSubmitter)
  case CertificateConfirmationSAO.templateId       => JsSuccess(CertificateConfirmationSAO)
  case NotificationConfirmation.templateId         => JsSuccess(NotificationConfirmation)

}
