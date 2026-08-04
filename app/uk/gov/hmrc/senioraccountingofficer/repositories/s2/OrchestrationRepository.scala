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

package uk.gov.hmrc.senioraccountingofficer.repositories.s2

import play.api.libs.json.{Format, JsPath, JsString, Json, OFormat, Reads, Writes}
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.formats.MongoJavatimeFormats
import uk.gov.hmrc.mongo.workitem.{WorkItemFields, WorkItemRepository}

import java.time
import java.time.{Clock, Instant, Duration as JavaDuration}
import java.util.concurrent.TimeUnit
import javax.inject.{Inject, Singleton}
import scala.concurrent.duration.*
import scala.concurrent.{ExecutionContext, duration}
import scala.jdk.DurationConverters.*

enum JobType {
  case DPS, PDF, ZIP, SDES, Email, Audit
}

object JobType {
  given Reads[JobType]  = JsPath.read[String].map(JobType.valueOf)
  given Writes[JobType] = Writes[JobType](job => JsString(job.toString))
}

enum SubmissionType {
  case Notification, Certificate
}

object SubmissionType {
  given Reads[SubmissionType]  = JsPath.read[String].map(SubmissionType.valueOf)
  given Writes[SubmissionType] = Writes[SubmissionType](sub => JsString(sub.toString))
}

final case class SubmissionOrchestration(correlationId: String, submissionType: SubmissionType, jobType: JobType)

object SubmissionOrchestration {
  given format: OFormat[SubmissionOrchestration] = Json.format
}

@Singleton
class OrchestrationRepository @Inject() (
    mongoComponent: MongoComponent,
    clock: Clock
)(using ExecutionContext)
    extends WorkItemRepository[SubmissionOrchestration](
      collectionName = "s2-orchestration",
      mongoComponent = mongoComponent,
      itemFormat = SubmissionOrchestration.format,
      workItemFields = WorkItemFields.default
    ) {

  given instantFormat: Format[Instant] = MongoJavatimeFormats.instantFormat

  override def now(): Instant = clock.instant()

  override val inProgressRetryAfter: JavaDuration =
    Duration(60, TimeUnit.SECONDS).toJava
}
