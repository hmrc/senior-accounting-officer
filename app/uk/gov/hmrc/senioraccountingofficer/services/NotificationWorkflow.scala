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
import play.api.libs.json.{Json, Reads}
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}
import uk.gov.hmrc.senioraccountingofficer.connectors.{CrmmConnector, GetSubscriptionConnector, NotificationConnector}
import uk.gov.hmrc.senioraccountingofficer.models.NotificationResult
import uk.gov.hmrc.senioraccountingofficer.models.NotificationResult.DownstreamService
import uk.gov.hmrc.senioraccountingofficer.models.NotificationResult.DownstreamService.*
import uk.gov.hmrc.senioraccountingofficer.models.NotificationResult.PostNotificationResponse
import uk.gov.hmrc.senioraccountingofficer.models.NotificationResult.PostNotificationResponse.*
import uk.gov.hmrc.senioraccountingofficer.models.crmm.{RetrieveCustomerRequest, RetrieveCustomerResponse}
import uk.gov.hmrc.senioraccountingofficer.models.documentum.DocumentumPackageContext
import uk.gov.hmrc.senioraccountingofficer.models.dps.{
  GetSubscriptionDpsResponse,
  NotificationDpsRequest,
  NotificationDpsResponse
}
import uk.gov.hmrc.senioraccountingofficer.models.workitems.{
  NotificationCheckpoint,
  NotificationStep,
  NotificationStepStatus
}
import uk.gov.hmrc.senioraccountingofficer.services.NotificationWorkflow.WorkflowFailure
import uk.gov.hmrc.senioraccountingofficer.services.EmailService.EmailRejected
import uk.gov.hmrc.senioraccountingofficer.services.documentum.DocumentumPackageService

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try
import scala.util.control.NonFatal

import java.time.Instant
import java.time.temporal.ChronoUnit.MILLIS
import javax.inject.{Inject, Singleton}

