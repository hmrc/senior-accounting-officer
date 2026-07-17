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

import cats.data.EitherT
import play.api.http.Status.*
import play.api.libs.json.*
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}
import uk.gov.hmrc.objectstore.client.Path
import uk.gov.hmrc.objectstore.client.play.Implicits.*
import uk.gov.hmrc.objectstore.client.play.PlayObjectStoreClient
import uk.gov.hmrc.senioraccountingofficer.connectors.NotificationConnector
import uk.gov.hmrc.senioraccountingofficer.models.dps.{NotificationDpsRequest, NotificationDpsResponse}
import uk.gov.hmrc.senioraccountingofficer.services.NotificationService.*
import uk.gov.hmrc.senioraccountingofficer.services.NotificationService.DownstreamService.DPS
import uk.gov.hmrc.senioraccountingofficer.services.NotificationService.PostNotificationResponse.*

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try
import scala.util.control.NonFatal

import javax.inject.Inject

class NotificationService @Inject() (
    notificationConnector: NotificationConnector,
    objectStoreClient: PlayObjectStoreClient
)(using ExecutionContext) {

  def postNotification(subscriptionId: String, request: NotificationDpsRequest)(using
      HeaderCarrier
  ): Future[PostNotificationResponse] = {
    for {
      dpsResult      <- postNotificationDps(subscriptionId, request)
      isPdfAvailable <- generateAndUploadPdf(dpsResult.notificationRef)
    } yield Success(notificationId = dpsResult.notificationRef, isPdfAvailable = isPdfAvailable)
  }.merge

  private def postNotificationDps(subscriptionId: String, request: NotificationDpsRequest)(using
      HeaderCarrier
  ): EitherT[Future, PostNotificationResponse with Failure, NotificationDpsResponse] = {
    EitherT(notificationConnector.postNotification(subscriptionId, request).map {
      case HttpResponse(CREATED, body, _) =>
        Try(Json.parse(body).validate[NotificationDpsResponse].asEither).toEither.flatten.left
          .map(_ => MalformedResponse(DPS))
      case HttpResponse(BAD_REQUEST, body, _)           => Left(BadRequestFailure(DPS))
      case HttpResponse(INTERNAL_SERVER_ERROR, body, _) => Left(InternalServerFailure(DPS))
      case HttpResponse(SERVICE_UNAVAILABLE, body, _)   => Left(ServiceUnavailableFailure(DPS))
      case HttpResponse(status, body, _)                => Left(UnknownFailure(DPS, status))
    })
  }

  // TODO proper pdf generation
  private def generateAndUploadPdf(notificationReference: String)(using
      HeaderCarrier
  ): EitherT[Future, PostNotificationResponse with Failure, Boolean] = {
    EitherT.right(
      objectStoreClient
        .putObject(
          path = Path
            .Directory(s"/senior-accounting-officer/$notificationReference/")
            .file(s"${notificationReference}_SAO_notification.pdf"),
          content = "dummy file content",
          owner = "senior-accounting-officer"
        )
        .map { _ => true }
        .recover { case NonFatal(_) => false }
    )
  }
}

object NotificationService {
  enum DownstreamService {
    case DPS
  }
  sealed trait Failure
  enum PostNotificationResponse {
    case Success(notificationId: String, isPdfAvailable: Boolean)          extends PostNotificationResponse
    case MalformedResponse(downstreamService: DownstreamService)           extends PostNotificationResponse with Failure
    case BadRequestFailure(downstreamService: DownstreamService)           extends PostNotificationResponse with Failure
    case InternalServerFailure(downstreamService: DownstreamService)       extends PostNotificationResponse with Failure
    case ServiceUnavailableFailure(downstreamService: DownstreamService)   extends PostNotificationResponse with Failure
    case UnknownFailure(downstreamService: DownstreamService, status: Int) extends PostNotificationResponse with Failure
  }
}
