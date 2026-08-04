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

import org.mongodb.scala.bson.conversions.Bson
import org.mongodb.scala.model.*
import play.api.libs.json.{Format, Json, OFormat}
import uk.gov.hmrc.mdc.Mdc
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.PlayMongoRepository
import uk.gov.hmrc.mongo.play.json.formats.MongoJavatimeFormats

import java.time.{Clock, Instant}
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

final case class Subscription(subscriptionId: String, companyName: String)

object Subscription {
  given OFormat[Subscription] = Json.format
}

final case class WorkRequest(
    correlationId: String,
    subscription: Subscription,
    request: String,
    lastUpdated: Instant = Instant.now()
)

object WorkRequest {
  given instantFormat: Format[Instant] = MongoJavatimeFormats.instantFormat
  given format: OFormat[WorkRequest]   = Json.format
}

class RequestRepository @Inject() (
    mongoComponent: MongoComponent,
    clock: Clock
)(using ec: ExecutionContext)
    extends PlayMongoRepository[WorkRequest](
      collectionName = "s2-request",
      mongoComponent = mongoComponent,
      domainFormat = WorkRequest.format,
      indexes = Seq(
        IndexModel(
          Indexes.ascending("lastUpdated"),
          IndexOptions()
            .name("lastUpdatedIdx")
            .expireAfter(1, TimeUnit.DAYS)
        )
      )
    ) {

  given instantFormat: Format[Instant] = MongoJavatimeFormats.instantFormat

  private def byId(correlationId: String): Bson = Filters.equal("correlationId", correlationId)

  def keepAlive(correlationId: String): Future[Boolean] = Mdc.preservingMdc {
    collection
      .updateOne(
        filter = byId(correlationId),
        update = Updates.set("lastUpdated", Instant.now(clock))
      )
      .toFuture()
      .map(_ => true)
  }

  def get(id: String): Future[Option[WorkRequest]] = Mdc.preservingMdc {
    keepAlive(id).flatMap { _ =>
      collection
        .find(byId(id))
        .headOption()
    }
  }

  def set(state: WorkRequest): Future[Boolean] = Mdc.preservingMdc {

    val updatedState = state copy (lastUpdated = Instant.now(clock))

    collection
      .replaceOne(
        filter = byId(updatedState.correlationId),
        replacement = updatedState,
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
