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

package uk.gov.hmrc.senioraccountingofficer.controllers.testOnly.s2

import play.api.Logging
import play.api.mvc.{Action, AnyContent, ControllerComponents}
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.mongo.workitem.ProcessingStatus.{Deferred, Failed, PermanentlyFailed}
import uk.gov.hmrc.mongo.workitem.WorkItem
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController
import uk.gov.hmrc.senioraccountingofficer.config.AppConfig
import uk.gov.hmrc.senioraccountingofficer.repositories.s2.*
import uk.gov.hmrc.senioraccountingofficer.repositories.s2.JobType.{Audit, DPS, Email, PDF, SDES, ZIP}
import uk.gov.hmrc.senioraccountingofficer.repositories.s2.SubmissionType.Notification

import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import scala.util.control.NonFatal
import scala.concurrent.duration.*
import scala.concurrent.{ExecutionContext, Future}
import scala.jdk.DurationConverters.*
import scala.language.postfixOps
import scala.util.Random

class WorkItemController @Inject() (
    cc: ControllerComponents,
    orchestrationRepository: OrchestrationRepository,
    requestRepository: RequestRepository,
    responseRepository: ResponseRepository
)(using
    ExecutionContext
) extends BackendController(cc)
    with Logging {

  def postNew: Action[AnyContent] = Action.async { implicit request =>
    val correlationId  = UUID.randomUUID().toString
    val subscriptionId = "1234567890"
    val companyName    = "Test Company xx1"
    // todo e.g. emails
    val request        = Random().nextString(10)
    for {
      _ <- requestRepository.set(
        WorkRequest(
          correlationId = correlationId,
          subscription = Subscription(subscriptionId, companyName),
          request = request
        )
      )
      _ <- orchestrationRepository.pushNew(SubmissionOrchestration(correlationId, Notification, DPS))
    } yield NoContent
  }

  def work: Action[AnyContent] = Action.async { implicit request =>
    val now = Instant.now()

    def getJob = orchestrationRepository.pullOutstanding(
      failedBefore = now.minus(1.day.toJava),
      availableBefore = now
    )

    getJob
      .flatMap {
        case Some(work) =>
          (for {
            success <- processWork(work)
            if success
            _ <- orchestrationRepository.completeAndDelete(work.id)
          } yield Ok(s"${work.item.correlationId}"))
            .recoverWith { case NonFatal(e) =>
              orchestrationRepository.markAs(work.id, Failed)
              Future.failed(e)
            }
        case _ => Future.successful(NoContent)
      }
  }

  def processWork(work: WorkItem[SubmissionOrchestration]): Future[Boolean] =
    requestRepository.get(work.item.correlationId).flatMap {
      case Some(request) =>
        (work.item match {
          case SubmissionOrchestration(correlationId, submissionType, DPS) =>
            for {
              submissionId <- dps()
              _            <- generatePdf()
              _            <- responseRepository.set(
                WorkResponse(
                  correlationId = correlationId,
                  subscriptionId = request.subscription.subscriptionId,
                  status = WorkStatus.Completed,
                  submissionId = Some(submissionId)
                )
              )
              _ <- orchestrationRepository.pushNew(SubmissionOrchestration(correlationId, submissionType, Audit))
              _ <- orchestrationRepository.pushNew(SubmissionOrchestration(correlationId, submissionType, Email))
              _ <- orchestrationRepository.pushNew(SubmissionOrchestration(correlationId, submissionType, PDF))
              _ <- orchestrationRepository.completeAndDelete(work.id)
            } yield true

          case SubmissionOrchestration(correlationId, submissionType, PDF) =>
            for {
              isGenerated <- isPdfGenerated()
              if isGenerated
              _ <- orchestrationRepository.pushNew(SubmissionOrchestration(correlationId, submissionType, ZIP))
              _ <- orchestrationRepository.completeAndDelete(work.id)
            } yield true

          case SubmissionOrchestration(correlationId, submissionType, ZIP) =>
            for {
              isZipped <- packagePdf()
              if isZipped
              _ <- orchestrationRepository.pushNew(SubmissionOrchestration(correlationId, submissionType, SDES))
              _ <- orchestrationRepository.completeAndDelete(work.id)
            } yield true
          case SubmissionOrchestration(correlationId, submissionType, Email) =>
            for {
              emailSent <- email()
              if emailSent
              _ <- orchestrationRepository.completeAndDelete(work.id)
            } yield true
          case SubmissionOrchestration(correlationId, submissionType, Audit) =>
            for {
              auditSent <- audit()
              if auditSent
              _ <- orchestrationRepository.completeAndDelete(work.id)
            } yield true
          case SubmissionOrchestration(correlationId, submissionType, SDES) =>
            for {
              pdfSent <- sendPdf()
              if pdfSent
              _ <- orchestrationRepository.markAs(
                work.id,
                Deferred,
                availableAt = Some(Instant.now().plus(1.minute.toJava))
              ) // TODO what to do on callback?
            } yield true
        }).recoverWith { case NonFatal(e) =>
          orchestrationRepository.markAs(work.id, Failed)
          Future.failed(e)
        }
      case _ => orchestrationRepository.markAs(work.id, PermanentlyFailed)
    }

  def dps(): Future[String] = Future {
    val submissionId = UUID.randomUUID().toString
    logger.error(s"dps=$submissionId")
    submissionId
  }

  def generatePdf(): Future[Boolean] = Future {
    val isSuccess = Random.nextBoolean()
    logger.error(s"generatePdf=$isSuccess")
    isSuccess
  }

  def isPdfGenerated(): Future[Boolean] = Future {
    val isSuccess = Random.nextBoolean()
    logger.error(s"isPdfGenerated=$isSuccess")
    isSuccess
  }

  def email(): Future[Boolean] = Future {
    logger.error("email")
    true
  }

  def audit(): Future[Boolean] = Future {
    logger.error("audit")
    true
  }

  def packagePdf(): Future[Boolean] = Future {
    logger.error("packagePdf")
    true
  }

  def sendPdf(): Future[Boolean] = Future {
    logger.error("sendPdf")
    true
  }

}
