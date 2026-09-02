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

import org.apache.pekko.actor.ActorSystem
import play.api.{Environment, Logging}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.mongo.workitem.ProcessingStatus.{Failed, PermanentlyFailed}
import uk.gov.hmrc.senioraccountingofficer.models.workitems.{SubmissionFlowType, SubmissionStateStatus}
import uk.gov.hmrc.senioraccountingofficer.repositories.SubmissionOrchestrationRepository

import scala.concurrent.duration.*
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

import java.time.Instant
import javax.inject.{Inject, Singleton}

@Singleton
class SubmissionWorkItemPoller @Inject() (
    appConfig: uk.gov.hmrc.senioraccountingofficer.config.AppConfig,
    environment: Environment,
    actorSystem: ActorSystem,
    submissionStateRepository: uk.gov.hmrc.senioraccountingofficer.repositories.SubmissionStateRepository,
    submissionOrchestrationRepository: SubmissionOrchestrationRepository,
    notificationService: NotificationService
)(using ec: ExecutionContext)
    extends Logging {

  if appConfig.workItemsEnabled && environment.mode != play.api.Mode.Test then
    actorSystem.scheduler.scheduleWithFixedDelay(5.seconds, appConfig.workItemsPollIntervalSeconds.seconds) { () =>
      processOutstanding().recover { case NonFatal(e) =>
        logger.warn("[WorkItems][Poller][Failed]", e)
      }
      ()
    }

  def processOutstanding(): Future[Unit] = {
    val now = Instant.now()

    submissionOrchestrationRepository.pullOutstanding(
      failedBefore = now.minusSeconds(appConfig.workItemsPollIntervalSeconds.toLong * 4),
      availableBefore = now
    ).flatMap {
      case Some(workItem) =>
        submissionStateRepository.get(workItem.item.jobId).flatMap {
          case Some(state) if state.flowType == SubmissionFlowType.Notification =>
            val workerHeaderCarrier = HeaderCarrier(extraHeaders = Seq("correlationId" -> state.jobId))
            given HeaderCarrier = workerHeaderCarrier

            notificationService.processWorkItem(state).flatMap { completed =>
              if completed then
                submissionStateRepository.clear(state.jobId)
                  .flatMap(_ => submissionOrchestrationRepository.completeAndDelete(workItem.id).map(_ => ()))
              else
                submissionStateRepository.get(state.jobId).flatMap {
                  case Some(latest) if latest.status == SubmissionStateStatus.PermanentlyFailed =>
                    submissionOrchestrationRepository.markAs(workItem.id, PermanentlyFailed).map(_ => ())
                  case _                                                              =>
                    submissionOrchestrationRepository.markAs(workItem.id, Failed).map(_ => ())
                }
            }
          case Some(_) =>
            submissionOrchestrationRepository.markAs(workItem.id, PermanentlyFailed).map(_ => ())
          case None        =>
            submissionOrchestrationRepository.markAs(workItem.id, PermanentlyFailed).map(_ => ())
        }
      case None           => Future.unit
    }
  }
}
