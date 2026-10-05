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

import play.api.Configuration
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.senioraccountingofficer.models.submission.SubmissionStep

import scala.concurrent.ExecutionContext

import java.time.Clock
import javax.inject.{Inject, Singleton}

// Distinct repository instances/collections per command; both submission types use the same stages.
@Singleton
class SubmissionQueues @Inject() (mongo: MongoComponent, configuration: Configuration, clock: Clock)(using
    ExecutionContext
) {

  val all: Map[SubmissionStep, SubmissionWorkRepository] = SubmissionStep.values.iterator.map { step =>
    step -> new SubmissionWorkRepository(step, mongo, configuration, clock)
  }.toMap

  def apply(step: SubmissionStep): SubmissionWorkRepository = all(step)
}
