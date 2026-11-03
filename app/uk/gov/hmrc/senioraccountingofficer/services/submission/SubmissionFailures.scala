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

import play.api.http.Status.*
import uk.gov.hmrc.senioraccountingofficer.models.ApiError
import uk.gov.hmrc.senioraccountingofficer.models.ApiError.Reason.*
import uk.gov.hmrc.senioraccountingofficer.models.submission.SubmissionFailure

object SubmissionFailures {

  def unknownDpsOutcome: SubmissionFailure =
    SubmissionFailure(BAD_GATEWAY, ApiError(DOWNSTREAM_SERVICE_UNAVAILABLE), dpsOutcomeUnknown = true)

  def response(status: Int, isDpsSubmission: Boolean = false, isInitialLookup: Boolean = false): OperationRejected = {
    val (httpStatus, apiError) = status match {
      case BAD_REQUEST                     => (INTERNAL_SERVER_ERROR, DOWNSTREAM_SERVICE_MISALIGNMENT)
      case UNAUTHORIZED | FORBIDDEN        => (INTERNAL_SERVER_ERROR, SERVICE_MISCONFIGURATION)
      case SERVICE_UNAVAILABLE             => (BAD_GATEWAY, DOWNSTREAM_SERVICE_UNAVAILABLE)
      case n if n >= INTERNAL_SERVER_ERROR => (BAD_GATEWAY, DOWNSTREAM_SERVICE_ERROR)
      case _                               => (BAD_GATEWAY, DOWNSTREAM_SERVICE_MISALIGNMENT)
    }
    OperationRejected(
      failure = SubmissionFailure(
        httpStatus = httpStatus,
        error = ApiError(apiError),
        dpsOutcomeUnknown = isDpsSubmission && status >= INTERNAL_SERVER_ERROR
      ),
      retryable =
        if isDpsSubmission || isInitialLookup then
          status == REQUEST_TIMEOUT || status == UNPROCESSABLE_ENTITY || status == TOO_MANY_REQUESTS
        else status == TOO_MANY_REQUESTS || status >= INTERNAL_SERVER_ERROR
    )
  }

  def malformed(isDpsSubmission: Boolean): OperationRejected =
    OperationRejected(
      failure = SubmissionFailure(
        httpStatus = INTERNAL_SERVER_ERROR,
        error = ApiError(DOWNSTREAM_SERVICE_MISALIGNMENT),
        dpsOutcomeUnknown = isDpsSubmission
      ),
      retryable = false
    )
}
