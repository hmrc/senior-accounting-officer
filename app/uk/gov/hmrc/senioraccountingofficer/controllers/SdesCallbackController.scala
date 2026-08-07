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

import play.api.mvc.ControllerComponents
import scala.concurrent.ExecutionContext
import play.api.Logging
import javax.inject.Inject
import play.api.mvc.Action
import play.api.libs.json.JsValue
import uk.gov.hmrc.senioraccountingofficer.models.sdes.SdesFileNotification
import uk.gov.hmrc.senioraccountingofficer.repositories.SdesFileStatusRepository

class SdesCallbackController @Inject() (
    cc: ControllerComponents,
    sdesFileStatusRepository: SdesFileStatusRepository
)(using ExecutionContext)
    extends BaseController(cc)
    with Logging {
  // TODO: store state in mongodb
  // TODO: FileReceived -> create record in mongo
  // TODO: FileProcessed -> update mongo; delete zip from object store
  // TODO: FileProcessingFailure -> update mongo; log error
  def callback(): Action[JsValue] = Action(parse.json) { request =>
    // TODO: log if json conversion fails
    val notification = request.body.as[SdesFileNotification]
    sdesFileStatusRepository.upsert(notification)
    Ok("jacobwozere")
  }
}
