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

package uk.gov.hmrc.senioraccountingofficer.repositories.submission

import org.mongodb.scala.MongoWriteException
import org.mongodb.scala.bson.conversions.Bson
import org.mongodb.scala.model.*
import play.api.Configuration
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.Codecs
import uk.gov.hmrc.mongo.workitem.*
import uk.gov.hmrc.senioraccountingofficer.models.submission.*

import scala.concurrent.{ExecutionContext, Future}

import java.time.temporal.ChronoUnit.MILLIS
import java.time.{Clock, Duration, Instant}
import java.util.UUID
import java.util.concurrent.TimeUnit

class SubmissionWorkRepository(
    val step: SubmissionStep,
    mongo: MongoComponent,
    configuration: Configuration,
    clock: Clock
)(using ExecutionContext)
    extends WorkItemRepository[SubmissionCommand](
      collectionName = s"submission-${step.toString.toLowerCase}",
      mongoComponent = mongo,
      itemFormat = summon[play.api.libs.json.Format[SubmissionCommand]],
      workItemFields = WorkItemFields.default.copy(availableAt = "availableAt"),
      extraIndexes = Seq(
        IndexModel(Indexes.ascending("item.commandKey"), IndexOptions().unique(true)),
        IndexModel(Indexes.ascending("item.data.orchestrationId")),
        IndexModel(Indexes.ascending("item.expiresAt"), IndexOptions().expireAfter(0L, TimeUnit.SECONDS))
      )
    ) {

  override def now(): Instant                 = clock.instant().truncatedTo(MILLIS)
  override val inProgressRetryAfter: Duration = Duration.ofSeconds(configuration.get[Long]("work-items.lease-seconds"))

  def enqueue(command: SubmissionCommand): Future[SubmissionCommand] = {
    require(command.step == step)
    val key = Filters.equal("item.commandKey", command.commandKey)
    // An expired request key can be reused even before Mongo's TTL monitor runs.
    for {
      _         <- collection.deleteOne(Filters.and(key, Filters.lte("item.expiresAt", now()))).toFuture()
      persisted <- pushNew(command).map(_.item).recoverWith {
        case error: MongoWriteException if error.getError.getCode == 11000 =>
          collection.find(key).head().map(_.item)
      }
    } yield persisted
  }

  def claim(): Future[Option[WorkItem[SubmissionCommand]]] =
    pullOutstanding(now(), now()).flatMap {
      case None       => Future.successful(None)
      case Some(work) => claimLease(work)
    }

  private def claimLease(work: WorkItem[SubmissionCommand]): Future[Option[WorkItem[SubmissionCommand]]] =
    collection
      .findOneAndUpdate(
        Filters.and(
          Filters.equal("_id", work.id),
          Filters.equal("status", ProcessingStatus.InProgress),
          Filters.equal("updatedAt", work.updatedAt)
        ),
        Updates.set("item.leaseToken", UUID.randomUUID().toString),
        FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER)
      )
      .toFutureOption()

  private def ownedBy(work: WorkItem[SubmissionCommand]): Bson = Filters.and(
    Filters.equal("_id", work.id),
    Filters.equal("status", ProcessingStatus.InProgress),
    Filters.equal("item.leaseToken", work.item.leaseToken.get)
  )

  private def updateOwned(work: WorkItem[SubmissionCommand], updates: Bson): Future[Unit] =
    collection.updateOne(ownedBy(work), updates).toFuture().flatMap { result =>
      if result.getMatchedCount == 1 then Future.unit
      else Future.failed(new IllegalStateException("Work item lease lost"))
    }

  def heartbeat(work: WorkItem[SubmissionCommand]): Future[Unit] = updateOwned(work, Updates.set("updatedAt", now()))

  def save(work: WorkItem[SubmissionCommand], command: SubmissionCommand): Future[WorkItem[SubmissionCommand]] =
    updateOwned(work, Updates.combine(Updates.set("item", Codecs.toBson(command)), Updates.set("updatedAt", now())))
      .map(_ => work.copy(item = command))

  def finish(work: WorkItem[SubmissionCommand]): Future[Unit] = updateOwned(
    work,
    Updates.combine(
      Updates.set(
        "status",
        if work.item.failure.isDefined then ProcessingStatus.PermanentlyFailed else ProcessingStatus.Succeeded
      ),
      Updates.set("updatedAt", now())
    )
  )

  def retry(work: WorkItem[SubmissionCommand], delay: Long, error: String): Future[Unit] = updateOwned(
    work,
    Updates.combine(
      Updates.set("status", ProcessingStatus.Failed),
      Updates.set("availableAt", now().plusSeconds(delay)),
      Updates.set("updatedAt", now()),
      Updates.set("item.lastError", error),
      Updates.inc("failureCount", 1)
    )
  )

  def hasActive(orchestrationId: String): Future[Boolean] = collection
    .find(
      Filters.and(
        Filters.equal("item.data.orchestrationId", orchestrationId),
        Filters.nin("status", ProcessingStatus.Succeeded, ProcessingStatus.PermanentlyFailed)
      )
    )
    .headOption()
    .map(_.isDefined)

  def expire(orchestrationId: String, at: Instant): Future[Unit] = collection
    .updateMany(
      Filters.and(Filters.equal("item.data.orchestrationId", orchestrationId), Filters.exists("item.expiresAt", false)),
      Updates.set("item.expiresAt", at)
    )
    .toFuture()
    .map(_ => ())

  def retentionCandidates(): Future[Seq[WorkItem[SubmissionCommand]]] = collection
    .find(
      Filters.and(
        Filters.in("status", ProcessingStatus.Succeeded, ProcessingStatus.PermanentlyFailed),
        Filters.exists("item.expiresAt", false)
      )
    )
    .toFuture()
}
