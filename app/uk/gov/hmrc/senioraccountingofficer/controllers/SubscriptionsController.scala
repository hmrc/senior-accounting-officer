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
import play.api.mvc.{Action, ControllerComponents}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController
import uk.gov.hmrc.play.http.HeaderCarrierConverter
import uk.gov.hmrc.senioraccountingofficer.connectors.SubscriptionsConnector

import scala.concurrent.ExecutionContext

import javax.inject.Inject

class SubscriptionsController @Inject() (
    cc: ControllerComponents,
    subscriptionsConnector: SubscriptionsConnector
)(using ec: ExecutionContext)
    extends BackendController(cc) {

  def putSubscription: Action[String] = Action.async(parse.tolerantText) { implicit request =>
    given HeaderCarrier = HeaderCarrierConverter.fromRequest(request)

    subscriptionsConnector.putSubscription(request.body).map { response =>
      if response.body.isBlank then Status(response.status)
      else Status(response.status)(response.body).as(MimeTypes.JSON)
    }
  }
}
