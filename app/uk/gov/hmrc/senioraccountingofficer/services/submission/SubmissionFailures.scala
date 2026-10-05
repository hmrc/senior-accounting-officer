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

  def ambiguous: SubmissionFailure = SubmissionFailure(502, ApiError(DOWNSTREAM_SERVICE_UNAVAILABLE), ambiguous = true)

  def response(status: Int, dpsPost: Boolean = false): OperationRejected = {
    val (http, reason) = status match {
      case 400           => (500, DOWNSTREAM_SERVICE_MISALIGNMENT)
      case 401 | 403     => (500, SERVICE_MISCONFIGURATION)
      case 503           => (502, DOWNSTREAM_SERVICE_UNAVAILABLE)
      case n if n >= 500 => (502, DOWNSTREAM_SERVICE_ERROR)
      case _             => (502, DOWNSTREAM_SERVICE_MISALIGNMENT)
    }
    OperationRejected(
      SubmissionFailure(http, ApiError(reason), dpsPost && status >= 500),
      status == 429 || (!dpsPost && status >= 500)
    )
  }

  def malformed(dpsPost: Boolean): OperationRejected =
    OperationRejected(SubmissionFailure(500, ApiError(DOWNSTREAM_SERVICE_MISALIGNMENT), ambiguous = dpsPost), false)
}
