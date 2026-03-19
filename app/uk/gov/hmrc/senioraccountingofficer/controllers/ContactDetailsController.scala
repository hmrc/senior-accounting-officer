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

import play.api.http.MimeTypes
import play.api.mvc.Action
import play.api.mvc.ControllerComponents
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController
import uk.gov.hmrc.play.http.HeaderCarrierConverter
import uk.gov.hmrc.senioraccountingofficer.connectors.ContactDetailsConnector
import uk.gov.hmrc.senioraccountingofficer.helpers.JsonErrorHandling

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import javax.inject.Inject

class ContactDetailsController @Inject() (cc: ControllerComponents, contactDetailsConnector: ContactDetailsConnector)(
    using ExecutionContext
) extends BackendController(cc) {
  def getContactDetails(saoSubscriptionId: String): Action[String] = Action.async(parse.tolerantText) {
    implicit request =>
      given HeaderCarrier = HeaderCarrierConverter.fromRequest(request)
      contactDetailsConnector.getContactDetails(saoSubscriptionId).map { case HttpResponse(status, body, _) =>
        Status(status)(body)
      }
  }

  def putContactDetails(saoSubscriptionId: String): Action[String] = Action.async(parse.tolerantText) {
    implicit request =>
      given HeaderCarrier = HeaderCarrierConverter.fromRequest(request)

      JsonErrorHandling.parseJson(request.body) match {
        case Right(json) =>
          val errors = JsonErrorHandling.Validators.validateContactDetails(json)
          if errors.nonEmpty then Future.successful(JsonErrorHandling.badRequest(errors))
          else
            contactDetailsConnector.putContactDetails(saoSubscriptionId: String, request.body).map { response =>
              if response.body.isBlank then Status(response.status)
              else Status(response.status)(response.body).as(MimeTypes.JSON)
            }
        case Left(errorResult) =>
          Future.successful(errorResult)
      }
  }
}
