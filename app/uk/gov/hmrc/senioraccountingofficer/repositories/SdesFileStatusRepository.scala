/*
 * Copyright 2025 HM Revenue & Customs
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
import org.mongodb.scala.model.IndexModel
import org.mongodb.scala.model.IndexOptions
import org.mongodb.scala.model.Indexes
import org.mongodb.scala.model.ReplaceOptions
import uk.gov.hmrc.mdc.Mdc
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.PlayMongoRepository
import uk.gov.hmrc.senioraccountingofficer.models.sdes.SdesFileNotification

import scala.concurrent.{ExecutionContext, Future}

import javax.inject.{Inject, Singleton}

@Singleton
class SdesFileStatusRepository @Inject() (
    mongoComponent: MongoComponent
)(using ec: ExecutionContext)
    extends PlayMongoRepository[SdesFileNotification](
      collectionName = "sdes-file-notifications",
      mongoComponent = mongoComponent,
      domainFormat = SdesFileNotification.format,
      indexes = Seq(
        IndexModel(
          Indexes.ascending(s"correlationID"),
          IndexOptions().name(s"correlationIdIdx").unique(true).sparse(true)
        )
      )
    ) {

  private def byId(id: String): Bson = Filters.equal("_id", id)

  def upsert(answers: SdesFileNotification): Future[true] = Mdc.preservingMdc {
    collection
      .replaceOne(
        filter = byId(answers.correlationID),
        replacement = answers,
        options = ReplaceOptions().upsert(true)
      )
      .toFuture()
      .map(_ => true)
  }
}
