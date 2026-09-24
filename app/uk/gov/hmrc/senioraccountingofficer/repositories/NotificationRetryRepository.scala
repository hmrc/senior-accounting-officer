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

import org.bson.types.ObjectId
import org.mongodb.scala.model.{Filters, Updates}
import play.api.Configuration
import play.api.libs.json.Format
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.Codecs
import uk.gov.hmrc.mongo.play.json.formats.MongoJavatimeFormats
import uk.gov.hmrc.mongo.workitem.{ProcessingStatus, WorkItemFields, WorkItemRepository}
import uk.gov.hmrc.senioraccountingofficer.models.workitems.NotificationRetry

import scala.concurrent.{ExecutionContext, Future, duration}
import scala.jdk.DurationConverters.*

import java.time.{Clock, Duration as JavaDuration, Instant}
import java.util.concurrent.TimeUnit
import javax.inject.{Inject, Singleton}

@Singleton
class NotificationRetryRepository @Inject() (
    configuration: Configuration,
    mongoComponent: MongoComponent
)(using ExecutionContext)
    extends WorkItemRepository[NotificationRetry](
      collectionName = "notification-retries",
      mongoComponent = mongoComponent,
      itemFormat = summon[Format[NotificationRetry]],
      workItemFields = WorkItemFields.default
    ) {

  private val clock = Clock.systemUTC()

  given Format[Instant] = MongoJavatimeFormats.instantFormat

  override def now(): Instant = Instant.now(clock)

  override val inProgressRetryAfter: JavaDuration =
    duration.Duration(configuration.get[Long]("work-items.retry-after-seconds"), TimeUnit.SECONDS).toJava

  def updateAndMarkFailed(id: ObjectId, retry: NotificationRetry): Future[Boolean] =
    collection
      .updateOne(
        filter = Filters.and(
          Filters.equal(workItemFields.id, id),
          Filters.equal(workItemFields.status, ProcessingStatus.InProgress)
        ),
        update = Updates.combine(
          Updates.set(workItemFields.item, Codecs.toBson(retry)),
          Updates.set(workItemFields.status, ProcessingStatus.Failed),
          Updates.set(workItemFields.updatedAt, now()),
          Updates.inc(workItemFields.failureCount, 1)
        )
      )
      .toFuture()
      .map(_.getModifiedCount > 0)
}
