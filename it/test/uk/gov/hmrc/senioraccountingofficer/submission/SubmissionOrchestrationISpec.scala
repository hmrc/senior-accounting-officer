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

package uk.gov.hmrc.senioraccountingofficer.submission

import com.codahale.metrics.MetricRegistry
import org.apache.pekko.actor.ActorSystem
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.*
import org.mongodb.scala.{ObservableFuture, SingleObservableFuture}
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatestplus.mockito.MockitoSugar
import play.api.Configuration
import play.api.test.Helpers.*
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.workitem.ProcessingStatus
import uk.gov.hmrc.senioraccountingofficer.models.submission.*
import uk.gov.hmrc.senioraccountingofficer.models.submission.SubmissionStep.*
import uk.gov.hmrc.senioraccountingofficer.repositories.submission.*
import uk.gov.hmrc.senioraccountingofficer.services.submission.*
import uk.gov.hmrc.senioraccountingofficer.services.submission.SubmissionTestData.*
import java.time.{Clock, Instant, ZoneId, ZoneOffset}
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong
import scala.concurrent.{ExecutionContext, Future}

class SubmissionOrchestrationISpec extends AnyFreeSpec with Matchers with ScalaFutures with IntegrationPatience with MockitoSugar with BeforeAndAfterAll {
  given ActorSystem = ActorSystem("submission-repository-test")
  given ExecutionContext = summon[ActorSystem].dispatcher
  private val databases = scala.collection.mutable.ListBuffer.empty[MongoComponent]
  override def afterAll(): Unit = {
    databases.foreach { mongo => mongo.database.drop().toFuture().futureValue; mongo.client.close() }
    summon[ActorSystem].terminate().futureValue
    super.afterAll()
  }
  class TestClock extends Clock {
    private val millis = new AtomicLong(System.currentTimeMillis())
    def advance(seconds: Long): Unit = { millis.addAndGet(seconds * 1000); () }
    override def instant(): Instant = Instant.ofEpochMilli(millis.incrementAndGet())
    override def getZone: ZoneId = ZoneOffset.UTC
    override def withZone(zone: ZoneId): Clock = Clock.fixed(instant(), zone)
  }
  class Fixture {
    val clock = new TestClock
    val mongo = MongoComponent(s"mongodb://localhost:27017/sao-orchestration-test-${UUID.randomUUID()}")
    databases += mongo
    val config = Configuration("work-items.lease-seconds" -> 120, "work-items.operation-timeout-seconds" -> 1,
      "work-items.retry-base-seconds" -> 60, "work-items.retry-max-seconds" -> 900, "work-items.retention-seconds" -> 604800)
    val queues = new SubmissionQueues(mongo, config, clock)
    val states = new SubmissionStateRepository(mongo, clock)
    queues.all.values.foreach(_.initialised.futureValue)
    states.initialised.futureValue
    val ops = mock[SubmissionOperations]
    when(ops.successors(any())).thenCallRealMethod()
    when(ops.execute(any())(using any())).thenAnswer { invocation =>
      val c = invocation.getArgument[SubmissionCommand](0)
      val updated = c.step match {
        case SubmitDps => c.data.copy(reference = Some("REF123"), submittedAt = Some(time))
        case InitialPdf => c.data.copy(pdfAttempted = true, pdfStored = true)
        case _ => c.data
      }
      Future.successful(c.copy(data = updated, operationFinished = true))
    }
    val worker = new SubmissionWorker(queues, states, ops, config, summon[ActorSystem], new MetricRegistry, clock)
    val service = new SubmissionService(queues, states)
    def seed(c: SubmissionCommand): Unit = { states.initialize(c.data).futureValue; queues(c.step).enqueue(c).futureValue; () }
    def run(step: SubmissionStep): Unit = worker.processNext(step).futureValue
    def entries(step: SubmissionStep) = queues(step).collection.find().toFuture().futureValue
    def statusOf(d: SubmissionData) = status(service.status(d.subscriptionId, d.kind, d.idempotencyKey))
  }

