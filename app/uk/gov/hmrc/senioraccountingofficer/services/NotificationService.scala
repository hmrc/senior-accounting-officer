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

import play.api.http.Status.*
import play.api.libs.json.*
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}
import uk.gov.hmrc.senioraccountingofficer.connectors.NotificationConnector
import uk.gov.hmrc.senioraccountingofficer.models.dps.{NotificationDpsRequest, NotificationDpsResponse}
import uk.gov.hmrc.senioraccountingofficer.services.NotificationService.DownstreamService.DPS

import scala.concurrent.{ExecutionContext, Future}

import javax.inject.Inject

import NotificationService.*
import NotificationService.PostNotificationResponse.*

class NotificationService @Inject() (
    notificationConnector: NotificationConnector
)(using ExecutionContext) {

  def postNotification(id: String, request: NotificationDpsRequest)(using
      HeaderCarrier
  ): Future[PostNotificationResponse] = {
    notificationConnector.postNotification(id, request).map {
      case HttpResponse(CREATED, body, _) =>
        val res = Json.parse(body).validate[NotificationDpsResponse].asEither
        res.left
          .map(errs => MalformedResponse(DPS))
          .map(response => Success(response.notificationRef))
          .merge
      case HttpResponse(BAD_REQUEST, body, _)           => BadRequestFailure(DPS, body)
      case HttpResponse(INTERNAL_SERVER_ERROR, body, _) => InternalServerFailure(DPS, body)
      case HttpResponse(SERVICE_UNAVAILABLE, body, _)   => ServiceUnavailableFailure(DPS)
      case HttpResponse(status, body, _)                => UnknownFailure(DPS, status, body)
    }
  }
}

object NotificationService {
  enum DownstreamService {
    case DPS
  }
  enum PostNotificationResponse {
    case Success(notificationId: String)                                           extends PostNotificationResponse
    case MalformedResponse(downstreamService: DownstreamService)                   extends PostNotificationResponse
    case BadRequestFailure(downstreamService: DownstreamService, body: String)     extends PostNotificationResponse
    case InternalServerFailure(downstreamService: DownstreamService, body: String) extends PostNotificationResponse
    case ServiceUnavailableFailure(downstreamService: DownstreamService)           extends PostNotificationResponse
    case UnknownFailure(downstreamService: DownstreamService, status: Int, body: String)
        extends PostNotificationResponse
  }
}
