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

import play.api.libs.json.{JsError, JsSuccess, JsValue}
import play.api.mvc.{Action, ControllerComponents}
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController
import uk.gov.hmrc.senioraccountingofficer.models.NotificationRequest
import uk.gov.hmrc.senioraccountingofficer.services.NotificationService

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class NotificationController @Inject() (
                                         cc: ControllerComponents,
                                         notificationService: NotificationService
                                       )(implicit ec: ExecutionContext)
  extends BackendController(cc) {

  def postNotification(id: String): Action[JsValue] = Action.async(parse.json) { implicit request =>
    request.body.validate[NotificationRequest] match {
      case JsSuccess(notificationRequest, _) =>
        notificationService.postNotification(id, notificationRequest).map { response =>
          Status(response.status)(response.body)
        }
      case JsError(errors) =>
        Future.successful(BadRequest(errors.toString))
    }
  }
}