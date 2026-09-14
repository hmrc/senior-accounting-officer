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
import uk.gov.hmrc.senioraccountingofficer.config.AppConfig
import uk.gov.hmrc.senioraccountingofficer.models.NotificationResult
import uk.gov.hmrc.senioraccountingofficer.models.requests.NotificationRequest
import uk.gov.hmrc.senioraccountingofficer.models.workitems.{NotificationCheckpoint, NotificationRetry}
import uk.gov.hmrc.senioraccountingofficer.services.NotificationService.PostNotificationResponse
import uk.gov.hmrc.senioraccountingofficer.services.NotificationService.PostNotificationResponse.Success

import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

import javax.inject.{Inject, Provider}

class NotificationService @Inject() (
    appConfig: AppConfig,
    notificationWorkflow: NotificationWorkflow,
    notificationRetryService: Provider[NotificationRetryService]
)(using ExecutionContext)
    extends Logging {

  def postNotification(subscriptionId: String, request: NotificationRequest)(using
      HeaderCarrier
  ): Future[PostNotificationResponse] =
    notificationWorkflow.run(NotificationCheckpoint.start(subscriptionId, request)).flatMap {
      case Right(checkpoint) =>
        Future.successful(Success(checkpoint.notificationReference.get))
      case Left(failure) if failure.retriable && appConfig.workItemsEnabled =>
        notificationRetryService
          .get()
          .enqueue(NotificationRetry(failure.step, failure.checkpoint))
          .recover { case NonFatal(error) =>
            logger.error(
              s"[NotificationRetry][EnqueueFailed][Step=${failure.step}][CorrelationId=${failure.checkpoint.id}]",
              error
            )
          }
          .map(_ => failure.response)
      case Left(failure) =>
        Future.successful(failure.response)
    }
}

object NotificationService {
  type DownstreamService = NotificationResult.DownstreamService
  val DownstreamService = NotificationResult.DownstreamService

  type Failure = NotificationResult.Failure

  type PostNotificationResponse = NotificationResult.PostNotificationResponse
  val PostNotificationResponse = NotificationResult.PostNotificationResponse
}
