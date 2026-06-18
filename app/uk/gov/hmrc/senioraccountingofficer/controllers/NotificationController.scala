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

import play.api.libs.json.{JsObject, JsValue, Json}
import play.api.mvc.{Action, ControllerComponents}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController
import uk.gov.hmrc.play.http.HeaderCarrierConverter
import uk.gov.hmrc.senioraccountingofficer.helpers.JsonErrorHandling
import uk.gov.hmrc.senioraccountingofficer.models.NotificationRequest
import uk.gov.hmrc.senioraccountingofficer.services.NotificationService

import scala.concurrent.{ExecutionContext, Future}
import javax.inject.Inject
import uk.gov.hmrc.senioraccountingofficer.models.toNotificationDpsRequest

class NotificationController @Inject() (
    cc: ControllerComponents,
    notificationService: NotificationService
)(implicit ec: ExecutionContext)
    extends BackendController(cc) {

  def postNotification(): Action[String] = Action.async(parse.tolerantText) { implicit request =>
    given HeaderCarrier = HeaderCarrierConverter.fromRequest(request)

    JsonErrorHandling.parseJson(request.body) match {
      case Right(json) =>
        val errors = JsonErrorHandling.Validators.validateNotification(json)
        if errors.nonEmpty then Future.successful(JsonErrorHandling.badRequest(errors))
        else {
          val id           = (json \ "subscriptionId").as[String]
          val body         = Json.prettyPrint(json.as[JsObject] - "subscriptionId")
          val notification = json.as[NotificationRequest]
          // val stub = Notifi
          println("here!!")
          println(notification)

          val dpsRequest = notification.toNotificationDpsRequest

          notificationService
            .postNotification(id, body)
            .map { response =>
              Status(response.status)(response.body)
            }
        }
      case Left(errorResult) =>
        Future.successful(errorResult)
    }
  }
}
