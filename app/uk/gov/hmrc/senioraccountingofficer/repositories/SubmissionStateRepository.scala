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
import org.mongodb.scala.model.{Filters, IndexModel, IndexOptions, Indexes, ReplaceOptions, Updates}
import play.api.libs.json.Format
import uk.gov.hmrc.mdc.Mdc
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.PlayMongoRepository
import uk.gov.hmrc.mongo.play.json.formats.MongoJavatimeFormats
import uk.gov.hmrc.senioraccountingofficer.models.workitems.SubmissionWorkItemState

import scala.concurrent.{ExecutionContext, Future}

import java.time.{Clock, Instant}
import java.util.concurrent.TimeUnit
import javax.inject.{Inject, Singleton}

@Singleton
class SubmissionStateRepository @Inject() (
    mongoComponent: MongoComponent
)(using ec: ExecutionContext)
    extends PlayMongoRepository[SubmissionWorkItemState](
      collectionName = "submission-state",
      mongoComponent = mongoComponent,
      domainFormat = summon[Format[SubmissionWorkItemState]],
      indexes = Seq(
        IndexModel(
          Indexes.ascending("jobId"),
          IndexOptions().name("jobIdIdx").unique(true)
        ),
        IndexModel(
          Indexes.ascending("lastUpdated"),
          IndexOptions().name("lastUpdatedIdx").expireAfter(7, TimeUnit.DAYS)
        )
      )
    ) {

  private val clock = Clock.systemUTC()

  given Format[Instant] = MongoJavatimeFormats.instantFormat

  private def byId(jobId: String): Bson = Filters.equal("jobId", jobId)

  def get(jobId: String): Future[Option[SubmissionWorkItemState]] = Mdc.preservingMdc {
    collection.find(byId(jobId)).headOption()
  }

  def set(state: SubmissionWorkItemState): Future[Boolean] = Mdc.preservingMdc {
    collection
      .replaceOne(
        filter = byId(state.jobId),
        replacement = state.copy(lastUpdated = Instant.now(clock)),
        options = ReplaceOptions().upsert(true)
      )
      .toFuture()
      .map(_ => true)
  }

  def touch(jobId: String): Future[Boolean] = Mdc.preservingMdc {
    collection
      .updateOne(
        filter = byId(jobId),
        update = Updates.set("lastUpdated", Instant.now(clock))
      )
      .toFuture()
      .map(_ => true)
  }

  def clear(jobId: String): Future[Boolean] = Mdc.preservingMdc {
    collection.deleteOne(byId(jobId)).toFuture().map(_ => true)
  }
}
