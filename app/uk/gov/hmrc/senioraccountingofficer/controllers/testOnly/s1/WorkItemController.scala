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

package uk.gov.hmrc.senioraccountingofficer.controllers.testOnly.s1

import play.api.Logging
import play.api.mvc.{Action, AnyContent, ControllerComponents}
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.mongo.workitem.ProcessingStatus.{Failed, PermanentlyFailed}
import uk.gov.hmrc.mongo.workitem.WorkItem
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController
import uk.gov.hmrc.senioraccountingofficer.config.AppConfig
import uk.gov.hmrc.senioraccountingofficer.repositories.s1.{
  OrchestrationRepository,
  SubmissionOrchestration,
  SubmissionState,
  SubmissionStateRepository
}

import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import scala.concurrent.duration.*
import scala.concurrent.{ExecutionContext, Future}
import scala.jdk.DurationConverters.*
import scala.util.Random

class WorkItemController @Inject() (
    cc: ControllerComponents,
    orchestrationRepository: OrchestrationRepository,
    submissionStateRepository: SubmissionStateRepository
)(using
    ExecutionContext
) extends BackendController(cc)
    with Logging {

  def postNew: Action[AnyContent] = Action.async { implicit request =>
    val correlationId = UUID.randomUUID().toString
    val request       = Random().nextString(10)
    for {
      _ <- submissionStateRepository.set(
        SubmissionState(
          correlationId = correlationId,
          request = request,
          state = "dps",
          submissionId = None
        )
      )
      _ <- orchestrationRepository.pushNew(SubmissionOrchestration(correlationId, "main"))
    } yield NoContent
  }

  def work: Action[AnyContent] = Action.async { implicit request =>
    val now    = Instant.now()
    def getJob = orchestrationRepository.pullOutstanding(
      failedBefore = now.minus(1.day.toJava),
      availableBefore = now
    )

    getJob.flatMap {
      case Some(work) =>
        for {
          success <- processWork(work)
          if success
          _ <- orchestrationRepository.completeAndDelete(work.id)
        } yield Ok(s"${work.item.correlationId}")
      case _ => Future.successful(NoContent)
    }
  }

  def processWork(work: WorkItem[SubmissionOrchestration]): Future[Boolean] = work.item match {
    case SubmissionOrchestration(correlationId, "main") =>
      (for {
        optState <- submissionStateRepository.get(correlationId)
      } yield optState).flatMap {
        case Some(state) =>
          for {
            submissionId <- dps()
            updatedState01 = state.copy(submissionId = Some(submissionId), state = "genPdf")
            _             <- submissionStateRepository.set(updatedState01)
            pdfSuccessful <- generatePdf()

            updatedState02 = updatedState01.copy(
              submissionId = Some(submissionId),
              state = if pdfSuccessful then "sentPdf" else "genPdf",
              canShowConfirmation = true
            )
            _ <- submissionStateRepository.set(updatedState02)

            success10 <- orchestrationRepository.pushNew(SubmissionOrchestration(correlationId, "pdf"))
            updatedState03 = updatedState02.copy(forkedPdf = true)
            _ <- submissionStateRepository.set(updatedState03)

            success1 <- orchestrationRepository.pushNew(SubmissionOrchestration(correlationId, "audit"))
            updatedState10 = updatedState03.copy(forkedAudit = true)
            _ <- submissionStateRepository.set(updatedState10)

            success2 <- orchestrationRepository.pushNew(SubmissionOrchestration(correlationId, "email"))
            updatedState20 = updatedState10.copy(forkedEmail = true)
            _ <- submissionStateRepository.set(updatedState20)
          } yield true
        case _ =>
          orchestrationRepository.markAs(work.id, PermanentlyFailed)
      }
    case SubmissionOrchestration(correlationId, "pdf") =>
      submissionStateRepository.get(correlationId).flatMap {
        case Some(state) =>
          for {
            pdfSuccessful <- if state.state == "genPdf" then generatePdf() else Future.successful(true)
            updatedState = state.copy(
              state = if pdfSuccessful then "sentPdf" else "genPdf",
              canShowConfirmation = true
            )
            _ <- submissionStateRepository.set(updatedState)
            if pdfSuccessful
            _ <- packagePdf()
            _ <- sendPdf()
            _ <- orchestrationRepository.completeAndDelete(work.id)
          } yield true
        case _ => orchestrationRepository.markAs(work.id, PermanentlyFailed)
      }

    case SubmissionOrchestration(correlationId, "email") =>
      submissionStateRepository.get(correlationId).flatMap {
        case Some(state) =>
          for {
            _ <- email()
            _ <- orchestrationRepository.completeAndDelete(work.id)
          } yield true
        case _ => orchestrationRepository.markAs(work.id, PermanentlyFailed)
      }
    case SubmissionOrchestration(correlationId, "audit") =>
      submissionStateRepository.get(correlationId).flatMap {
        case Some(state) =>
          for {
            _ <- audit()
            _ <- orchestrationRepository.completeAndDelete(work.id)
          } yield true
        case _ => orchestrationRepository.markAs(work.id, PermanentlyFailed)
      }
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
