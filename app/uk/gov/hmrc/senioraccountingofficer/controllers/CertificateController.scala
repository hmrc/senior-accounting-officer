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

package uk.gov.hmrc.senioraccountingofficer.controllers

import play.api.mvc.{Action, AnyContent, ControllerComponents}
import uk.gov.hmrc.senioraccountingofficer.controllers.actions.{EnsureCorrelationIdAction, IdentifierAction}
import uk.gov.hmrc.senioraccountingofficer.models.requests.CertificateRequest
import uk.gov.hmrc.senioraccountingofficer.models.submission.{SubmissionData, SubmissionKind}
import uk.gov.hmrc.senioraccountingofficer.services.submission.SubmissionService

import scala.concurrent.ExecutionContext

import javax.inject.{Inject, Provider}

class CertificateController @Inject() (
    cc: ControllerComponents,
    identify: IdentifierAction,
    ensureCorrelationId: EnsureCorrelationIdAction,
    submissions: Provider[SubmissionService]
)(using ExecutionContext)
    extends BaseController(cc) {
  def postCertificateWithFaultTolerance: Action[String] =
    (identify andThen ensureCorrelationId).async(parse.tolerantText) { implicit request =>
      ValidateRequest.as[CertificateRequest] { payload =>
        submissions.get().submit(SubmissionData.certificate(request.saoSubscriptionId, getCorrelationId, payload))
      }
    }

  def getStateOfWorkItem(idempotencyKey: String): Action[AnyContent] =
    (identify andThen ensureCorrelationId).async { implicit request =>
      submissions.get().status(request.saoSubscriptionId, SubmissionKind.Certificate, idempotencyKey)
    }
}
