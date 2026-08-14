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
import play.api.Logging
import play.api.http.Status.*
import play.api.libs.json.*
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}
import uk.gov.hmrc.senioraccountingofficer.connectors.*
import uk.gov.hmrc.senioraccountingofficer.models.crmm.{RetrieveCustomerRequest, RetrieveCustomerResponse}
import uk.gov.hmrc.senioraccountingofficer.models.documentum.DocumentumPackageContext
import uk.gov.hmrc.senioraccountingofficer.models.dps.{
  GetSubscriptionDpsResponse,
  NotificationDpsRequest,
  NotificationDpsResponse
}
import uk.gov.hmrc.senioraccountingofficer.models.requests.NotificationRequest
import uk.gov.hmrc.senioraccountingofficer.services.NotificationService.*
import uk.gov.hmrc.senioraccountingofficer.services.NotificationService.DownstreamService.*
import uk.gov.hmrc.senioraccountingofficer.services.NotificationService.PostNotificationResponse.*
import uk.gov.hmrc.senioraccountingofficer.services.documentum.DocumentumPackageService

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

import javax.inject.Inject

class NotificationService @Inject() (
    notificationConnector: NotificationConnector,
    getSubscriptionConnector: GetSubscriptionConnector,
    crmmConnector: CrmmConnector,
    documentumPackageService: DocumentumPackageService,
    pdfService: PdfService,
    emailService: EmailService
)(using ExecutionContext)
    extends Logging {

  def postNotification(subscriptionId: String, request: NotificationRequest)(using
      HeaderCarrier
  ): Future[PostNotificationResponse] = {
    for {
      dpsSubscription <- getSubscriptionDps(subscriptionId)
      customerId <- retrieveCrmmCustomerId(dpsSubscription.nominatedCompany.crn, dpsSubscription.nominatedCompany.utr)
      requestWithCustomerId = request.toNotificationDpsRequest(customerId)
      dpsResult <- postNotificationDps(subscriptionId, requestWithCustomerId)
      _ = emailService.sendNotificationEmail(
        dpsSubscription.contacts,
        dpsSubscription.nominatedCompany.name,
        dpsResult.notificationRef
      )
      documentPackage <- packageAndSubmitDocumentumFile(
        subscriptionId,
        dpsSubscription.nominatedCompany.name,
        dpsResult.notificationRef,
        request
      )
    } yield Success(
      notificationReference = dpsResult.notificationRef,
      isPdfAvailable = documentPackage.packageAvailable
    )
  }.merge

  private def getSubscriptionDps(
      subscriptionId: String
  )(using HeaderCarrier): EitherT[Future, PostNotificationResponse with Failure, GetSubscriptionDpsResponse] = {
    EitherT(
      getSubscriptionConnector
        .getSubscription(subscriptionId)
        .map {
          case HttpResponse(OK, body, _) =>
            Try(Json.parse(body).as[GetSubscriptionDpsResponse]).toEither.left
              .map { _ =>
                MalformedResponse(Subscription)
              }
          case HttpResponse(BAD_REQUEST, _, _)           => Left(Misalignment(Subscription))
          case HttpResponse(UNAUTHORIZED, _, _)          => Left(DownstreamUnauthorised(Subscription))
          case HttpResponse(FORBIDDEN, _, _)             => Left(DownstreamForbidden(Subscription))
          case HttpResponse(INTERNAL_SERVER_ERROR, _, _) => Left(DownstreamServiceError(Subscription))
          case HttpResponse(SERVICE_UNAVAILABLE, _, _)   => Left(DownstreamServiceUnavailable(Subscription))
          case HttpResponse(status, _, _)                => Left(UnknownFailure(Subscription, status))
        }
    )
  }

  private def retrieveCrmmCustomerId(
      crn: Option[String],
      utr: String
  )(using HeaderCarrier): EitherT[Future, PostNotificationResponse with Failure, Option[String]] = {
    val request = RetrieveCustomerRequest(crn, Some(utr))
    EitherT(
      crmmConnector
        .retrieveCustomer(request)
        .map {
          case HttpResponse(OK, body, _)                 => parseCrmmResponse(body)
          case HttpResponse(BAD_REQUEST, _, _)           => Left(Misalignment(CRMM))
          case HttpResponse(UNAUTHORIZED, _, _)          => Left(DownstreamUnauthorised(CRMM))
          case HttpResponse(FORBIDDEN, _, _)             => Left(DownstreamForbidden(CRMM))
          case HttpResponse(INTERNAL_SERVER_ERROR, _, _) => Left(DownstreamServiceError(CRMM))
          case HttpResponse(SERVICE_UNAVAILABLE, _, _)   => Left(DownstreamServiceUnavailable(CRMM))
          case HttpResponse(status, _, _)                => Left(UnknownFailure(CRMM, status))
        }
    )
  }

  private def parseCrmmResponse(body: String): Either[PostNotificationResponse with Failure, Option[String]] = {
    Try(
      Json
        .parse(body)
        .as[RetrieveCustomerResponse]
    ).toEither match {
      case Left(_)         => Left(MalformedResponse(CRMM))
      case Right(customer) =>
        customer match {
          case RetrieveCustomerResponse(None, Some(_), false, "Failure") =>
            Right(None)
          case RetrieveCustomerResponse(Some(customerId), None, true, "Success") => Right(Some(customerId))
          case _                                                                 => Left(MalformedResponse(CRMM))
        }
    }
  }

  private def postNotificationDps(subscriptionId: String, request: NotificationDpsRequest)(using
      HeaderCarrier
  ): EitherT[Future, PostNotificationResponse with Failure, NotificationDpsResponse] = {
    EitherT(notificationConnector.postNotification(subscriptionId, request).map {
      case HttpResponse(CREATED, body, _) =>
        Try(Json.parse(body).as[NotificationDpsResponse]).toEither.left.map { _ =>
          MalformedResponse(DPS)
        }
      case HttpResponse(BAD_REQUEST, _, _)           => Left(Misalignment(DPS))
      case HttpResponse(UNAUTHORIZED, _, _)          => Left(DownstreamUnauthorised(DPS))
      case HttpResponse(FORBIDDEN, _, _)             => Left(DownstreamForbidden(DPS))
      case HttpResponse(INTERNAL_SERVER_ERROR, _, _) => Left(DownstreamServiceError(DPS))
      case HttpResponse(SERVICE_UNAVAILABLE, _, _)   => Left(DownstreamServiceUnavailable(DPS))
      case HttpResponse(status, _, _)                => Left(UnknownFailure(DPS, status))
    })
  }

  private def packageAndSubmitDocumentumFile(
      subscriptionId: String,
      companyName: String,
      notificationReference: String,
      request: NotificationRequest
  )(using
      HeaderCarrier
  ) =
    EitherT.right[PostNotificationResponse with Failure](
      documentumPackageService.packageAndSubmit(
        DocumentumPackageContext.notification(notificationReference, subscriptionId, request),
        pdfService.generateNotificationPdf(
          NotificationDpsRequest.toPdfNotification(notificationReference, request, companyName)
        )
      )
    )
}

object NotificationService {
  enum DownstreamService {
    case Subscription, DPS, CRMM
  }
  sealed trait Failure
  enum PostNotificationResponse {
    case Success(notificationReference: String, isPdfAvailable: Boolean) extends PostNotificationResponse
    case MalformedResponse(downstreamService: DownstreamService)         extends PostNotificationResponse with Failure
    case Misalignment(downstreamService: DownstreamService)              extends PostNotificationResponse with Failure
    case DownstreamUnauthorised(downstreamService: DownstreamService)    extends PostNotificationResponse with Failure
    case DownstreamForbidden(downstreamService: DownstreamService)       extends PostNotificationResponse with Failure
    case DownstreamServiceError(downstreamService: DownstreamService)    extends PostNotificationResponse with Failure
    case DownstreamServiceUnavailable(downstreamService: DownstreamService)
        extends PostNotificationResponse
        with Failure
    case UnknownFailure(downstreamService: DownstreamService, status: Int) extends PostNotificationResponse with Failure
  }
}
