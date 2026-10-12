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

import uk.gov.hmrc.senioraccountingofficer.models.ApiError
import uk.gov.hmrc.senioraccountingofficer.models.ApiError.Reason.*
import uk.gov.hmrc.senioraccountingofficer.models.submission.SubmissionFailure

object SubmissionFailures {

  def unknownDpsOutcome: SubmissionFailure =
    SubmissionFailure(502, ApiError(DOWNSTREAM_SERVICE_UNAVAILABLE), dpsOutcomeUnknown = true)

  def response(status: Int, isDpsSubmission: Boolean = false): OperationRejected = {
    val (httpStatus, apiError) = status match {
      case 400           => (500, DOWNSTREAM_SERVICE_MISALIGNMENT)
      case 401 | 403     => (500, SERVICE_MISCONFIGURATION)
      case 503           => (502, DOWNSTREAM_SERVICE_UNAVAILABLE)
      case n if n >= 500 => (502, DOWNSTREAM_SERVICE_ERROR)
      case _             => (502, DOWNSTREAM_SERVICE_MISALIGNMENT)
    }
    OperationRejected(
      failure = SubmissionFailure(
        httpStatus = httpStatus,
        error = ApiError(apiError),
        dpsOutcomeUnknown = isDpsSubmission && status >= 500
      ),
      retryable = status == 429 || (!isDpsSubmission && status >= 500)
    )
  }

  def malformed(isDpsSubmission: Boolean): OperationRejected =
    OperationRejected(
      failure = SubmissionFailure(
        httpStatus = 500,
        error = ApiError(DOWNSTREAM_SERVICE_MISALIGNMENT),
        dpsOutcomeUnknown = isDpsSubmission
      ),
      retryable = false
    )
}
