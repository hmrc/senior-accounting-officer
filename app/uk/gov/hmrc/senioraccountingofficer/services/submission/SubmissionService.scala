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

package uk.gov.hmrc.senioraccountingofficer.services.submission

import play.api.libs.json.Json
import play.api.mvc.{Result, Results}
import uk.gov.hmrc.senioraccountingofficer.models.ApiError
import uk.gov.hmrc.senioraccountingofficer.models.ApiError.Reason.*
import uk.gov.hmrc.senioraccountingofficer.models.submission.*
import uk.gov.hmrc.senioraccountingofficer.repositories.submission.*

import scala.concurrent.{ExecutionContext, Future}

import javax.inject.Inject

class SubmissionService @Inject() (queues: SubmissionQueues, states: SubmissionStateRepository)(using
    ExecutionContext
) {

  def submit(data: SubmissionData): Future[Result] = {
    if data.idempotencyKey.trim.isEmpty then
      Future.successful(Results.BadRequest(Json.toJson(ApiError(CANNOT_BE_EMPTY, Some("/idempotencyKey")))))
    else if data.idempotencyKey.length > 256 then
      Future.successful(Results.BadRequest(Json.toJson(ApiError(LENGTH_OUT_OF_BOUNDS, Some("/idempotencyKey")))))
    else
      queues(SubmissionStep.RetrieveSubscription).enqueue(SubmissionCommand.start(data)).flatMap { existing =>
        if existing.data.payload != data.payload then
          Future.successful(Results.Conflict(Json.toJson(ApiError(IDEMPOTENCY_KEY_CONFLICT, Some("/idempotencyKey")))))
        else
          states
            .initialize(existing.data)
            .map(_ => Results.Accepted(Json.obj("idempotencyKey" -> existing.data.idempotencyKey)))
      }
  }

  def status(subscriptionId: String, kind: SubmissionKind, key: String): Future[Result] =
    states.get(SubmissionData.scopeKey(subscriptionId, kind, key)).map {
      case None        => Results.NotFound(Json.toJson(ApiError(NOT_FOUND)))
      case Some(state) =>
        (state.failure, state.reference, state.pdfAttempted) match {
          case (Some(failure), _, _)         => Results.Status(failure.httpStatus)(Json.toJson(failure.error))
          case (None, Some(reference), true) =>
            val field = kind match {
              case SubmissionKind.Notification => "notificationRef"
              case SubmissionKind.Certificate  => "certificateRef"
            }
            Results.Ok(Json.obj(field -> reference))
          case _ => Results.NoContent
        }
    }
}
