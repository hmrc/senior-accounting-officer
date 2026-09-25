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

import org.bson.types.ObjectId
import org.mockito.ArgumentMatchers.{any, argThat, eq as meq}
import org.mockito.Mockito.{never, verify, when}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatestplus.mockito.MockitoSugar
import play.api.libs.json.Json
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.mongo.workitem.{ProcessingStatus, WorkItem}
import uk.gov.hmrc.senioraccountingofficer.config.AppConfig
import uk.gov.hmrc.senioraccountingofficer.models.NotificationResult.DownstreamService.Subscription
import uk.gov.hmrc.senioraccountingofficer.models.NotificationResult.PostNotificationResponse.DownstreamServiceUnavailable
import uk.gov.hmrc.senioraccountingofficer.models.dps.{Contact, GetSubscriptionDpsResponse, NominatedCompany}
import uk.gov.hmrc.senioraccountingofficer.models.requests.*
import uk.gov.hmrc.senioraccountingofficer.models.workitems.{
  NotificationCheckpoint,
  NotificationRetry,
  NotificationStep
}
import uk.gov.hmrc.senioraccountingofficer.repositories.NotificationRetryRepository
import uk.gov.hmrc.senioraccountingofficer.services.NotificationWorkflow.WorkflowFailure
import uk.gov.hmrc.senioraccountingofficer.utils.TestDataGenerator.{generateCrn, generateUtr}

import scala.concurrent.{ExecutionContext, Future}

import java.time.{Instant, LocalDate, LocalDateTime}

import NotificationRetryServiceSpec.*

class NotificationRetryServiceSpec extends AnyFreeSpec with Matchers with MockitoSugar with ScalaFutures {

  given ExecutionContext = ExecutionContext.global

  "processNext updates the claimed work item when the workflow needs another retry" in {
    val appConfig  = mock[AppConfig]
    val repository = mock[NotificationRetryRepository]
    val workflow   = mock[NotificationWorkflow]
    val service    = new NotificationRetryService(appConfig, repository, workflow)
    val checkpoint = NotificationCheckpoint.start(
      "correlation-id",
      testSubscriptionId,
      testGetSubscriptionDpsResponse,
      notificationRequest
    )
    val originalRetry = NotificationRetry(NotificationStep.SubmitDps, checkpoint)
    val now           = Instant.now()
    val workItemId    = new ObjectId()
    val workItem      = WorkItem(
      workItemId,
      now,
      now,
      now,
      ProcessingStatus.InProgress,
      0,
      originalRetry
    )
    val advancedCheckpoint = checkpoint.copy(customerId = Some("customer-id"))
    val failure            = WorkflowFailure(
      DownstreamServiceUnavailable(Subscription),
      advancedCheckpoint,
      NotificationStep.SendEmail,
      retriable = true
    )

    when(appConfig.workItemsPollIntervalSeconds).thenReturn(1)
    when(repository.pullOutstanding(any(), any())).thenReturn(Future.successful(Some(workItem)))
    when(workflow.run(meq(checkpoint))(using any[HeaderCarrier]))
      .thenReturn(Future.successful(Left(failure)))
    when(repository.updateAndMarkFailed(meq(workItemId), any())).thenReturn(Future.successful(true))

    service.processNext().futureValue

    verify(repository).updateAndMarkFailed(
      meq(workItemId),
      argThat(retry => retry.failedStep == NotificationStep.SendEmail && retry.checkpoint == advancedCheckpoint)
    )
    verify(repository, never()).pushNew(any(), any(), any())
  }

  "NotificationRetry JSON format round trips the checkpoint payload" in {
    val retry = NotificationRetry(
      NotificationStep.SendEmail,
      NotificationCheckpoint.start(
        "correlation-id",
        testSubscriptionId,
        testGetSubscriptionDpsResponse,
        notificationRequest
      )
    )

    Json.toJson(retry).as[NotificationRetry] mustBe retry
  }
}

object NotificationRetryServiceSpec {
  val testSubscriptionId          = "123"
  val testSubmissionReference     = "CRT0123456789"
  val nominatedCompanyUtr         = generateUtr
  val nominatedCompanyCrn         = generateCrn
  val nominatedCompanyName        = "company name"
  val testSafeId                  = "safe id"
  val testCustomerId              = "customer id"
  val testContacts: List[Contact] = List(
    Contact("contact 1", "contact1@example.com", "en-GB", "active"),
    Contact("contact 2", "contact2@example.com", "en-GB", "active"),
    Contact("contact 3", "contact3@example.com", "en-GB", "active")
  )
  val created: LocalDateTime = LocalDateTime.now().minusDays(2)
  val updated: LocalDateTime = LocalDateTime.now()

  val notificationRequest: NotificationRequest = NotificationRequest(
    NotificationCompanies(
      List(
        NotificationCompany(
          None,
          Utr("1234567890"),
          CompanyName("Example Ltd"),
          LocalDate.parse("2024-12-31"),
          CompanyStatus.Active,
          CompanyType.LTD
        )
      )
    ),
    Saos(List(Sao(PersonName("Firstname Lastname"), None, None))),
    None
  )

  val testGetSubscriptionDpsResponse: GetSubscriptionDpsResponse = GetSubscriptionDpsResponse(
    etmpSafeId = testSafeId,
    nominatedCompany = NominatedCompany(
      crn = Some(nominatedCompanyCrn),
      name = nominatedCompanyName,
      utr = nominatedCompanyUtr
    ),
    contacts = testContacts,
    created = created,
    updated = updated
  )
}
