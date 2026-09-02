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

package uk.gov.hmrc.senioraccountingofficer.repositories

import play.api.Configuration
import play.api.libs.json.{Format, Json}
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.formats.MongoJavatimeFormats
import uk.gov.hmrc.mongo.workitem.{WorkItemFields, WorkItemRepository}
import uk.gov.hmrc.senioraccountingofficer.models.workitems.SubmissionStep

import scala.concurrent.{ExecutionContext, duration}
import scala.jdk.DurationConverters.*

import java.time.{Clock, Duration as JavaDuration, Instant}
import java.util.concurrent.TimeUnit
import javax.inject.{Inject, Singleton}

final case class SubmissionOrchestration(jobId: String, step: SubmissionStep)

object SubmissionOrchestration {
  given Format[SubmissionOrchestration] = Json.format
}

@Singleton
class SubmissionOrchestrationRepository @Inject() (
    configuration: Configuration,
    mongoComponent: MongoComponent
)(using ExecutionContext)
    extends WorkItemRepository[SubmissionOrchestration](
      collectionName = "submission-orchestration",
      mongoComponent = mongoComponent,
      itemFormat = summon[Format[SubmissionOrchestration]],
      workItemFields = WorkItemFields.default
    ) {

  private val clock = Clock.systemUTC()

  given Format[Instant] = MongoJavatimeFormats.instantFormat

  override def now(): Instant = Instant.now(clock)

  override val inProgressRetryAfter: JavaDuration =
    duration.Duration(configuration.get[Long]("work-items.retry-after-seconds"), TimeUnit.SECONDS).toJava
}
