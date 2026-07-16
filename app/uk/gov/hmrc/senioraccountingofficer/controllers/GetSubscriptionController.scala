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
import play.api.mvc.{Action, AnyContent, ControllerComponents}
import uk.gov.hmrc.http.HttpResponse
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController
import uk.gov.hmrc.senioraccountingofficer.connectors.GetSubscriptionConnector
import uk.gov.hmrc.senioraccountingofficer.controllers.actions.*
import uk.gov.hmrc.senioraccountingofficer.models.ApiError
import uk.gov.hmrc.senioraccountingofficer.models.ApiError.*
import uk.gov.hmrc.senioraccountingofficer.models.dps.GetSubscriptionDpsResponse

import scala.concurrent.ExecutionContext
import scala.util.Try

import javax.inject.Inject

class GetSubscriptionController @Inject() (
    cc: ControllerComponents,
    getSubscriptionConnector: GetSubscriptionConnector,
    identify: IdentifierAction,
    ensureCorrelationId: EnsureCorrelationIdAction
)(using
    ExecutionContext
) extends BackendController(cc)
    with Logging {
  def getSubscription: Action[AnyContent] = (identify andThen ensureCorrelationId).async { implicit request =>
    getSubscriptionConnector
      .getSubscription(request.saoSubscriptionId)
      .map {
        case HttpResponse(200, body, _) =>
          Try(Json.parse(body).validate[GetSubscriptionDpsResponse].asEither).toEither.flatten
            .map { response =>
              Ok(Json.toJson(response))
            }
            .left
            .map { _ =>
              logger.warn("[GetSubscription][DPS][MalformedResponse]")
              BadGateway(Json.toJson(ApiError(reason = Reason.DOWNSTREAM_SERVICE_MISALIGNMENT)))
            }
            .merge
        case HttpResponse(status @ 204, body, _) =>
          logger.warn("[GetSubscription][DPS][NO_CONTENT]")
          BadGateway(Json.toJson(ApiError(reason = Reason.SUBSCRIPTION_NOT_FOUND)))
        case HttpResponse(400, body, _) =>
          logger.warn("[GetSubscription][DPS][BAD_REQUEST]")
          InternalServerError(Json.toJson(ApiError(reason = Reason.DOWNSTREAM_SERVICE_MISALIGNMENT)))
        case HttpResponse(401, body, _) =>
          logger.warn("[GetSubscription][DPS][UNAUTHORIZED]")
          InternalServerError(Json.toJson(ApiError(reason = Reason.SERVICE_MISCONFIGURATION)))
        case HttpResponse(403, body, _) =>
          logger.warn("[GetSubscription][DPS][FORBIDDEN]")
          InternalServerError(Json.toJson(ApiError(reason = Reason.SERVICE_MISCONFIGURATION)))
        case HttpResponse(500, body, _) =>
          logger.warn("[GetSubscription][DPS][INTERNAL_SERVER_ERROR]")
          BadGateway(Json.toJson(ApiError(reason = Reason.DOWNSTREAM_SERVICE_ERROR)))
        case HttpResponse(503, body, _) =>
          logger.warn("[GetSubscription][DPS][INTERNAL_SERVER_ERROR]")
          BadGateway(Json.toJson(ApiError(reason = Reason.DOWNSTREAM_SERVICE_UNAVAILABLE)))
        case HttpResponse(status, _, _) =>
          logger.warn(s"[GetSubscription][DPS][Unknown]status=$status")
          BadGateway(Json.toJson(ApiError(reason = Reason.DOWNSTREAM_SERVICE_MISALIGNMENT)))
      }
  }
}
