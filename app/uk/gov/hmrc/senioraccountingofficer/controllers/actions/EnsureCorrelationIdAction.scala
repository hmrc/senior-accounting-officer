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
import play.api.Logging
import play.api.mvc.{ActionFilter, Result}
import uk.gov.hmrc.senioraccountingofficer.helpers.JsonErrorHandling
import uk.gov.hmrc.senioraccountingofficer.models.ApiError
import uk.gov.hmrc.senioraccountingofficer.models.ApiError.*
import uk.gov.hmrc.senioraccountingofficer.models.auth.IdentifierRequest

import scala.concurrent.{ExecutionContext, Future}

class EnsureCorrelationIdAction @Inject() ()(override implicit val executionContext: ExecutionContext)
    extends ActionFilter[IdentifierRequest]
    with Logging {

  override def filter[A](request: IdentifierRequest[A]): Future[Option[Result]] =
    if request.headers.get("correlationId").isEmpty then {
      logger.warn(s"[BAD_REQUEST][MISSING_CORRELATION_ID] ${request.method} ${request.uri}")
      Future.successful(Some(JsonErrorHandling.badRequest(Seq(ApiError(reason = Reason.MISSING_CORRELATION_ID)))))
    } else Future.successful(None)

}