  for kind <- SubmissionKind.values do {
    s"$kind accepts concurrent identical requests exactly once and isolates keys" in {
      val f = new Fixture
      val d = data(kind).copy(reference = None, submittedAt = None)
      val responses = Future.traverse(1 to 8)(_ => f.service.submit(d.copy(orchestrationId = UUID.randomUUID().toString))).futureValue
      responses.map(_.header.status).distinct mustBe Seq(202)
      f.entries(RetrieveSubscription).size mustBe 1
      f.statusOf(d) mustBe 204
      f.statusOf(d.copy(subscriptionId = "another-subscription")) mustBe 404
      f.service.submit(d.copy(subscriptionId = "another-subscription", orchestrationId = UUID.randomUUID().toString)).futureValue.header.status mustBe 202
      f.entries(RetrieveSubscription).size mustBe 2
    }
    s"$kind completes after a failed initial PDF attempt while delivery and emails remain queued" in {
      val f = new Fixture
      val d = data(kind).copy(reference = None, submittedAt = None)
      f.service.submit(d).futureValue
      Seq(RetrieveSubscription, RetrieveCustomer, SubmitDps).foreach(f.run)
      f.statusOf(d) mustBe 204
      // Match the claimed command, whose lease token is assigned by the repository.
      doAnswer((inv: org.mockito.invocation.InvocationOnMock) => {
        val c = inv.getArgument[SubmissionCommand](0)
        if c.step == InitialPdf then Future.failed(new RuntimeException("pdf unavailable"))
        else Future.successful(c.copy(operationFinished = true))
      }).when(f.ops).execute(any())(using any())
      f.run(InitialPdf)
      f.statusOf(d) mustBe 200
      f.states.get(d.scopeKey).futureValue.get.pdfStored mustBe false
      f.entries(EnsurePdf).size mustBe 1
      f.entries(SendEmail).size mustBe (if kind == SubmissionKind.Notification then 2 else 3)
      contentAsJson(f.service.status(d.subscriptionId, d.kind, d.idempotencyKey)).toString must include("REF123")
    }
  }
  "changed payload conflicts, missing keys are generated, and submission types do not share keys" in {
    val f = new Fixture
    val d = data()
    f.service.submit(d).futureValue.header.status mustBe 202
    val changed = d.copy(notification = d.notification.map(_.copy(remarks = Some(uk.gov.hmrc.senioraccountingofficer.models.requests.FreeText("changed")))))
    f.service.submit(changed).futureValue.header.status mustBe 409
    f.service.submit(data(SubmissionKind.Certificate)).futureValue.header.status mustBe 202
    val generated = SubmissionData.notification("subscription", "correlation", notification.copy(idempotencyKey = None))
    UUID.fromString(generated.idempotencyKey) mustBe a[UUID]
    f.service.submit(generated).futureValue.header.status mustBe 202
  }
  "acceptance interrupted before status creation is repaired by the root worker" in {
    val f = new Fixture
    val c = SubmissionCommand.start(data().copy(reference = None))
    f.queues(RetrieveSubscription).enqueue(c).futureValue
    f.statusOf(c.data) mustBe 404
    f.run(RetrieveSubscription)
    f.statusOf(c.data) mustBe 204
    f.entries(RetrieveCustomer).size mustBe 1
  }
  "a transient lookup failure waits for backoff and permanent failure reaches GET" in {
    val f = new Fixture
    val c = command(RetrieveCustomer).copy(data = data().copy(reference = None))
    f.seed(c)
    doReturn(Future.failed(SubmissionFailures.response(503))).when(f.ops).execute(any())(using any())
    f.run(RetrieveCustomer)
    f.entries(RetrieveCustomer).head.status mustBe ProcessingStatus.Failed
    f.run(RetrieveCustomer)
    verify(f.ops, times(1)).execute(any())(using any())
    f.statusOf(c.data) mustBe 204
    f.clock.advance(61)
    doReturn(Future.failed(SubmissionFailures.response(401))).when(f.ops).execute(any())(using any())
    f.run(RetrieveCustomer)
    f.statusOf(c.data) mustBe 500
    f.entries(SubmitDps) mustBe empty
  }
  "an interrupted DPS dispatch is never sent again" in {
    val f = new Fixture
    val c = command(SubmitDps).copy(dispatched = true)
    f.seed(c)
    f.run(SubmitDps)
    verify(f.ops, never()).execute(any())(using any())
    f.statusOf(c.data) mustBe 502
    f.entries(SubmitDps).head.item.failure.get.ambiguous mustBe true
    f.entries(InitialPdf) mustBe empty
  }
  "DPS transport failure and timeout are terminal without resubmission" in {
    val f = new Fixture
    val c = command(SubmitDps)
    f.seed(c)
    doReturn(scala.concurrent.Promise[SubmissionCommand]().future).when(f.ops).execute(any())(using any())
    f.run(SubmitDps)
    f.clock.advance(1000)
    f.run(SubmitDps)
    verify(f.ops, times(1)).execute(any())(using any())
    f.statusOf(c.data) mustBe 502
  }
  "an explicit DPS rate-limit rejection can be retried" in {
    val f = new Fixture
    f.seed(command(SubmitDps))
    doReturn(Future.failed(SubmissionFailures.response(429, true))).when(f.ops).execute(any())(using any())
    f.run(SubmitDps)
    f.entries(SubmitDps).head.item.dispatched mustBe false
    f.entries(SubmitDps).head.status mustBe ProcessingStatus.Failed
  }
  "saved DPS success resumes handoff without repeating DPS and duplicate handoffs create one child" in {
    val f = new Fixture
    val c = command(SubmitDps).copy(operationFinished = true, dispatched = true)
    f.seed(c)
    val next = f.ops.successors(c).head
    f.queues(InitialPdf).enqueue(next).futureValue // Child inserted, parent completion interrupted.
    f.run(SubmitDps)
    verify(f.ops, never()).execute(any())(using any())
    f.entries(InitialPdf).size mustBe 1
    f.entries(SubmitDps).head.status mustBe ProcessingStatus.Succeeded
    f.states.get(c.data.scopeKey).futureValue.get.reference mustBe Some("REF123")
  }
  "interrupted initial PDF attempt completes without attempting generation again" in {
    val f = new Fixture
    val c = command(InitialPdf).copy(dispatched = true)
    f.seed(c)
    f.run(InitialPdf)
    verify(f.ops, never()).execute(any())(using any())
    f.statusOf(c.data) mustBe 200
  }
  "stale worker writes are rejected after another worker claims the lease" in {
    val f = new Fixture
    val repository = f.queues(RetrieveCustomer)
    f.seed(command(RetrieveCustomer))
    val first = repository.claim().futureValue.get
    f.clock.advance(121)
    val second = repository.claim().futureValue.get
    first.item.leaseToken must not be second.item.leaseToken
    repository.save(first, first.item.copy(operationFinished = true)).failed.futureValue mustBe a[IllegalStateException]
    repository.save(second, second.item.copy(operationFinished = true)).futureValue.item.operationFinished mustBe true
  }
  "status and deduplication expire only after all work is terminal and seven days pass" in {
    val f = new Fixture
    val root = SubmissionCommand.start(data()).copy(operationFinished = true, failure = Some(SubmissionFailures.ambiguous))
    f.seed(root)
    f.run(RetrieveSubscription)
    f.worker.maintain().futureValue
    f.statusOf(root.data) mustBe 502
    val before = f.states.get(root.data.scopeKey).futureValue.get.expiresAt
    f.service.status(root.data.subscriptionId, root.data.kind, root.data.idempotencyKey).futureValue
    f.states.get(root.data.scopeKey).futureValue.get.expiresAt mustBe before
    f.clock.advance(604801)
    f.statusOf(root.data) mustBe 404
    val replacement = data()
    f.service.submit(replacement).futureValue.header.status mustBe 202
    f.entries(RetrieveSubscription).head.item.data.orchestrationId mustBe replacement.orchestrationId
  }
  "a failed status write retries publication without repeating the successful DPS POST" in {
    val f = new Fixture
    val c = command(SubmitDps)
    f.seed(c)
    val unavailableStates = spy(f.states)
    doReturn(Future.failed(new RuntimeException("mongo unavailable"))).when(unavailableStates).publish(any())
    val worker = new SubmissionWorker(f.queues, unavailableStates, f.ops, f.config, summon[ActorSystem], new MetricRegistry, f.clock)
    worker.processNext(SubmitDps).futureValue
    f.entries(SubmitDps).head.item.operationFinished mustBe true
    f.entries(SubmitDps).head.status mustBe ProcessingStatus.Failed
    f.clock.advance(61)
    f.run(SubmitDps)
    verify(f.ops, times(1)).execute(any())(using any())
    f.entries(InitialPdf).size mustBe 1
  }
  for step <- Seq(SendEmail, EnsurePdf, PackageDocumentum, NotifySdes) do {
    s"$step retries independently without changing completed public status" in {
      val f = new Fixture
      val c = command(step).copy(data = data().copy(pdfAttempted = true, pdfStored = true))
      f.seed(c)
      f.states.publish(c).futureValue
      doReturn(Future.failed(SubmissionFailures.response(503))).when(f.ops).execute(any())(using any())
      f.run(step)
      f.entries(step).head.status mustBe ProcessingStatus.Failed
      f.statusOf(c.data) mustBe 200
      f.clock.advance(61)
      doReturn(Future.failed(SubmissionFailures.response(400))).when(f.ops).execute(any())(using any())
      f.run(step)
      f.entries(step).head.status mustBe ProcessingStatus.PermanentlyFailed
      f.statusOf(c.data) mustBe 200
    }
  }
  "concurrent claims yield a single owner and active graphs never acquire an expiry" in {
    val f = new Fixture
    val c = SubmissionCommand.start(data())
    f.seed(c)
    val claims = Future.traverse(1 to 8)(_ => f.queues(RetrieveSubscription).claim()).futureValue.flatten
    claims.size mustBe 1
    f.queues(RetrieveSubscription).save(claims.head, claims.head.item.copy(operationFinished = true)).futureValue
    f.clock.advance(121)
    f.run(RetrieveSubscription)
    f.worker.maintain().futureValue
    f.entries(RetrieveSubscription).head.item.expiresAt mustBe None
    f.states.get(c.data.scopeKey).futureValue.get.expiresAt mustBe None
  }

}
