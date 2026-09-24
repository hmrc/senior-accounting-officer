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

package uk.gov.hmrc.senioraccountingofficer.models.mongo

import play.api.libs.json.{Format, JsObject, Json}
import uk.gov.hmrc.mongo.play.json.formats.MongoJavatimeFormats

import java.time.{Clock, Instant}

final case class SubmissionStatus(
    _id: String,
    submissionId: Option[String],
    failed: Boolean,
    lastUpdated: Instant
)

object SubmissionStatus {
  given instantFormat: Format[Instant]   = MongoJavatimeFormats.instantFormat
  given format: Format[SubmissionStatus] = Json.format

  def apply(correlationId: String, submissionId: Option[String] = None)(using Clock) =
    new SubmissionStatus(
      _id = correlationId,
      submissionId = submissionId,
      failed = false,
      lastUpdated = Instant.now(summon[Clock])
    )
}
