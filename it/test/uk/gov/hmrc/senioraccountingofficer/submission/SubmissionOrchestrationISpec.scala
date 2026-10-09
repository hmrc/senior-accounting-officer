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

import ch.qos.logback.classic.{Level, Logger}
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import org.slf4j.LoggerFactory
import scala.jdk.CollectionConverters.*
import com.codahale.metrics.MetricRegistry
import org.apache.pekko.actor.ActorSystem
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.*
import org.mongodb.scala.{ObservableFuture, SingleObservableFuture}
import org.mongodb.scala.model.Filters
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach, OptionValues}
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.must.Matchers
import org.scalatestplus.mockito.MockitoSugar
import play.api.Configuration
import play.api.test.Helpers.*
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.workitem.{ProcessingStatus, WorkItem}
import uk.gov.hmrc.senioraccountingofficer.models.submission.*
import uk.gov.hmrc.senioraccountingofficer.models.submission.SubmissionStep.*
import uk.gov.hmrc.senioraccountingofficer.repositories.submission.*
import uk.gov.hmrc.senioraccountingofficer.services.submission.*
import uk.gov.hmrc.senioraccountingofficer.services.submission.SubmissionTestData.*
import java.time.{Clock, Instant, ZoneId, ZoneOffset}
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong
import scala.concurrent.{ExecutionContext, Future, Promise}

