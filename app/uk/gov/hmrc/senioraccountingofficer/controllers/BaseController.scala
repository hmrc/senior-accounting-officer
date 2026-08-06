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

import play.api.mvc.{ControllerComponents, RequestHeader}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

abstract class BaseController(
    controllerComponents: ControllerComponents
) extends BackendController(controllerComponents) {

  override implicit protected def hc(implicit request: RequestHeader): HeaderCarrier = {
    val headerCarrier = super.hc(request)
    request.headers.get("correlationId") match {
      case Some(correlationId) =>
        headerCarrier.copy(extraHeaders = headerCarrier.extraHeaders :+ "correlationId" -> correlationId)
      case None => headerCarrier
    }
  }
}
