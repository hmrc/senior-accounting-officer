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

package uk.gov.hmrc.senioraccountingofficer.models.submission

import play.api.libs.json.{Format, Json, OFormat}
import uk.gov.hmrc.mongo.play.json.formats.MongoJavatimeFormats

import java.time.Instant

case class SubmissionCommand(
    commandKey: String,
    step: SubmissionStep,
    data: SubmissionData,
    email: Option[SubmissionEmail] = None,
    dispatched: Boolean = false,
    operationFinished: Boolean = false,
    failure: Option[SubmissionFailure] = None,
    lastError: Option[String] = None,
    leaseToken: Option[String] = None,
    expiresAt: Option[Instant] = None
) {

  def next(step: SubmissionStep, email: Option[SubmissionEmail] = None): SubmissionCommand =
    SubmissionCommand(
      s"${data.orchestrationId}/$step/${email.map(e => SubmissionData.hash(e.address + e.template)).getOrElse("")}",
      step,
      data,
      email
    )
}
object SubmissionCommand {

  given Format[Instant] = MongoJavatimeFormats.instantFormat

  given OFormat[SubmissionCommand] = Json.format

  def start(data: SubmissionData): SubmissionCommand =
    SubmissionCommand(data.scopeKey, SubmissionStep.RetrieveSubscription, data)
}
