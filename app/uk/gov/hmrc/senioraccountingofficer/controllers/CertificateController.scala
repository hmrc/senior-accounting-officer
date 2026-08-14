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

import play.api.Logging
import play.api.libs.json.Json
import play.api.mvc.{Action, ControllerComponents}
import uk.gov.hmrc.senioraccountingofficer.controllers.actions.{EnsureCorrelationIdAction, IdentifierAction}
import uk.gov.hmrc.senioraccountingofficer.models.ApiError
import uk.gov.hmrc.senioraccountingofficer.models.ApiError.*
import uk.gov.hmrc.senioraccountingofficer.models.certificate.CertificateResponse
import uk.gov.hmrc.senioraccountingofficer.models.requests.CertificateRequest
import uk.gov.hmrc.senioraccountingofficer.services.CertificateService
import uk.gov.hmrc.senioraccountingofficer.services.CertificateService.PostCertificateResponse.*

import scala.concurrent.ExecutionContext

import javax.inject.Inject

class CertificateController @Inject() (
    cc: ControllerComponents,
    certificateService: CertificateService,
    identify: IdentifierAction,
    ensureCorrelationId: EnsureCorrelationIdAction
)(using ExecutionContext)
    extends BaseController(cc)
    with Logging {

  def postCertificate(): Action[String] = (identify andThen ensureCorrelationId).async(parse.tolerantText) {
    implicit request =>
      ValidateRequest.as[CertificateRequest] { certificateRequest =>
        certificateService
          .postCertificate(request.saoSubscriptionId, certificateRequest)
          .map {
            case Success(certificateRef) =>
              Created(Json.toJson(CertificateResponse(certificateRef)))
            case Misalignment(downstreamService) =>
              logger.warn(s"[Certificate][$downstreamService][BadRequest]")
              InternalServerError(Json.toJson(ApiError(reason = Reason.DOWNSTREAM_SERVICE_MISALIGNMENT)))
            case MalformedResponse(downstreamService) =>
              logger.warn(s"[Certificate][$downstreamService][MalformedResponse]")
              InternalServerError(Json.toJson(ApiError(reason = Reason.DOWNSTREAM_SERVICE_MISALIGNMENT)))
            case DownstreamUnauthorised(downstreamService) =>
              logger.warn(s"[Certificate][$downstreamService][Unauthorised]")
              InternalServerError(Json.toJson(ApiError(reason = Reason.SERVICE_MISCONFIGURATION)))
            case DownstreamForbidden(downstreamService) =>
              logger.warn(s"[Certificate][$downstreamService][Forbidden]")
              InternalServerError(Json.toJson(ApiError(reason = Reason.SERVICE_MISCONFIGURATION)))
            case DownstreamServiceError(downstreamService) =>
              logger.warn(s"[Certificate][$downstreamService][DownstreamInternalServerError]")
              BadGateway(Json.toJson(ApiError(reason = Reason.DOWNSTREAM_SERVICE_ERROR)))
            case DownstreamServiceUnavailable(downstreamService) =>
              logger.warn(s"[Certificate][$downstreamService][ServiceUnavailable]")
              BadGateway(Json.toJson(ApiError(reason = Reason.DOWNSTREAM_SERVICE_UNAVAILABLE)))
            case UnknownFailure(downstreamService, status) =>
              logger.warn(s"[Certificate][$downstreamService][$status]")
              BadGateway(Json.toJson(ApiError(reason = Reason.DOWNSTREAM_SERVICE_MISALIGNMENT)))
          }
      }
  }

}
