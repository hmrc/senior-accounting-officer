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
import play.api.libs.json.{JsValue, Json}
import play.api.mvc.{Action, ControllerComponents}
import uk.gov.hmrc.senioraccountingofficer.controllers.actions.{EnsureCorrelationIdAction, IdentifierAction}
import uk.gov.hmrc.senioraccountingofficer.helpers.JsonErrorHandling
import uk.gov.hmrc.senioraccountingofficer.models.ApiError.*
import uk.gov.hmrc.senioraccountingofficer.models.notification.*
import uk.gov.hmrc.senioraccountingofficer.models.{ApiError, NotificationRequest}
import uk.gov.hmrc.senioraccountingofficer.services.NotificationService
import uk.gov.hmrc.senioraccountingofficer.services.NotificationService.PostNotificationResponse.*

import scala.concurrent.{ExecutionContext, Future}

import javax.inject.Inject

class NotificationController @Inject() (
    cc: ControllerComponents,
    identify: IdentifierAction,
    ensureCorrelationId: EnsureCorrelationIdAction,
    notificationService: NotificationService
)(implicit ec: ExecutionContext)
    extends BaseController(cc)
    with Logging {

  def postNotification(): Action[String] = (identify andThen ensureCorrelationId).async(parse.tolerantText) {
    implicit request =>
      JsonErrorHandling.parseJson(request.body) match {
        case Right(json) =>
          val errors = JsonErrorHandling.Validators.validateNotification(json)
          if errors.nonEmpty then Future.successful(JsonErrorHandling.badRequest(errors))
          else {
            val notificationRequest = json.as[NotificationRequest]
            notificationService
              .postNotification(request.saoSubscriptionId, notificationRequest)
              .map {
                case Success(notificationId, isPdfAvailable) =>
                  Ok(Json.toJson(NotificationResponse(notificationId, isPdfAvailable)))
                case Misalignment(downstreamService) =>
                  logger.warn(s"[Notification][$downstreamService][MISALIGNMENT]")
                  InternalServerError(Json.toJson(ApiError(reason = Reason.DOWNSTREAM_SERVICE_MISALIGNMENT)))
                case MalformedResponse(downstreamService) =>
                  logger.warn(s"[Notification][$downstreamService][MalformedResponse]")
                  InternalServerError(Json.toJson(ApiError(reason = Reason.DOWNSTREAM_SERVICE_MISALIGNMENT)))
                case Misconfiguration(downstreamService, status) =>
                  logger.warn(s"[Notification][$downstreamService][MISCONFIGURATION]status=$status")
                  InternalServerError(Json.toJson(ApiError(reason = Reason.DOWNSTREAM_SERVICE_MISALIGNMENT)))
                case NotFoundFailure(downstreamService) =>
                  logger.warn(s"[Notification][$downstreamService][NOT_FOUND]")
                  InternalServerError(Json.toJson(ApiError(reason = Reason.NOT_FOUND)))
                case DownstreamServiceError(downstreamService) =>
                  logger.warn(s"[Notification][$downstreamService][INTERNAL_SERVER_ERROR]")
                  BadGateway(Json.toJson(ApiError(reason = Reason.DOWNSTREAM_SERVICE_ERROR)))
                case DownstreamServiceUnavailable(downstreamService) =>
                  logger.warn(s"[Notification][$downstreamService][SERVICE_UNAVAILABLE]")
                  BadGateway(Json.toJson(ApiError(reason = Reason.DOWNSTREAM_SERVICE_UNAVAILABLE)))
                case UnknownFailure(downstreamService, status) =>
                  logger.warn(s"[Notification][$downstreamService][Unknown]status=$status")
                  BadGateway(Json.toJson(ApiError(reason = Reason.DOWNSTREAM_SERVICE_MISALIGNMENT)))
              }
          }
        case Left(errorResult) =>
          Future.successful(errorResult)
      }
  }
}
