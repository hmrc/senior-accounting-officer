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

import play.api.mvc.{Action, AnyContent, ControllerComponents}
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.{HttpResponse, StringContextOps}
import javax.inject.Inject
import scala.concurrent.ExecutionContext

class ObligationController @Inject() (httpClient: HttpClientV2, cc: ControllerComponents)(using ExecutionContext)
    extends BackendController(cc) {
  def getObligation(saoSubscriptionId: String): Action[AnyContent] = Action.async { implicit request =>
    httpClient
      .get(url"http://localhost:10061/obligation/123")
      .setHeader(("Authorization", "Basic Q2xpZW50SWQ6Q2xpZW50U2VjcmV0"))
      .execute[HttpResponse]
      .map {
        case HttpResponse(OK, body, _) => Ok(body)
        case HttpResponse(_, body, _)  => Ok(body)
      }
  }
}
