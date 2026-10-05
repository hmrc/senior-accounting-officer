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

package uk.gov.hmrc.senioraccountingofficer.services.submission

import com.codahale.metrics.MetricRegistry
import org.apache.pekko.actor.ActorSystem
import org.bson.types.ObjectId
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{inOrder as inInvocationOrder, *}
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach}
import org.scalatestplus.mockito.MockitoSugar
import play.api.Configuration
import uk.gov.hmrc.mongo.workitem.{ProcessingStatus, WorkItem}
import uk.gov.hmrc.senioraccountingofficer.models.submission.*
import uk.gov.hmrc.senioraccountingofficer.models.submission.SubmissionStep.*
import uk.gov.hmrc.senioraccountingofficer.repositories.submission.*

import scala.collection.immutable.ListMap
import scala.concurrent.{ExecutionContext, Future, Promise}

import java.time.{Clock, Instant, ZoneOffset}

class SubmissionRetentionSpec
    extends AnyFreeSpec
    with Matchers
    with ScalaFutures
    with IntegrationPatience
    with MockitoSugar
    with BeforeAndAfterAll
    with BeforeAndAfterEach {

  given ActorSystem = ActorSystem("submission-retention-test")

  given ExecutionContext = summon[ActorSystem].dispatcher

  override def afterAll(): Unit = {
    summon[ActorSystem].terminate().futureValue
    super.afterAll()
  }

  private val handoffs = Seq(
    RetrieveCustomer  -> SubmitDps,
    SubmitDps         -> InitialPdf,
    InitialPdf        -> SendEmail,
    InitialPdf        -> EnsurePdf,
    EnsurePdf         -> PackageDocumentum,
    PackageDocumentum -> NotifySdes
  )

  val mockSubmissionQueues: SubmissionQueues                   = mock[SubmissionQueues]
  val mockSubmissionStateRepository: SubmissionStateRepository = mock[SubmissionStateRepository]
  val mockSubmissionOperations: SubmissionOperations           = mock[SubmissionOperations]
  val now: Any                                                 = Instant.parse("2026-10-05T10:00:00Z")
  val command: Any            = SubmissionCommand.start(SubmissionTestData.data()).copy(operationFinished = true)
  val root: WorkItem[Nothing] = WorkItem(new ObjectId(), now, now, now, ProcessingStatus.Succeeded, 0, command)
  val id                      = command.data.orchestrationId
  val expiresAt: Any          = now.plusSeconds(604800)

  val repositories: Any = SubmissionStep.values.map(step => step -> mock[SubmissionWorkRepository]).toMap

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockSubmissionQueues, mockSubmissionStateRepository, mockSubmissionOperations)
    repositories.values.foreach(repository => reset(repository))
    when(mockSubmissionQueues.all).thenReturn(repositories)

    when(mockSubmissionQueues.apply(any())).thenAnswer(invocation =>
      repositories(invocation.getArgument[SubmissionStep](0))
    )

    repositories.values.foreach { repository =>
      when(repository.metrics).thenReturn(Future.successful(Map.empty[String, Int]))
      when(repository.hasActive(id)).thenReturn(Future.successful(false))
      when(repository.expire(id, expiresAt)).thenReturn(Future.unit)
    }

    when(repositories(RetrieveSubscription).retentionCandidates()).thenReturn(Future.successful(Seq(root)))
    when(mockSubmissionStateRepository.expire(id, expiresAt)).thenReturn(Future.successful(expiresAt))

  }

  val worker = new SubmissionWorker(
    mockSubmissionQueues,
    mockSubmissionStateRepository,
    mockSubmissionOperations,
    Configuration(
      "work-items.operation-timeout-seconds" -> 60,
      "work-items.lease-seconds"             -> 120,
      "work-items.retention-seconds"         -> 604800
    ),
    summon[ActorSystem],
    new MetricRegistry,
    Clock.fixed(now, ZoneOffset.UTC)
  )

  def assertNotExpired(): Unit = {
    verify(mockSubmissionStateRepository, never()).expire(any(), any())
    repositories.values.foreach(repository => verify(repository, never()).expire(any(), any()))
  }

  def assertExpired(): Unit = {
    verify(mockSubmissionStateRepository).expire(id, expiresAt)
    repositories.values.foreach(repository => verify(repository).expire(id, expiresAt))
  }

  "maintain" - {
    for (parent, successor) <- handoffs do {
      s"preserve work handed from $parent to $successor during a retention scan" in {
        // List the successor first to catch scans that miss a concurrent handoff.
        val order = successor +: SubmissionStep.values.toSeq.filterNot(_ == successor)
        when(mockSubmissionQueues.all).thenReturn(ListMap.from(order.map(step => step -> repositories(step))))

        val parentReadStarted = Promise[Unit]()
        val parentReadResult  = Promise[Boolean]()
        when(repositories(parent).hasActive(id)).thenAnswer { _ =>
          parentReadStarted.trySuccess(())
          parentReadResult.future
        }

        val sweep = worker.maintain()
        parentReadStarted.future.futureValue
        // The successor is persisted before the parent becomes terminal, as in processNext.
        when(repositories(successor).hasActive(id)).thenReturn(Future.successful(true))
        parentReadResult.success(false)
        sweep.futureValue

        assertNotExpired()
        val reads = inInvocationOrder(repositories(parent), repositories(successor))
        reads.verify(repositories(parent)).hasActive(id)
        reads.verify(repositories(successor)).hasActive(id)

        // A later sweep can expire the graph once the successor has completed.
        when(repositories(successor).hasActive(id)).thenReturn(Future.successful(false))
        worker.maintain().futureValue
        assertExpired()
      }
    }
  }
}