@Singleton
class NotificationWorkflow @Inject() (
    notificationConnector: NotificationConnector,
    getSubscriptionConnector: GetSubscriptionConnector,
    crmmConnector: CrmmConnector,
    documentumPackageService: DocumentumPackageService,
    pdfService: PdfService,
    emailService: EmailService
)(using ExecutionContext) {

  def run(checkpoint: NotificationCheckpoint)(using
      HeaderCarrier
  ): Future[Either[WorkflowFailure, NotificationCheckpoint]] = {
    val flow = for {
      withSubscription <- getSubscriptionStep(checkpoint)
      withCustomerId   <- retrieveCrmmCustomerIdStep(withSubscription)
      withDpsResult    <- submitDpsStep(withCustomerId)
      withEmail        <- sendEmailStep(withDpsResult)
      withDocument     <- packageDocumentumStep(withEmail)
      completed        <- notifySdesStep(withDocument)
    } yield completed

    flow.value
  }

  private def getSubscriptionStep(
      checkpoint: NotificationCheckpoint
  )(using HeaderCarrier): EitherT[Future, WorkflowFailure, NotificationCheckpoint] =
    if isCompleted(checkpoint, NotificationStep.GetSubscription) then EitherT.rightT(checkpoint)
    else
      attemptDownstream(
        getSubscription(checkpoint.subscriptionId),
        checkpoint,
        NotificationStep.GetSubscription,
        Subscription
      ).map(subscription =>
        markSuccess(checkpoint.copy(subscription = Some(subscription)), NotificationStep.GetSubscription)
      )

  private def retrieveCrmmCustomerIdStep(
      checkpoint: NotificationCheckpoint
  )(using HeaderCarrier): EitherT[Future, WorkflowFailure, NotificationCheckpoint] =
    if isCompleted(checkpoint, NotificationStep.RetrieveCrmmCustomer) then EitherT.rightT(checkpoint)
    else {
      val subscription = checkpoint.subscription.get
      attemptDownstream(
        retrieveCustomerId(subscription.nominatedCompany.crn, subscription.nominatedCompany.utr),
        checkpoint,
        NotificationStep.RetrieveCrmmCustomer,
        CRMM
      ).map(customerId => markSuccess(checkpoint.copy(customerId = customerId), NotificationStep.RetrieveCrmmCustomer))
    }

  private def submitDpsStep(
      checkpoint: NotificationCheckpoint
  )(using HeaderCarrier): EitherT[Future, WorkflowFailure, NotificationCheckpoint] =
    if isCompleted(checkpoint, NotificationStep.SubmitDps) then EitherT.rightT(checkpoint)
    else
      attemptDownstream(
        submitNotification(
          checkpoint.subscriptionId,
          checkpoint.request.toNotificationDpsRequest(checkpoint.customerId)
        ),
        checkpoint,
        NotificationStep.SubmitDps,
        DPS
      ).map(result =>
        markSuccess(
          checkpoint.copy(notificationReference = Some(result.notificationRef)),
          NotificationStep.SubmitDps
        )
      )

  private def sendEmailStep(
      checkpoint: NotificationCheckpoint
  )(using HeaderCarrier): EitherT[Future, WorkflowFailure, NotificationCheckpoint] =
    if isCompleted(checkpoint, NotificationStep.SendEmail) then EitherT.rightT(checkpoint)
    else {
      val subscription = checkpoint.subscription.get
      attemptSideEffect(
        emailService.sendNotificationEmail(
          subscription.contacts,
          subscription.nominatedCompany.name,
          checkpoint.notificationReference.get
        ),
        checkpoint,
        NotificationStep.SendEmail,
        {
          case rejection: EmailRejected => rejection.retriable
          case _                        => true
        }
      ).map(_ => markSuccess(checkpoint, NotificationStep.SendEmail))
    }

  private def packageDocumentumStep(
      checkpoint: NotificationCheckpoint
  )(using HeaderCarrier): EitherT[Future, WorkflowFailure, NotificationCheckpoint] =
    if isCompleted(checkpoint, NotificationStep.PackageDocumentum) then EitherT.rightT(checkpoint)
    else {
      val subscription          = checkpoint.subscription.get
      val notificationReference = checkpoint.notificationReference.get
      val context               = DocumentumPackageContext.notification(
        notificationReference,
        checkpoint.customerId,
        checkpoint.subscriptionId,
        subscription.nominatedCompany,
        checkpoint.request
      )

      attemptSideEffect(
        documentumPackageService.preparePackage(
          context,
          pdfService.generateNotificationPdf(
            NotificationDpsRequest.toPdfNotification(
              notificationReference,
              checkpoint.request,
              subscription.nominatedCompany.name
            )
          )
        ),
        checkpoint,
        NotificationStep.PackageDocumentum
      ).map(prepared =>
        markSuccess(
          checkpoint.copy(preparedSdesSubmission = Some(prepared)),
          NotificationStep.PackageDocumentum
        )
      )
    }

  private def notifySdesStep(
      checkpoint: NotificationCheckpoint
  )(using HeaderCarrier): EitherT[Future, WorkflowFailure, NotificationCheckpoint] =
    if isCompleted(checkpoint, NotificationStep.NotifySdes) then EitherT.rightT(checkpoint)
    else
      attemptSideEffect(
        documentumPackageService.notifySdes(checkpoint.preparedSdesSubmission.get),
        checkpoint,
        NotificationStep.NotifySdes
      ).map(_ => markSuccess(checkpoint, NotificationStep.NotifySdes))

  private def attemptDownstream[A](
      operation: => Future[Either[PostNotificationResponse & NotificationResult.Failure, A]],
      checkpoint: NotificationCheckpoint,
      step: NotificationStep,
      service: DownstreamService
  ): EitherT[Future, WorkflowFailure, A] =
    EitherT(
      operation
        .map(_.left.map(toFailure(checkpoint, step, _)))
        .recover { case NonFatal(error) =>
          Left(toFailure(checkpoint, step, DownstreamServiceUnavailable(service), Some(error.getMessage)))
        }
    )

  private def attemptSideEffect[A](
      operation: => Future[A],
      checkpoint: NotificationCheckpoint,
      step: NotificationStep,
      isRetriable: Throwable => Boolean = _ => true
  ): EitherT[Future, WorkflowFailure, A] =
    EitherT(
      operation
        .map(Right(_))
        .recover { case NonFatal(error) =>
          val retriable = isRetriable(error)
          Left(
            WorkflowFailure(
              response = postDpsResponse(checkpoint),
              checkpoint = markFailure(checkpoint, step, error.getMessage, permanently = !retriable),
              step = step,
              retriable = retriable
            )
          )
        }
    )

  private def toFailure(
      checkpoint: NotificationCheckpoint,
      step: NotificationStep,
      response: PostNotificationResponse,
      errorOverride: Option[String] = None
  ): WorkflowFailure = {
    val retriable = isRetriable(response)
    WorkflowFailure(
      response = response,
      checkpoint = markFailure(checkpoint, step, errorOverride.getOrElse(describe(response)), permanently = !retriable),
      step = step,
      retriable = retriable
    )
  }

  private def markSuccess(checkpoint: NotificationCheckpoint, step: NotificationStep): NotificationCheckpoint = {
    val current = checkpoint.steps.stateFor(step)
    checkpoint.copy(
      steps = checkpoint.steps.update(
        step,
        current.copy(
          status = NotificationStepStatus.Completed,
          attempts = current.attempts + 1,
          lastError = None,
          updatedAt = Instant.now().truncatedTo(MILLIS)
        )
      )
    )
  }

  private def markFailure(
      checkpoint: NotificationCheckpoint,
      step: NotificationStep,
      message: String,
      permanently: Boolean
  ): NotificationCheckpoint = {
    val current = checkpoint.steps.stateFor(step)
    checkpoint.copy(
      steps = checkpoint.steps.update(
        step,
        current.copy(
          status = if permanently then NotificationStepStatus.PermanentlyFailed else NotificationStepStatus.Failed,
          attempts = current.attempts + 1,
          lastError = Some(message),
          updatedAt = Instant.now().truncatedTo(MILLIS)
        )
      )
    )
  }

  private def isCompleted(checkpoint: NotificationCheckpoint, step: NotificationStep): Boolean =
    checkpoint.steps.stateFor(step).status == NotificationStepStatus.Completed

  private def postDpsResponse(checkpoint: NotificationCheckpoint): PostNotificationResponse =
    Success(checkpoint.notificationReference.get)

  private def getSubscription(
      subscriptionId: String
  )(using
      HeaderCarrier
  ): Future[Either[PostNotificationResponse & NotificationResult.Failure, GetSubscriptionDpsResponse]] =
    getSubscriptionConnector.getSubscription(subscriptionId).map {
      case HttpResponse(OK, body, _)                 => parse[GetSubscriptionDpsResponse](body, Subscription)
      case HttpResponse(BAD_REQUEST, _, _)           => Left(Misalignment(Subscription))
      case HttpResponse(UNAUTHORIZED, _, _)          => Left(DownstreamUnauthorised(Subscription))
      case HttpResponse(FORBIDDEN, _, _)             => Left(DownstreamForbidden(Subscription))
      case HttpResponse(INTERNAL_SERVER_ERROR, _, _) => Left(DownstreamServiceError(Subscription))
      case HttpResponse(SERVICE_UNAVAILABLE, _, _)   => Left(DownstreamServiceUnavailable(Subscription))
      case HttpResponse(status, _, _)                => Left(UnknownFailure(Subscription, status))
    }

  private def retrieveCustomerId(
      crn: Option[String],
      utr: String
  )(using HeaderCarrier): Future[Either[PostNotificationResponse & NotificationResult.Failure, Option[String]]] =
    crmmConnector.retrieveCustomer(RetrieveCustomerRequest(crn, Some(utr))).map {
      case HttpResponse(OK, body, _)                 => parse[RetrieveCustomerResponse](body, CRMM).map(_.customerId)
      case HttpResponse(BAD_REQUEST, _, _)           => Left(Misalignment(CRMM))
      case HttpResponse(UNAUTHORIZED, _, _)          => Left(DownstreamUnauthorised(CRMM))
      case HttpResponse(FORBIDDEN, _, _)             => Left(DownstreamForbidden(CRMM))
      case HttpResponse(INTERNAL_SERVER_ERROR, _, _) => Left(DownstreamServiceError(CRMM))
      case HttpResponse(SERVICE_UNAVAILABLE, _, _)   => Left(DownstreamServiceUnavailable(CRMM))
      case HttpResponse(status, _, _)                => Left(UnknownFailure(CRMM, status))
    }

  private def submitNotification(
      subscriptionId: String,
      request: NotificationDpsRequest
  )(using
      HeaderCarrier
  ): Future[Either[PostNotificationResponse & NotificationResult.Failure, NotificationDpsResponse]] =
    notificationConnector.postNotification(subscriptionId, request).map {
      case HttpResponse(CREATED, body, _)            => parse[NotificationDpsResponse](body, DPS)
      case HttpResponse(BAD_REQUEST, _, _)           => Left(Misalignment(DPS))
      case HttpResponse(UNAUTHORIZED, _, _)          => Left(DownstreamUnauthorised(DPS))
      case HttpResponse(FORBIDDEN, _, _)             => Left(DownstreamForbidden(DPS))
      case HttpResponse(INTERNAL_SERVER_ERROR, _, _) => Left(DownstreamServiceError(DPS))
      case HttpResponse(SERVICE_UNAVAILABLE, _, _)   => Left(DownstreamServiceUnavailable(DPS))
      case HttpResponse(status, _, _)                => Left(UnknownFailure(DPS, status))
    }

  private def parse[A: Reads](
      body: String,
      service: DownstreamService
  ): Either[PostNotificationResponse & NotificationResult.Failure, A] =
    Try(Json.parse(body).as[A]).toEither.left.map(_ => MalformedResponse(service))

  private def isRetriable(response: PostNotificationResponse): Boolean = response match {
    case DownstreamServiceError(_)       => true
    case DownstreamServiceUnavailable(_) => true
    case UnknownFailure(_, status)       => status >= 500
    case _                               => false
  }

  private def describe(response: PostNotificationResponse): String = response match {
    case Success(reference)                    => s"Unexpected success $reference"
    case MalformedResponse(service)            => s"Malformed response from $service"
    case Misalignment(service)                 => s"Misalignment from $service"
    case DownstreamUnauthorised(service)       => s"Unauthorised from $service"
    case DownstreamForbidden(service)          => s"Forbidden from $service"
    case DownstreamServiceError(service)       => s"Service error from $service"
    case DownstreamServiceUnavailable(service) => s"Service unavailable from $service"
    case UnknownFailure(service, status)       => s"Unknown failure from $service with status $status"
  }
}

object NotificationWorkflow {
  final case class WorkflowFailure(
      response: PostNotificationResponse,
      checkpoint: NotificationCheckpoint,
      step: NotificationStep,
      retriable: Boolean
  )
}
