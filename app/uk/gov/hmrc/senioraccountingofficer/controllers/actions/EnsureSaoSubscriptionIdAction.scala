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

package uk.gov.hmrc.senioraccountingofficer.controllers.actions

import com.google.inject.Inject
import play.api.mvc.{ActionFilter, Result}
import uk.gov.hmrc.senioraccountingofficer.helpers.JsonErrorHandling
import uk.gov.hmrc.senioraccountingofficer.models.ApiError
import uk.gov.hmrc.senioraccountingofficer.models.ApiError.Reason
import uk.gov.hmrc.senioraccountingofficer.models.auth.IdentifierRequest

import scala.concurrent.{ExecutionContext, Future}

class EnsureSaoSubscriptionIdAction @Inject() (expectedId: String)(
    override implicit val executionContext: ExecutionContext
) extends ActionFilter[IdentifierRequest] {

  override protected def filter[A](request: IdentifierRequest[A]): Future[Option[Result]] = {
    if request.saoSubscriptionId != expectedId then {
      Future.successful(Some(JsonErrorHandling.badRequest(Seq(ApiError(reason = Reason.AUTH_MISALIGNMENT)))))
    } else {
      Future.successful(None)
    }
  }
}
