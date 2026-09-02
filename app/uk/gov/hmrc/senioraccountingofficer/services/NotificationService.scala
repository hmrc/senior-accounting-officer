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
import uk.gov.hmrc.senioraccountingofficer.models.workitems.*
import uk.gov.hmrc.senioraccountingofficer.repositories.SubmissionStateRepository
import uk.gov.hmrc.senioraccountingofficer.services.NotificationService.*
import uk.gov.hmrc.senioraccountingofficer.services.NotificationService.DownstreamService.*
import uk.gov.hmrc.senioraccountingofficer.services.NotificationService.PostNotificationResponse.*
import uk.gov.hmrc.senioraccountingofficer.services.documentum.DocumentumPackageService

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

import java.time.Instant
import javax.inject.Inject

class NotificationService @Inject() (
    notificationConnector: NotificationConnector,
    getSubscriptionConnector: GetSubscriptionConnector,
    crmmConnector: CrmmConnector,
    documentumPackageService: DocumentumPackageService,
    pdfService: PdfService,
    emailService: EmailService,
    submissionStateRepository: SubmissionStateRepository,
    submissionWorkItemScheduler: SubmissionWorkItemScheduler
)(using ExecutionContext)
    extends Logging {

  def postNotification(subscriptionId: String, request: NotificationRequest)(using
      HeaderCarrier
  ): Future[PostNotificationResponse] = {
    val initialState = SubmissionWorkItemState.notification(subscriptionId, request)

    submissionStateRepository.set(initialState).flatMap(_ => run(initialState, ExecutionMode.Initial))
  }

  def processWorkItem(state: SubmissionWorkItemState)(using HeaderCarrier): Future[Boolean] =
    runInternal(state, ExecutionMode.Resume).map(_.isRight)

  private def run(
      state: SubmissionWorkItemState,
      mode: ExecutionMode
  )(using HeaderCarrier): Future[PostNotificationResponse] =
    runInternal(state, mode).map {
      case Right(successState) => Success(successState.submissionReference.get)
      case Left(failure)       => failure.response
    }

  private def runInternal(
      state: SubmissionWorkItemState,
      mode: ExecutionMode
  )(using HeaderCarrier): Future[Either[FlowFailure, SubmissionWorkItemState]] = {
    val flow = for {
      withSubscription <- getSubscriptionStep(state)
      withCustomerId   <- retrieveCrmmCustomerIdStep(withSubscription)
      withDpsResult    <- postNotificationDpsStep(withCustomerId)
      withEmail        <- sendNotificationEmailStep(withDpsResult)
      withDocument     <- prepareDocumentumStep(withEmail)
      withSdes         <- notifySdesStep(withDocument)
    } yield withSdes

    flow.value.flatMap {
      case Right(successState) =>
        mode match {
          case ExecutionMode.Initial => submissionStateRepository.clear(successState.jobId).map(_ => Right(successState))
          case ExecutionMode.Resume  => submissionStateRepository.set(successState).map(_ => Right(successState))
        }
      case Left(failure)       =>
        for {
          _ <- submissionStateRepository.set(failure.state)
          _ <- mode match {
                 case ExecutionMode.Initial if failure.shouldEnqueue =>
                   submissionWorkItemScheduler.enqueue(failure.state.jobId, failure.step)
                 case _                                              => Future.unit
               }
        } yield Left(failure)
    }
  }

  private def getSubscriptionStep(
      state: SubmissionWorkItemState
  )(using HeaderCarrier): EitherT[Future, FlowFailure, SubmissionWorkItemState] =
    if isCompleted(state, SubmissionStep.GetSubscription) then EitherT.rightT(state)
    else
      EitherT(
        getSubscriptionDps(state.subscriptionId).map {
          case Right(subscription) =>
            Right(markSuccess(state.copy(subscription = Some(subscription)), SubmissionStep.GetSubscription))
          case Left(error)         =>
            Left(toFailure(state, SubmissionStep.GetSubscription, error))
        }
      )

  private def retrieveCrmmCustomerIdStep(
      state: SubmissionWorkItemState
  )(using HeaderCarrier): EitherT[Future, FlowFailure, SubmissionWorkItemState] =
    if isCompleted(state, SubmissionStep.RetrieveCrmmCustomer) then EitherT.rightT(state)
    else {
      val subscription = state.subscription.get
      EitherT(
        retrieveCrmmCustomerId(subscription.nominatedCompany.crn, subscription.nominatedCompany.utr).map {
          case Right(customerId) =>
            Right(markSuccess(state.copy(customerId = customerId), SubmissionStep.RetrieveCrmmCustomer))
          case Left(error)       =>
            Left(toFailure(state, SubmissionStep.RetrieveCrmmCustomer, error))
        }
      )
    }

  private def postNotificationDpsStep(
      state: SubmissionWorkItemState
  )(using HeaderCarrier): EitherT[Future, FlowFailure, SubmissionWorkItemState] =
    if isCompleted(state, SubmissionStep.SubmitDps) then EitherT.rightT(state)
    else {
      val requestWithCustomerId = state.notificationRequest.get.toNotificationDpsRequest(state.customerId)
      EitherT(
        postNotificationDps(state.subscriptionId, requestWithCustomerId).map {
          case Right(dpsResult) =>
            Right(markSuccess(state.copy(submissionReference = Some(dpsResult.notificationRef)), SubmissionStep.SubmitDps))
          case Left(error)      =>
            Left(toFailure(state, SubmissionStep.SubmitDps, error))
        }
      )
    }

  private def sendNotificationEmailStep(
      state: SubmissionWorkItemState
  )(using HeaderCarrier): EitherT[Future, FlowFailure, SubmissionWorkItemState] =
    if isCompleted(state, SubmissionStep.SendEmail) then EitherT.rightT(state)
    else {
      val subscription = state.subscription.get
      EitherT(
        emailService
          .sendNotificationEmailStrict(
            subscription.contacts,
            subscription.nominatedCompany.name,
            state.submissionReference.get
          )
          .map(_ => Right(markSuccess(state, SubmissionStep.SendEmail)))
          .recover { case error =>
            Left(
              FlowFailure(
                response = Success(state.submissionReference.get),
                state = markFailure(state, SubmissionStep.SendEmail, error.getMessage),
                step = SubmissionStep.SendEmail,
                shouldEnqueue = true
              )
            )
          }
      )
    }

  private def prepareDocumentumStep(
      state: SubmissionWorkItemState
  )(using HeaderCarrier): EitherT[Future, FlowFailure, SubmissionWorkItemState] =
    if isCompleted(state, SubmissionStep.PackageDocumentum) then EitherT.rightT(state)
    else {
      val subscription = state.subscription.get
      val context      = DocumentumPackageContext.notification(
        state.submissionReference.get,
        state.customerId,
        state.subscriptionId,
        subscription.nominatedCompany,
        state.notificationRequest.get
      )
      EitherT(
        documentumPackageService
          .preparePackage(
            context,
            pdfService.generateNotificationPdf(
              NotificationDpsRequest.toPdfNotification(
                state.submissionReference.get,
                state.notificationRequest.get,
                subscription.nominatedCompany.name
              )
            )
          )
          .map(prepared =>
            Right(markSuccess(state.copy(preparedSdesSubmission = Some(prepared)), SubmissionStep.PackageDocumentum))
          )
          .recover { case error =>
            Left(
              FlowFailure(
                response = Success(state.submissionReference.get),
                state = markFailure(state, SubmissionStep.PackageDocumentum, error.getMessage),
                step = SubmissionStep.PackageDocumentum,
                shouldEnqueue = true
              )
            )
          }
      )
    }

  private def notifySdesStep(
      state: SubmissionWorkItemState
  )(using HeaderCarrier): EitherT[Future, FlowFailure, SubmissionWorkItemState] =
    if isCompleted(state, SubmissionStep.NotifySdes) then EitherT.rightT(state)
    else
      EitherT(
        documentumPackageService
          .notifySdes(state.preparedSdesSubmission.get)
          .map(_ => Right(markSuccess(state, SubmissionStep.NotifySdes)))
          .recover { case error =>
            Left(
              FlowFailure(
                response = Success(state.submissionReference.get),
                state = markFailure(state, SubmissionStep.NotifySdes, error.getMessage),
                step = SubmissionStep.NotifySdes,
                shouldEnqueue = true
              )
            )
          }
      )

  private def toFailure(
      state: SubmissionWorkItemState,
      step: SubmissionStep,
      response: PostNotificationResponse
  ): FlowFailure = {
    val retriable = isRetriableFailure(response)
    FlowFailure(
      response = response,
      state =
        if retriable then markFailure(state, step, describeFailure(response))
        else markPermanentFailure(state, step, describeFailure(response)),
      step = step,
      shouldEnqueue = retriable
    )
  }

  private def isCompleted(state: SubmissionWorkItemState, step: SubmissionStep): Boolean =
    state.steps.stateFor(step).status == SubmissionStepStatus.Completed

  private def markSuccess(state: SubmissionWorkItemState, step: SubmissionStep): SubmissionWorkItemState = {
    val current = state.steps.stateFor(step)
    state.copy(
      steps = state.steps.update(
        step,
        current.copy(
          status = SubmissionStepStatus.Completed,
          attempts = current.attempts + 1,
          lastError = None,
          updatedAt = Instant.now()
        )
      )
    )
  }

  private def markFailure(state: SubmissionWorkItemState, step: SubmissionStep, errorMessage: String): SubmissionWorkItemState = {
    val current = state.steps.stateFor(step)
    state.copy(
      steps = state.steps.update(
        step,
        current.copy(
          status = SubmissionStepStatus.Failed,
          attempts = current.attempts + 1,
          lastError = Some(errorMessage),
          updatedAt = Instant.now()
        )
      )
    )
  }

  private def markPermanentFailure(
      state: SubmissionWorkItemState,
      step: SubmissionStep,
      errorMessage: String
  ): SubmissionWorkItemState = {
    val current = state.steps.stateFor(step)
    state.copy(
      steps = state.steps.update(
        step,
        current.copy(
          status = SubmissionStepStatus.PermanentlyFailed,
          attempts = current.attempts + 1,
          lastError = Some(errorMessage),
          updatedAt = Instant.now()
        )
      ),
      status = SubmissionStateStatus.PermanentlyFailed
    )
  }

  private def getSubscriptionDps(
      subscriptionId: String
  )(using HeaderCarrier): Future[Either[PostNotificationResponse with Failure, GetSubscriptionDpsResponse]] =
    getSubscriptionConnector
      .getSubscription(subscriptionId)
      .map {
        case HttpResponse(OK, body, _) =>
          Try(Json.parse(body).as[GetSubscriptionDpsResponse]).toEither.left
            .map(_ => MalformedResponse(Subscription))
        case HttpResponse(BAD_REQUEST, _, _)           => Left(Misalignment(Subscription))
        case HttpResponse(UNAUTHORIZED, _, _)          => Left(DownstreamUnauthorised(Subscription))
        case HttpResponse(FORBIDDEN, _, _)             => Left(DownstreamForbidden(Subscription))
        case HttpResponse(INTERNAL_SERVER_ERROR, _, _) => Left(DownstreamServiceError(Subscription))
        case HttpResponse(SERVICE_UNAVAILABLE, _, _)   => Left(DownstreamServiceUnavailable(Subscription))
        case HttpResponse(status, _, _)                => Left(UnknownFailure(Subscription, status))
      }

  private def retrieveCrmmCustomerId(
      crn: Option[String],
      utr: String
  )(using HeaderCarrier): Future[Either[PostNotificationResponse with Failure, Option[String]]] = {
    val request = RetrieveCustomerRequest(crn, Some(utr))
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
  }

  private def parseCrmmResponse(body: String): Either[PostNotificationResponse with Failure, Option[String]] =
    Try(Json.parse(body).as[RetrieveCustomerResponse]).toEither match {
      case Left(_)         => Left(MalformedResponse(CRMM))
      case Right(customer) =>
        customer match {
          case RetrieveCustomerResponse(None)             => Right(None)
          case RetrieveCustomerResponse(Some(customerId)) => Right(Some(customerId))
          case _                                          => Left(MalformedResponse(CRMM))
        }
    }

  private def postNotificationDps(subscriptionId: String, request: NotificationDpsRequest)(using
      HeaderCarrier
  ): Future[Either[PostNotificationResponse with Failure, NotificationDpsResponse]] =
    notificationConnector.postNotification(subscriptionId, request).map {
      case HttpResponse(CREATED, body, _) =>
        Try(Json.parse(body).as[NotificationDpsResponse]).toEither.left.map(_ => MalformedResponse(DPS))
      case HttpResponse(BAD_REQUEST, _, _)           => Left(Misalignment(DPS))
      case HttpResponse(UNAUTHORIZED, _, _)          => Left(DownstreamUnauthorised(DPS))
      case HttpResponse(FORBIDDEN, _, _)             => Left(DownstreamForbidden(DPS))
      case HttpResponse(INTERNAL_SERVER_ERROR, _, _) => Left(DownstreamServiceError(DPS))
      case HttpResponse(SERVICE_UNAVAILABLE, _, _)   => Left(DownstreamServiceUnavailable(DPS))
      case HttpResponse(status, _, _)                => Left(UnknownFailure(DPS, status))
    }
}

