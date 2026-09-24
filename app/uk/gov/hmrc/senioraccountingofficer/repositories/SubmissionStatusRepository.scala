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

import org.mongodb.scala.bson.conversions.Bson
import org.mongodb.scala.model.*
import play.api.libs.json.Format
import uk.gov.hmrc.mdc.Mdc
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.PlayMongoRepository
import uk.gov.hmrc.mongo.play.json.formats.MongoJavatimeFormats
import uk.gov.hmrc.senioraccountingofficer.config.AppConfig
import uk.gov.hmrc.senioraccountingofficer.models.mongo.SubmissionStatus

import scala.concurrent.{ExecutionContext, Future}

import java.time.{Clock, Instant}
import java.util.concurrent.TimeUnit
import javax.inject.Inject

class SubmissionStatusRepository @Inject() (
    appConfig: AppConfig,
    mongoComponent: MongoComponent,
    clock: Clock
)(using ExecutionContext)
    extends PlayMongoRepository[SubmissionStatus](
      collectionName = "submission-status",
      mongoComponent = mongoComponent,
      domainFormat = SubmissionStatus.format,
      indexes = Seq(
        IndexModel(
          Indexes.ascending("lastUpdated"),
          IndexOptions()
            .name("lastUpdatedIdx")
            .expireAfter(appConfig.cacheTtl, TimeUnit.SECONDS)
        )
      )
    ) {

  given instantFormat: Format[Instant] = MongoJavatimeFormats.instantFormat

  private def byId(correlationId: String): Bson = Filters.equal("_id", correlationId)

  def keepAlive(correlationId: String): Future[Boolean] = Mdc.preservingMdc {
    collection
      .updateOne(
        filter = byId(correlationId),
        update = Updates.set("lastUpdated", Instant.now(clock))
      )
      .toFuture()
      .map(_ => true)
  }

  def get(correlationId: String): Future[Option[SubmissionStatus]] = {
    keepAlive(correlationId).flatMap { _ =>
      collection
        .find(byId(correlationId))
        .headOption()
    }
  }

  def set(submissionStatus: SubmissionStatus): Future[true] = Mdc.preservingMdc {

    val updatedStatus = submissionStatus copy (lastUpdated = Instant.now(clock))

    collection
      .replaceOne(
        filter = byId(updatedStatus._id),
        replacement = updatedStatus,
        options = ReplaceOptions().upsert(true)
      )
      .toFuture()
      .map(_ => true)
  }

  def clear(correlationId: String): Future[Boolean] = Mdc.preservingMdc {
    collection
      .deleteOne(byId(correlationId))
      .toFuture()
      .map(_ => true)
  }
}
