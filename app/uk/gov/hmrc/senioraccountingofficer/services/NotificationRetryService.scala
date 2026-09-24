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

package uk.gov.hmrc.senioraccountingofficer.services

import play.api.Logging
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.mongo.workitem.ProcessingStatus.{Failed, PermanentlyFailed}
import uk.gov.hmrc.senioraccountingofficer.config.AppConfig
import uk.gov.hmrc.senioraccountingofficer.models.workitems.NotificationRetry
import uk.gov.hmrc.senioraccountingofficer.repositories.NotificationRetryRepository

import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

import java.time.Instant
import javax.inject.{Inject, Singleton}

@Singleton
class NotificationRetryService @Inject() (
    appConfig: AppConfig,
    repository: NotificationRetryRepository,
    workflow: NotificationWorkflow
)(using ExecutionContext)
    extends Logging {

  def enqueue(retry: NotificationRetry): Future[Unit] =
    repository.pushNew(retry).map(_ => ())

  def processNext(): Future[Unit] = {
    val now = Instant.now()
    repository
      .pullOutstanding(
        failedBefore = now.minusSeconds(appConfig.workItemsPollIntervalSeconds.toLong * 4),
        availableBefore = now
      )
      .flatMap {
        case Some(workItem) =>
          val retry           = workItem.item
          given HeaderCarrier = HeaderCarrier(extraHeaders = Seq("correlationId" -> retry.checkpoint.id))

          workflow
            .run(retry.checkpoint)
            .flatMap {
              case Right(_) =>
                requireUpdate(repository.completeAndDelete(workItem.id), s"complete ${workItem.id}")
              case Left(failure) if failure.retriable =>
                requireUpdate(
                  repository.updateAndMarkFailed(
                    workItem.id,
                    NotificationRetry(failure.step, failure.checkpoint)
                  ),
                  s"update failed retry ${workItem.id}"
                )
              case Left(_) =>
                requireUpdate(repository.markAs(workItem.id, PermanentlyFailed), s"permanently fail ${workItem.id}")
            }
            .recoverWith { case NonFatal(error) =>
              logger.error(
                s"[NotificationRetry][ProcessingFailed][CorrelationId=${retry.checkpoint.id}]",
                error
              )
              requireUpdate(repository.markAs(workItem.id, Failed), s"fail ${workItem.id}")
            }
        case None => Future.unit
      }
  }

  private def requireUpdate(result: Future[Boolean], operation: String): Future[Unit] =
    result.flatMap {
      case true  => Future.unit
      case false => Future.failed(new IllegalStateException(s"Unable to $operation"))
    }
}
