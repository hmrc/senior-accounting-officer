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
import org.mockito.Mockito.*
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatestplus.mockito.MockitoSugar
import play.api.Configuration
import uk.gov.hmrc.mongo.workitem.{ProcessingStatus, WorkItem}
import uk.gov.hmrc.senioraccountingofficer.models.submission.*
import uk.gov.hmrc.senioraccountingofficer.models.submission.SubmissionStep.*
import uk.gov.hmrc.senioraccountingofficer.repositories.submission.*

import scala.collection.immutable.ListMap
import scala.concurrent.{ExecutionContext, Future, Promise}

import java.time.{Clock, Instant, ZoneOffset}
import java.util.concurrent.atomic.AtomicBoolean

class SubmissionRetentionSpec
    extends AnyFreeSpec
    with Matchers
    with ScalaFutures
    with IntegrationPatience
    with MockitoSugar
    with BeforeAndAfterAll {
  given ActorSystem      = ActorSystem("submission-retention-test")
  given ExecutionContext = summon[ActorSystem].dispatcher

  override def afterAll(): Unit = {
    summon[ActorSystem].terminate().futureValue
    super.afterAll()
  }

  // Each non-root handoff can overlap a sweep of a root that has already completed.
  private val handoffs = Seq(
    RetrieveCustomer  -> SubmitDps,
    SubmitDps         -> InitialPdf,
    InitialPdf        -> SendEmail,
    InitialPdf        -> EnsurePdf,
    EnsurePdf         -> PackageDocumentum,
    PackageDocumentum -> NotifySdes
  )

  for (parent, successor) <- handoffs do {
    s"retention cannot miss work handed from $parent to $successor during its scan" in {
      val queues     = mock[SubmissionQueues]
      val states     = mock[SubmissionStateRepository]
      val operations = mock[SubmissionOperations]
      val now        = Instant.parse("2026-10-05T10:00:00Z")
      val command    = SubmissionCommand.start(SubmissionTestData.data()).copy(operationFinished = true)
      val root       = WorkItem(new ObjectId(), now, now, now, ProcessingStatus.Succeeded, 0, command)
      val id         = command.data.orchestrationId
      val expiresAt  = now.plusSeconds(604800)

      // Deliberately list the successor first: scanning this map in parallel would read it
      // before insertion, then read the parent after completion and incorrectly expire both.
      val order        = successor +: SubmissionStep.values.toSeq.filterNot(_ == successor)
      val repositories = ListMap.from(order.map(step => step -> mock[SubmissionWorkRepository]))
      when(queues.all).thenReturn(repositories)
      when(queues.apply(any())).thenAnswer(invocation => repositories(invocation.getArgument[SubmissionStep](0)))
      repositories.values.foreach { repository =>
        when(repository.metrics).thenReturn(Future.successful(Map.empty[String, Int]))
        when(repository.hasActive(id)).thenReturn(Future.successful(false))
        when(repository.expire(id, expiresAt)).thenReturn(Future.unit)
      }
      when(repositories(RetrieveSubscription).retentionCandidates()).thenReturn(Future.successful(Seq(root)))
      when(states.expire(id, expiresAt)).thenReturn(Future.successful(expiresAt))

      val parentReadStarted = Promise[Unit]()
      val parentReadResult  = Promise[Boolean]()
      val successorActive   = new AtomicBoolean(false)
      when(repositories(parent).hasActive(id)).thenAnswer { _ =>
        parentReadStarted.trySuccess(())
        parentReadResult.future
      }
      when(repositories(successor).hasActive(id)).thenAnswer { _ =>
        Future.successful(successorActive.get())
      }

      val worker = new SubmissionWorker(
        queues,
        states,
        operations,
        Configuration(
          "work-items.operation-timeout-seconds" -> 60,
          "work-items.lease-seconds"             -> 120,
          "work-items.retention-seconds"         -> 604800
        ),
        summon[ActorSystem],
        new MetricRegistry,
        Clock.fixed(now, ZoneOffset.UTC)
      )
      val sweep = worker.maintain()
      parentReadStarted.future.futureValue
      // The successor is persisted before the parent becomes terminal, as in processNext.
      successorActive.set(true)
      parentReadResult.success(false)
      sweep.futureValue

      verify(states, never()).expire(any(), any())
      repositories.values.foreach(repository => verify(repository, never()).expire(any(), any()))
      val reads = org.mockito.Mockito.inOrder(repositories(parent), repositories(successor))
      reads.verify(repositories(parent)).hasActive(id)
      reads.verify(repositories(successor)).hasActive(id)

      // A later sweep can still expire the graph once the successor really has completed.
      successorActive.set(false)
      worker.maintain().futureValue
      verify(states).expire(id, expiresAt)
      repositories.values.foreach(repository => verify(repository).expire(id, expiresAt))
    }
  }
}