object NotificationService {
  enum DownstreamService {
    case Subscription, DPS, CRMM
  }

  sealed trait Failure

  enum PostNotificationResponse {
    case Success(notificationReference: String)                       extends PostNotificationResponse
    case MalformedResponse(downstreamService: DownstreamService)      extends PostNotificationResponse with Failure
    case Misalignment(downstreamService: DownstreamService)           extends PostNotificationResponse with Failure
    case DownstreamUnauthorised(downstreamService: DownstreamService) extends PostNotificationResponse with Failure
    case DownstreamForbidden(downstreamService: DownstreamService)    extends PostNotificationResponse with Failure
    case DownstreamServiceError(downstreamService: DownstreamService) extends PostNotificationResponse with Failure
    case DownstreamServiceUnavailable(downstreamService: DownstreamService)
        extends PostNotificationResponse
        with Failure
    case UnknownFailure(downstreamService: DownstreamService, status: Int) extends PostNotificationResponse with Failure
  }

  private enum ExecutionMode {
    case Initial, Resume
  }

  private final case class FlowFailure(
      response: PostNotificationResponse,
      state: SubmissionWorkItemState,
      step: SubmissionStep,
      shouldEnqueue: Boolean
  )

  private def describeFailure(response: PostNotificationResponse): String = response match {
    case Success(reference)                     => s"Unexpected success $reference"
    case MalformedResponse(service)            => s"Malformed response from $service"
    case Misalignment(service)                 => s"Misalignment from $service"
    case DownstreamUnauthorised(service)       => s"Unauthorised from $service"
    case DownstreamForbidden(service)          => s"Forbidden from $service"
    case DownstreamServiceError(service)       => s"Service error from $service"
    case DownstreamServiceUnavailable(service) => s"Service unavailable from $service"
    case UnknownFailure(service, status)       => s"Unknown failure from $service with status $status"
  }

  private def isRetriableFailure(response: PostNotificationResponse): Boolean = response match {
    case DownstreamServiceError(_)       => true
    case DownstreamServiceUnavailable(_) => true
    case UnknownFailure(_, status)       => status >= 500
    case _                               => false
  }
}