class SubmissionOrchestrationISpec
    extends AnyWordSpec
    with Matchers
    with OptionValues
    with ScalaFutures
    with IntegrationPatience
    with MockitoSugar
    with BeforeAndAfterAll
    with BeforeAndAfterEach {

  given ActorSystem = ActorSystem("submission-repository-test")

  given ExecutionContext = summon[ActorSystem].dispatcher

  override def beforeAll(): Unit = {
    super.beforeAll()
    queues.all.values.foreach(_.initialised.futureValue)
    states.initialised.futureValue
  }

  override def afterAll(): Unit = {
    try mongo.database.drop().toFuture().futureValue
    finally {
      mongo.client.close()
      summon[ActorSystem].terminate().futureValue
      super.afterAll()
    }
  }

  private class TestClock extends Clock {

    private val millis = new AtomicLong(System.currentTimeMillis())

    def reset(): Unit                = millis.set(System.currentTimeMillis())
    def advance(seconds: Long): Unit = {
      millis.addAndGet(seconds * 1000)
      ()
    }
    override def instant(): Instant            = Instant.ofEpochMilli(millis.incrementAndGet())
    override def getZone: ZoneId               = ZoneOffset.UTC
    override def withZone(zone: ZoneId): Clock = Clock.fixed(instant(), zone)
  }

  private val clock = new TestClock
  val mongo         = MongoComponent(s"mongodb://localhost:27017/sao-orchestration-test-${UUID.randomUUID()}")
  val config        = Configuration(
    "work-items.lease-seconds"             -> 120,
    "work-items.operation-timeout-seconds" -> 1,
    "work-items.retry-base-seconds"        -> 60,
    "work-items.retry-max-seconds"         -> 900,
    "work-items.retention-seconds"         -> 604800
  )
  val queues                                         = new SubmissionQueues(mongo, config, clock)
  val states                                         = new SubmissionStateRepository(mongo, clock)
  val mockSubmissionOperations: SubmissionOperations = mock[SubmissionOperations]
  val worker                                         =
    new SubmissionWorker(
      queues,
      states,
      mockSubmissionOperations,
      config,
      summon[ActorSystem],
      new MetricRegistry,
      clock
    )

  val service = new SubmissionService(queues, states)

  def seed(submission: SubmissionCommand): Unit = {
    states.initialize(submission.data).futureValue
    queues(submission.step).enqueue(submission).futureValue
    ()
  }

  def run(step: SubmissionStep): Unit = worker.processNext(step).futureValue

  def entries(step: SubmissionStep): Seq[WorkItem[SubmissionCommand]] =
    queues(step).collection.find().toFuture().futureValue

  def statusOf(submissionData: SubmissionData): Int = status(
    service.status(submissionData.subscriptionId, submissionData.kind, submissionData.idempotencyKey)
  )

  override def beforeEach(): Unit = {
    super.beforeEach()
    clock.reset()
    reset(mockSubmissionOperations)
    queues.all.values.foreach(_.collection.deleteMany(Filters.empty()).toFuture().futureValue)
    states.collection.deleteMany(Filters.empty()).toFuture().futureValue
    when(mockSubmissionOperations.successors(any())).thenCallRealMethod()
    when(mockSubmissionOperations.execute(any())(using any())).thenAnswer { invocation =>
      val submission = invocation.getArgument[SubmissionCommand](0)
      val updated    = submission.step match {
        case SubmitDps  => submission.data.copy(reference = Some("REF123"), submittedAt = Some(time))
        case InitialPdf => submission.data.copy(pdfAttempted = true, pdfStored = true)
        case _          => submission.data
      }
      Future.successful(submission.copy(data = updated, operationFinished = true))
    }
  }

  "SubmissionService.submit" must {
    for kind <- SubmissionKind.values do {
      s"queue one root command for concurrent identical $kind submissions" in {
        val submissionData = data(kind).copy(reference = None, submittedAt = None)
        val responses      = Future
          .traverse(1 to 8)(_ => service.submit(submissionData.copy(orchestrationId = UUID.randomUUID().toString)))
          .futureValue
        responses.map(_.header.status).distinct mustBe Seq(202)
        entries(RetrieveSubscription).size mustBe 1
        statusOf(submissionData) mustBe 204
      }
    }

    for kind <- SubmissionKind.values do {
      s"isolate $kind submissions for different subscriptions" in {
        val submissionData = data(kind).copy(reference = None, submittedAt = None)
        service.submit(submissionData).futureValue.header.status mustBe 202
        statusOf(submissionData.copy(subscriptionId = "another-subscription")) mustBe 404
        service
          .submit(
            submissionData.copy(subscriptionId = "another-subscription", orchestrationId = UUID.randomUUID().toString)
          )
          .futureValue
          .header
          .status mustBe 202
        entries(RetrieveSubscription).size mustBe 2
      }
    }

    "reject a changed payload submitted with an existing idempotency key" in {
      val submissionData = data()
      service.submit(submissionData).futureValue.header.status mustBe 202
      val changed = submissionData.copy(notification =
        submissionData.notification.map(
          _.copy(remarks = Some(uk.gov.hmrc.senioraccountingofficer.models.requests.FreeText("changed")))
        )
      )
      service.submit(changed).futureValue.header.status mustBe 409
    }

    "allow notification and certificate submissions to use the same idempotency key" in {
      service.submit(data()).futureValue.header.status mustBe 202
      service.submit(data(SubmissionKind.Certificate)).futureValue.header.status mustBe 202
      entries(RetrieveSubscription).size mustBe 2
    }

    "accept a submission with a generated idempotency key" in {
      val generated =
        SubmissionData.notification("subscription", "correlation", notification.copy(idempotencyKey = None))
      UUID.fromString(generated.idempotencyKey) mustBe a[UUID]
      service.submit(generated).futureValue.header.status mustBe 202
    }
  }

  "SubmissionStateRepository.publish" must {
    for failure <- Seq(None, Some(SubmissionFailures.unknownDpsOutcome)) do {
      s"record and preserve the public outcome timestamp for ${if failure.isDefined then "failure" else "success"}" in {
        val submission = command(InitialPdf).copy(
          data = data().copy(pdfAttempted = true, pdfStored = true),
          failure = failure
        )
        states.initialize(submission.data).futureValue
        states.get(submission.data.scopeKey).futureValue.value.outcomeRecordedAt mustBe None
        states.publish(submission).futureValue
        val recorded = states.get(submission.data.scopeKey).futureValue.value
        recorded.outcomeRecordedAt mustBe defined
        recorded.failure mustBe failure

        clock.advance(60)
        states.publish(submission.copy(data = submission.data.copy(reference = Some("DIFFERENT")))).futureValue
        states.get(submission.data.scopeKey).futureValue.value mustBe recorded
      }
    }

    "reject PDF completion without a reference without publishing a terminal state" in {
      val submission = command(InitialPdf).copy(data = data().copy(reference = None, pdfAttempted = true))
      states.initialize(submission.data).futureValue
      val before = states.get(submission.data.scopeKey).futureValue

      val error = states.publish(submission).failed.futureValue
      error mustBe a[IllegalStateException]
      error.getMessage mustBe "Missing required submission field: reference"
      states.get(submission.data.scopeKey).futureValue mustBe before
    }
  }

  "SubmissionWorkRepository lease ownership" must {
    for operation <- Seq("heartbeat", "save", "finish", "retry") do {
      s"reject $operation without a lease token and leave the work item unchanged" in {
        val submission = command(RetrieveSubscription)
        seed(submission)
        val repository = queues(RetrieveSubscription)
        val claimed    = repository.claim().futureValue.value
        val unleased   = claimed.copy(item = claimed.item.copy(leaseToken = None))
        val result     = operation match {
          case "heartbeat" => repository.heartbeat(unleased)
          case "save"      => repository.save(unleased, unleased.item).map(_ => ())
          case "finish"    => repository.finish(unleased)
          case "retry"     => repository.retry(unleased, 60, "error")
        }

        val error = result.failed.futureValue
        error mustBe a[IllegalStateException]
        error.getMessage mustBe "Missing work item lease token"
        entries(RetrieveSubscription) mustBe Seq(claimed)
      }
    }

    "reject writes from a worker after its lease has been reclaimed" in {
      val repository = queues(RetrieveCustomer)
      seed(command(RetrieveCustomer))
      val first = repository.claim().futureValue.value
      clock.advance(121)
      val second = repository.claim().futureValue.value
      first.item.leaseToken must not be second.item.leaseToken
      repository
        .save(first, first.item.copy(operationFinished = true))
        .failed
        .futureValue mustBe a[IllegalStateException]
      repository.save(second, second.item.copy(operationFinished = true)).futureValue.item.operationFinished mustBe true
    }

    "return a single owner for concurrent claims" in {
      val submission = SubmissionCommand.start(data())
      seed(submission)
      val claims = Future.traverse(1 to 8)(_ => queues(RetrieveSubscription).claim()).futureValue.flatten
      claims.size mustBe 1
    }
  }

  "SubmissionWorker.processNext" must {
    for failure <- Seq(None, Some(SubmissionFailures.unknownDpsOutcome)) do {
      s"log ${if failure.isDefined then "permanent failure at WARN" else "successful completion at INFO"}" in {
        val logger   = LoggerFactory.getLogger(classOf[SubmissionWorker]).asInstanceOf[Logger]
        val appender = new ListAppender[ILoggingEvent]()
        appender.start()
        logger.addAppender(appender)
        try {
          val submission = command(RetrieveCustomer).copy(operationFinished = true, failure = failure)
          seed(submission)
          run(RetrieveCustomer)
          val metric = if failure.isDefined then "permanent-failure" else "completed"
          val event  = appender.list.asScala.find(_.getFormattedMessage.contains(s"[RetrieveCustomer][$metric]")).value
          event.getLevel mustBe (if failure.isDefined then Level.WARN else Level.INFO)
          event.getFormattedMessage must include(s"[OrchestrationId=${submission.data.orchestrationId}]")
          event.getFormattedMessage must include(s"[CorrelationId=${submission.data.correlationId}]")
          if failure.isDefined then event.getFormattedMessage must include("[DpsOutcomeUnknown=true]")
        } finally {
          logger.detachAppender(appender)
          appender.stop()
        }
      }
    }

    for kind <- SubmissionKind.values do {
      s"complete a $kind submission after a failed initial PDF attempt while delivery remains queued" in {
        val submissionData = data(kind).copy(reference = None, submittedAt = None)
        service.submit(submissionData).futureValue
        Seq(RetrieveSubscription, RetrieveCustomer, SubmitDps).foreach(run)
        statusOf(submissionData) mustBe 204
        doReturn(Future.failed(new RuntimeException("pdf unavailable")))
          .when(mockSubmissionOperations)
          .execute(any())(using any())
        run(InitialPdf)
        statusOf(submissionData) mustBe 200
        states.get(submissionData.scopeKey).futureValue.value.pdfStored mustBe false
        entries(EnsurePdf).size mustBe 1
        entries(SendEmail).size mustBe (if kind == SubmissionKind.Notification then 2 else 3)
        contentAsJson(
          service.status(submissionData.subscriptionId, submissionData.kind, submissionData.idempotencyKey)
        ).toString must include("REF123")
      }
    }

    "create missing public state when processing an accepted root command" in {
      val submission = SubmissionCommand.start(data().copy(reference = None))
      queues(RetrieveSubscription).enqueue(submission).futureValue
      statusOf(submission.data) mustBe 404
      run(RetrieveSubscription)
      statusOf(submission.data) mustBe 204
      entries(RetrieveCustomer).size mustBe 1
    }

    "wait for backoff before retrying a transient customer lookup failure" in {
      val submission = command(RetrieveCustomer).copy(data = data().copy(reference = None))
      seed(submission)
      doReturn(Future.failed(SubmissionFailures.response(503)))
        .when(mockSubmissionOperations)
        .execute(any())(using any())
      run(RetrieveCustomer)
      entries(RetrieveCustomer).head.status mustBe ProcessingStatus.Failed
      run(RetrieveCustomer)
      verify(mockSubmissionOperations, times(1)).execute(any())(using any())
      statusOf(submission.data) mustBe 204
      clock.advance(61)
      run(RetrieveCustomer)
      verify(mockSubmissionOperations, times(2)).execute(any())(using any())
    }

    "publish a permanent customer lookup failure without submitting to DPS" in {
      val submission = command(RetrieveCustomer).copy(data = data().copy(reference = None))
      seed(submission)
      doReturn(Future.failed(SubmissionFailures.response(401)))
        .when(mockSubmissionOperations)
        .execute(any())(using any())
      run(RetrieveCustomer)
      statusOf(submission.data) mustBe 500
      entries(SubmitDps) mustBe empty
    }

    "record an unknown outcome without repeating an interrupted DPS dispatch" in {
      val submission = command(SubmitDps).copy(dispatched = true)
      seed(submission)
      run(SubmitDps)
      verify(mockSubmissionOperations, never()).execute(any())(using any())
      statusOf(submission.data) mustBe 502
      entries(SubmitDps).head.item.failure.value.dpsOutcomeUnknown mustBe true
      entries(InitialPdf) mustBe empty
    }

    "record an unknown outcome without resubmitting after a DPS timeout" in {
      val submission = command(SubmitDps)
      seed(submission)
      doReturn(Promise[SubmissionCommand]().future).when(mockSubmissionOperations).execute(any())(using any())
      run(SubmitDps)
      clock.advance(1000)
      run(SubmitDps)
      verify(mockSubmissionOperations, times(1)).execute(any())(using any())
      statusOf(submission.data) mustBe 502
    }

    "allow a retry after an explicit DPS rate-limit rejection" in {
      seed(command(SubmitDps))
      doReturn(Future.failed(SubmissionFailures.response(429, true)))
        .when(mockSubmissionOperations)
        .execute(any())(using any())
      run(SubmitDps)
      entries(SubmitDps).head.item.dispatched mustBe false
      entries(SubmitDps).head.status mustBe ProcessingStatus.Failed
    }

    "resume a saved DPS result without repeating the call or duplicating its successor" in {
      val submission = command(SubmitDps).copy(operationFinished = true, dispatched = true)
      seed(submission)
      val next = mockSubmissionOperations.successors(submission).head
      queues(InitialPdf).enqueue(next).futureValue // Child inserted, parent completion interrupted.
      run(SubmitDps)
      verify(mockSubmissionOperations, never()).execute(any())(using any())
      entries(InitialPdf).size mustBe 1
      entries(SubmitDps).head.status mustBe ProcessingStatus.Succeeded
      states.get(submission.data.scopeKey).futureValue.value.reference mustBe Some("REF123")
    }

    "complete an interrupted initial PDF attempt without regenerating it" in {
      val submission = command(InitialPdf).copy(dispatched = true)
      seed(submission)
      run(InitialPdf)
      verify(mockSubmissionOperations, never()).execute(any())(using any())
      statusOf(submission.data) mustBe 200
    }

    "retry public state publication without repeating a successful DPS call" in {
      val submission = command(SubmitDps)
      seed(submission)
      val unavailableStates = spy(states)
      doReturn(Future.failed(new RuntimeException("mongo unavailable"))).when(unavailableStates).publish(any())
      val workerWithUnavailableState = new SubmissionWorker(
        queues,
        unavailableStates,
        mockSubmissionOperations,
        config,
        summon[ActorSystem],
        new MetricRegistry,
        clock
      )
      workerWithUnavailableState.processNext(SubmitDps).futureValue
      entries(SubmitDps).head.item.operationFinished mustBe true
      entries(SubmitDps).head.status mustBe ProcessingStatus.Failed
      clock.advance(61)
      run(SubmitDps)
      verify(mockSubmissionOperations, times(1)).execute(any())(using any())
      entries(InitialPdf).size mustBe 1
    }

    for step <- Seq(SendEmail, EnsurePdf, PackageDocumentum, NotifySdes) do {
      s"handle $step failures without changing the recorded public outcome" in {
        val submission = command(step).copy(data = data().copy(pdfAttempted = true, pdfStored = true))
        seed(submission)
        states.publish(submission).futureValue
        val publicOutcome = states.get(submission.data.scopeKey).futureValue.value
        doReturn(Future.failed(SubmissionFailures.response(503)))
          .when(mockSubmissionOperations)
          .execute(any())(using any())
        run(step)
        entries(step).head.status mustBe ProcessingStatus.Failed
        statusOf(submission.data) mustBe 200
        states.get(submission.data.scopeKey).futureValue.value mustBe publicOutcome
        clock.advance(61)
        doReturn(Future.failed(SubmissionFailures.response(400)))
          .when(mockSubmissionOperations)
          .execute(any())(using any())
        run(step)
        entries(step).head.status mustBe ProcessingStatus.PermanentlyFailed
        entries(step).head.item.failure mustBe Some(SubmissionFailures.response(400).failure)
        statusOf(submission.data) mustBe 200
        states.get(submission.data.scopeKey).futureValue.value mustBe publicOutcome
      }
    }
  }

  "SubmissionWorker.maintain" must {
    "expire public status and allow key reuse seven days after all work finishes" in {
      val root =
        SubmissionCommand
          .start(data())
          .copy(operationFinished = true, failure = Some(SubmissionFailures.unknownDpsOutcome))
      seed(root)
      run(RetrieveSubscription)
      worker.maintain().futureValue
      statusOf(root.data) mustBe 502
      val before = states.get(root.data.scopeKey).futureValue.value.expiresAt
      service.status(root.data.subscriptionId, root.data.kind, root.data.idempotencyKey).futureValue
      states.get(root.data.scopeKey).futureValue.value.expiresAt mustBe before
      clock.advance(604801)
      statusOf(root.data) mustBe 404
      val replacement = data()
      service.submit(replacement).futureValue.header.status mustBe 202
      entries(RetrieveSubscription).head.item.data.orchestrationId mustBe replacement.orchestrationId
    }

    "keep active submissions without an expiry while handing work to the next step" in {
      val submission = SubmissionCommand.start(data())
      seed(submission)
      val claimed = queues(RetrieveSubscription).claim().futureValue.value
      queues(RetrieveSubscription).save(claimed, claimed.item.copy(operationFinished = true)).futureValue
      clock.advance(121)
      run(RetrieveSubscription)
      worker.maintain().futureValue
      entries(RetrieveSubscription).head.item.expiresAt mustBe None
      states.get(submission.data.scopeKey).futureValue.value.expiresAt mustBe None
    }
  }
}
