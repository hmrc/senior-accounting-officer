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

package uk.gov.hmrc.senioraccountingofficer.controllers.testOnly

import play.api.mvc.{Action, ControllerComponents}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController
import uk.gov.hmrc.play.http.HeaderCarrierConverter
import uk.gov.hmrc.senioraccountingofficer.services.documentum.DocumentumPackageService

import scala.concurrent.ExecutionContext
import scala.util.control.NonFatal

import javax.inject.Inject

class DocumentumPackageController @Inject() (
    cc: ControllerComponents,
    documentumPackageService: DocumentumPackageService
)(using ExecutionContext)
    extends BackendController(cc) {

  def download(submissionId: String, fileName: String): Action[?] = Action.async { implicit request =>
    given HeaderCarrier = HeaderCarrierConverter.fromRequest(request)

    documentumPackageService
      .download(submissionId, fileName)
      .map {
        case Some(source) => Ok.chunked(source).as("application/zip")
        case None         => NotFound
      }
      .recover { case NonFatal(_) => NotFound }
  }
}
