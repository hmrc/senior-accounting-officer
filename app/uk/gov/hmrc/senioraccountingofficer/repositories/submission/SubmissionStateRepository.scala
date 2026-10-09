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

import org.mongodb.scala.bson.conversions.Bson
import org.mongodb.scala.model.*
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.{Codecs, PlayMongoRepository}
import uk.gov.hmrc.senioraccountingofficer.models.submission.*

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

import java.time.{Clock, Instant}
import java.util.concurrent.TimeUnit
import javax.inject.{Inject, Singleton}

@Singleton
class SubmissionStateRepository @Inject() (mongo: MongoComponent, clock: Clock)(using ExecutionContext)
    extends PlayMongoRepository[SubmissionState](
      collectionName = "submission-orchestrations",
      mongoComponent = mongo,
      domainFormat = summon[play.api.libs.json.OFormat[SubmissionState]],
      indexes = Seq(
        IndexModel(Indexes.ascending("scopeKey")),
        IndexModel(Indexes.ascending("expiresAt"), IndexOptions().expireAfter(0L, TimeUnit.SECONDS))
      )
    ) {

  def initialize(data: SubmissionData): Future[Unit] = collection
    .updateOne(
      Filters.equal("_id", data.orchestrationId),
      Updates.setOnInsert("scopeKey", data.scopeKey),
      UpdateOptions().upsert(true)
    )
    .toFuture()
    .map(_ => ())

  def get(scopeKey: String): Future[Option[SubmissionState]] = collection
    .find(
      Filters.and(
        Filters.equal("scopeKey", scopeKey),
        Filters.or(Filters.exists("expiresAt", false), Filters.gt("expiresAt", clock.instant()))
      )
    )
    .headOption()

  def publish(command: SubmissionCommand): Future[Unit] =
    Future.fromTry(Try(publicationUpdates(command))).flatMap { updates =>
      if updates.isEmpty then Future.unit
      else
        collection
          .updateOne(
            Filters.and(Filters.equal("_id", command.data.orchestrationId), Filters.exists("outcomeRecordedAt", false)),
            Updates.combine(updates*)
          )
          .toFuture()
          .flatMap { result =>
            if result.getMatchedCount > 0 then Future.unit
            else ensureStateExists(command.data.orchestrationId)
          }
    }

  private def publicationUpdates(command: SubmissionCommand): Seq[Bson] = {
    val data = command.data
    command.failure match {
      case Some(failure) =>
        Seq(Updates.set("failure", Codecs.toBson(failure)), Updates.set("outcomeRecordedAt", clock.instant()))
      case None if data.pdfAttempted =>
        Seq(
          Updates.set(
            "reference",
            data.reference.getOrElse(throw new IllegalStateException("Missing required submission field: reference"))
          ),
          Updates.set("pdfAttempted", true),
          Updates.set("pdfStored", data.pdfStored),
          Updates.set("outcomeRecordedAt", clock.instant())
        )
      case None => data.reference.toSeq.map(reference => Updates.set("reference", reference))
    }
  }

  private def ensureStateExists(orchestrationId: String): Future[Unit] =
    collection.find(Filters.equal("_id", orchestrationId)).headOption().flatMap {
      case Some(_) => Future.unit // Already published; handoff is replayable.
      case None    => Future.failed(new IllegalStateException("Missing submission state"))
    }

  def expire(id: String, at: Instant): Future[Instant] = collection
    .findOneAndUpdate(
      Filters.and(Filters.equal("_id", id), Filters.exists("expiresAt", false)),
      Updates.set("expiresAt", at),
      FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER)
    )
    .toFutureOption()
    .flatMap {
      case Some(state) => Future.successful(expiryOf(state))
      case None        =>
        collection
          .find(Filters.equal("_id", id))
          .headOption()
          .map {
            case Some(state) => expiryOf(state)
            case None        => throw new IllegalStateException("Missing terminal submission state")
          }
    }

  private def expiryOf(state: SubmissionState): Instant =
    state.expiresAt.getOrElse(throw new IllegalStateException("Missing terminal submission state"))
}
