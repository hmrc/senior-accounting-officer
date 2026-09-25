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
import org.apache.pekko.stream.scaladsl.Source
import org.apache.pekko.util.ByteString
import play.api.http.Status.*
import play.api.libs.json.{Json, Reads}
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}
import uk.gov.hmrc.senioraccountingofficer.connectors.NotificationConnector
import uk.gov.hmrc.senioraccountingofficer.models.NotificationResult
import uk.gov.hmrc.senioraccountingofficer.models.NotificationResult.DownstreamService.*
import uk.gov.hmrc.senioraccountingofficer.models.NotificationResult.PostNotificationResponse.*
import uk.gov.hmrc.senioraccountingofficer.models.NotificationResult.{DownstreamService, PostNotificationResponse}
import uk.gov.hmrc.senioraccountingofficer.models.documentum.{DocumentumPackageContext, PreparedSdesSubmission}
import uk.gov.hmrc.senioraccountingofficer.models.dps.{
  GetSubscriptionDpsResponse,
  NotificationDpsRequest,
  NotificationDpsResponse
}
import uk.gov.hmrc.senioraccountingofficer.models.mongo.SubmissionStatus
import uk.gov.hmrc.senioraccountingofficer.models.workitems.{
  NotificationCheckpoint,
  NotificationStep,
  NotificationStepStatus
}
import uk.gov.hmrc.senioraccountingofficer.repositories.SubmissionStatusRepository
import uk.gov.hmrc.senioraccountingofficer.services.EmailService.EmailRejected
import uk.gov.hmrc.senioraccountingofficer.services.NotificationWorkflow.{WorkflowFailure, ukTimeZone}
import uk.gov.hmrc.senioraccountingofficer.services.documentum.DocumentumPackageService

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try
import scala.util.control.NonFatal

import java.time.*
import java.time.temporal.ChronoUnit.MILLIS
import javax.inject.{Inject, Singleton}

@Singleton
class NotificationWorkflow @Inject() (
    notificationConnector: NotificationConnector,
    documentumPackageService: DocumentumPackageService,
    pdfService: PdfService,
    emailService: EmailService,
    submissionStatusRepository: SubmissionStatusRepository,
    systemClock: Clock
)(using ExecutionContext) {
  private def ukClock: Clock                    = systemClock.withZone(ukTimeZone)
  private def localDateTimeNow(): LocalDateTime = LocalDateTime.now(ukClock)

  def run(checkpoint: NotificationCheckpoint)(using
      HeaderCarrier
  ): Future[Either[WorkflowFailure, NotificationCheckpoint]] = {
    val flow = for {
      withDpsResult         <- submitDpsStep(checkpoint)
      withGeneratePdfResult <- generatePdf(withDpsResult)
      withEmail             <- sendEmailStep(withGeneratePdfResult)
      pdfSource             <- ensurePdf(withGeneratePdfResult)
      withDocument          <- packageDocumentumStep(withEmail, pdfSource)
      completed             <- notifySdesStep(withDocument)
    } yield completed

    flow.value
  }

  private def submitDpsStep(
      checkpoint: NotificationCheckpoint
  )(using HeaderCarrier): EitherT[Future, WorkflowFailure, NotificationCheckpoint] =
    if isCompleted(checkpoint, NotificationStep.SubmitDps) then EitherT.rightT(checkpoint)
    else {
      // TODO if this fails we should just fail and let the user to manually retry instead of auto retry
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
          checkpoint.copy(
            notificationReference = Some(result.notificationRef),
            notificationDateTime = Some(localDateTimeNow())
          ),
          NotificationStep.SubmitDps
        )
      )
    }

  private def generatePdf(
      checkpoint: NotificationCheckpoint
  )(using HeaderCarrier): EitherT[Future, WorkflowFailure, NotificationCheckpoint] =
    if isCompleted(checkpoint, NotificationStep.GeneratePdf) then EitherT.rightT(checkpoint)
    else
      EitherT(
        pdfService
          .uploadNotificationPdf(
            NotificationDpsRequest.toPdfNotification(
              subscriptionId = checkpoint.subscriptionId,
              dpsSubscription = checkpoint.subscription,
              notificationRef = checkpoint.notificationReference.get,
              notificationDateTime = checkpoint.notificationDateTime.get,
              request = checkpoint.request
            )
          )
          .flatMap { result =>
            // TODO probably should move this to its own thing, but need to figure out how this will work
            //   with the permanent submit to DSP failures
            submissionStatusRepository
              .set(
                SubmissionStatus(
                  correlationId = checkpoint.id,
                  submissionId = checkpoint.notificationReference
                )(using systemClock)
              )
              .map(_ => result)
          }
          .map(result =>
            Right.apply[WorkflowFailure, NotificationCheckpoint](
              markSuccess(
                checkpoint,
                NotificationStep.GeneratePdf
              )
            )
          )
      )

  private def sendEmailStep(
      checkpoint: NotificationCheckpoint
  )(using HeaderCarrier): EitherT[Future, WorkflowFailure, NotificationCheckpoint] =
    if isCompleted(checkpoint, NotificationStep.SendEmail) then EitherT.rightT(checkpoint)
    else {
      val subscription = checkpoint.subscription
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

  private def ensurePdf(
      checkpoint: NotificationCheckpoint
  )(using HeaderCarrier): EitherT[Future, WorkflowFailure, Source[ByteString, ?]] = {
    val notification = NotificationDpsRequest.toPdfNotification(
      subscriptionId = checkpoint.subscriptionId,
      dpsSubscription = checkpoint.subscription,
      notificationRef = checkpoint.notificationReference.get,
      notificationDateTime = checkpoint.notificationDateTime.get,
      request = checkpoint.request
    )
    EitherT(
      pdfService
        .uploadNotificationPdf(notification)
        .flatMap(_ => pdfService.getNotificationPdf(notification))
        .collect { case Some(result) =>
          Right.apply[WorkflowFailure, Source[ByteString, ?]](result.content)
        }
    )
  }

  private def packageDocumentumStep(
      checkpoint: NotificationCheckpoint,
      pdfSource: Source[ByteString, ?]
  )(using HeaderCarrier): EitherT[Future, WorkflowFailure, NotificationCheckpoint] =
    if isCompleted(checkpoint, NotificationStep.PackageDocumentum) then EitherT.rightT(checkpoint)
    else {
      val subscription          = checkpoint.subscription
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
          pdfSource
        ),
        checkpoint,
        NotificationStep.PackageDocumentum
      ).map { prepared =>
        markSuccess(
          checkpoint.copy(preparedSdesSubmission = Some(prepared)),
          NotificationStep.PackageDocumentum
        )
      }
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
  val ukTimeZone: ZoneId = ZoneId.of("Europe/London")
}
