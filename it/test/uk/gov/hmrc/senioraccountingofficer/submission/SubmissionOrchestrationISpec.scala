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
import com.codahale.metrics.MetricRegistry
import org.apache.pekko.actor.ActorSystem
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.*
import org.mongodb.scala.{ObservableFuture, SingleObservableFuture}
import org.mongodb.scala.model.{Filters, Updates}
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach, OptionValues}
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.must.Matchers
import org.scalatestplus.mockito.MockitoSugar
import org.slf4j.LoggerFactory
import play.api.Configuration
import play.api.test.Helpers.*
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.workitem.{ProcessingStatus, WorkItem}
import uk.gov.hmrc.senioraccountingofficer.models.submission.*
import uk.gov.hmrc.senioraccountingofficer.models.submission.SubmissionStep.*
import uk.gov.hmrc.senioraccountingofficer.repositories.submission.*
import uk.gov.hmrc.senioraccountingofficer.services.submission.*
import uk.gov.hmrc.senioraccountingofficer.services.submission.SubmissionTestData.*

import scala.concurrent.duration.*
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.jdk.CollectionConverters.*

import java.time.{Clock, Instant, ZoneId, ZoneOffset}
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong

class SubmissionOrchestrationISpec
    extends AnyWordSpec
    with Matchers
    with OptionValues
    with ScalaFutures
    with IntegrationPatience
    with MockitoSugar
    with BeforeAndAfterAll
    with BeforeAndAfterEach {

  private val millisecondsPerSecond = 1000L
  private val retentionSeconds      = 7.days.toSeconds

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

    private val milliseconds = new AtomicLong(System.currentTimeMillis())

    def reset(): Unit                = milliseconds.set(System.currentTimeMillis())
    def set(instant: Instant): Unit  = milliseconds.set(instant.toEpochMilli)
    def advance(seconds: Long): Unit = {
      milliseconds.addAndGet(seconds * millisecondsPerSecond)
      ()
    }
    override def instant(): Instant            = Instant.ofEpochMilli(milliseconds.incrementAndGet())
    override def getZone: ZoneId               = ZoneOffset.UTC
    override def withZone(zone: ZoneId): Clock = Clock.fixed(instant(), zone)
  }

  private val clock = new TestClock
  val mongo         = MongoComponent(s"mongodb://localhost:27017/sao-orchestration-test-${UUID.randomUUID()}")
  val config        = Configuration(
    "work-items.replay.run-id"             -> "test-run",
    "work-items.lease-seconds"             -> 120,
    "work-items.operation-timeout-seconds" -> 1,
    "work-items.retry-delays-seconds"      -> Seq(60L, 300L, 900L, 3600L),
    "work-items.retention-seconds"         -> retentionSeconds
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

  def workerWithConfig(overrides: Configuration): SubmissionWorker =
    new SubmissionWorker(
      queues,
      states,
      mockSubmissionOperations,
      config ++ overrides,
      summon[ActorSystem],
      new MetricRegistry,
      clock
    )

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

  "Failure retention" must {
    "extend legacy failures from their failure time but leave expired records alone" in {
      clock.set(Instant.parse("2030-07-01T10:00:00Z"))
      val submission = command(NotifySdes).copy(data = data().copy(prepared = Some(preparedSdesSubmission)))
      seed(submission)
      doReturn(Future.failed(SubmissionFailures.response(FORBIDDEN)))
        .when(mockSubmissionOperations)
        .execute(any())(using any())
      run(NotifySdes)
      val repository = queues(NotifySdes)
      val failedAt   = entries(NotifySdes).head.updatedAt
      repository.collection
        .updateMany(
          Filters.empty(),
          Updates.combine(
            Updates.unset("item.lastPermanentFailureAt"),
            Updates.set("item.expiresAt", failedAt.plusSeconds(7.days.toSeconds))
          )
        )
        .toFuture()
        .futureValue
      worker.maintain().futureValue
      entries(NotifySdes).head.item.lastPermanentFailureAt mustBe Some(failedAt)
      entries(NotifySdes).head.item.expiresAt mustBe Some(failedAt.plusSeconds(30.days.toSeconds))
      val expiredAt = clock.instant().minusSeconds(1)
      repository.collection.updateMany(Filters.empty(), Updates.set("item.expiresAt", expiredAt)).toFuture().futureValue
      worker.maintain().futureValue
      entries(NotifySdes).head.item.expiresAt mustBe Some(expiredAt)
    }

    "extend the whole orchestration from a stable failure timestamp and preserve it during replay" in {
      val submission = command(NotifySdes).copy(data = data().copy(prepared = Some(preparedSdesSubmission)))
      seed(submission)
      queues(EnsurePdf).enqueue(submission.next(EnsurePdf)).futureValue
      doReturn(Future.failed(SubmissionFailures.response(FORBIDDEN)))
        .when(mockSubmissionOperations)
        .execute(any())(using any())
      run(NotifySdes)
      val active   = queues(EnsurePdf).claim().futureValue.value
      val failed   = entries(NotifySdes).head
      val deadline = failed.item.lastPermanentFailureAt.value.plusSeconds(30.days.toSeconds)
      worker.maintain().futureValue
      entries(NotifySdes).head.item.expiresAt mustBe Some(deadline)
      entries(EnsurePdf).head.item.expiresAt mustBe Some(deadline)
      queues(EnsurePdf).save(active, active.item.copy(operationFinished = true)).futureValue
      entries(EnsurePdf).head.item.expiresAt mustBe Some(deadline)
      states.get(submission.data.scopeKey).futureValue.value.expiresAt mustBe Some(deadline)
      clock.advance(60)
      worker.maintain().futureValue
      entries(NotifySdes).head.item.expiresAt mustBe Some(deadline)
      queues(NotifySdes).replaySdes(submission.data.orchestrationId, "recovery").futureValue mustBe true
      entries(NotifySdes).head.item.expiresAt mustBe Some(deadline)
    }
  }

  "Configured SDES replay" must {
    "replay a successful notification once per configured run ID" in {
      val submission = command(NotifySdes).copy(data = data().copy(prepared = Some(preparedSdesSubmission)))
      seed(submission)
      run(NotifySdes)
      for runId <- Seq("first", "second", "first", "second") do {
        val replayWorker = workerWithConfig(
          Configuration(
            "work-items.replay.run-id"                        -> runId,
            "work-items.replay.sdes-replay-orchestration-ids" -> Seq(submission.data.orchestrationId)
          )
        )
        replayWorker.maintain().futureValue
        run(NotifySdes)
        entries(NotifySdes).head.status mustBe ProcessingStatus.Succeeded
      }
      verify(mockSubmissionOperations, times(3)).execute(any())(using any())
    }

    "replay a configured failure at the configured UTC time and complete successfully" in {
      clock.set(Instant.parse("2030-07-01T10:59:00Z"))
      val submission = command(NotifySdes).copy(data = data().copy(prepared = Some(preparedSdesSubmission)))
      seed(submission)
      doReturn(Future.failed(SubmissionFailures.response(FORBIDDEN)))
        .when(mockSubmissionOperations)
        .execute(any())(using any())
      run(NotifySdes)
      val replayWorker = workerWithConfig(
        Configuration(
          "work-items.replay.sdes-replay-orchestration-ids" -> Seq(submission.data.orchestrationId),
          "work-items.replay.replay-time"                   -> "11:00:00",
          "work-items.replay.time-zone"                     -> "UTC"
        )
      )
      replayWorker.maintain().futureValue
      entries(NotifySdes).head.status mustBe ProcessingStatus.PermanentlyFailed
      clock.advance(60)
      replayWorker.maintain().futureValue
      doAnswer { invocation =>
        Future.successful(invocation.getArgument[SubmissionCommand](0).copy(operationFinished = true))
      }.when(mockSubmissionOperations).execute(any())(using any())
      run(NotifySdes)
      entries(NotifySdes).head.status mustBe ProcessingStatus.Succeeded
      replayWorker.maintain().futureValue
      entries(NotifySdes).head.status mustBe ProcessingStatus.Succeeded
      entries(SubmitDps) mustBe empty
      entries(SendEmail) mustBe empty
      entries(PackageDocumentum) mustBe empty
    }

    "atomically replay only once, execute the notification again and preserve its data" in {
      val submission = command(NotifySdes).copy(data = data().copy(prepared = Some(preparedSdesSubmission)))
      seed(submission)
      doReturn(Future.failed(SubmissionFailures.response(FORBIDDEN)))
        .when(mockSubmissionOperations)
        .execute(any())(using any())
      run(NotifySdes)
      val repository = queues(NotifySdes)
      entries(NotifySdes).head.status mustBe ProcessingStatus.PermanentlyFailed
      val results =
        Future.traverse(1 to 8)(_ => repository.replaySdes(submission.data.orchestrationId, "test-run")).futureValue
      results.count(identity) mustBe 1
      val replay = entries(NotifySdes).head.item
      replay.operationFinished mustBe false
      replay.failure mustBe None
      replay.leaseToken mustBe None
      replay.data mustBe submission.data
      replay.sdesReplayRequestedAt mustBe defined
      run(NotifySdes)
      entries(NotifySdes).head.status mustBe ProcessingStatus.PermanentlyFailed
      repository.replaySdes(submission.data.orchestrationId, "test-run").futureValue mustBe false
      verify(mockSubmissionOperations, times(2)).execute(any())(using any())
    }

    "ignore pending, active, missing and expired work items" in {
      val submission = command(NotifySdes).copy(data = data().copy(prepared = Some(preparedSdesSubmission)))
      seed(submission)
      val repository = queues(NotifySdes)
      repository.replaySdes(submission.data.orchestrationId, "test-run").futureValue mustBe false
      val claimed = repository.claim().futureValue.value
      repository.replaySdes(submission.data.orchestrationId, "test-run").futureValue mustBe false
      repository.retry(claimed, 60, "error").futureValue
      repository.replaySdes(submission.data.orchestrationId, "test-run").futureValue mustBe false
      clock.advance(61)
      run(NotifySdes)
      repository.replaySdes("missing", "test-run").futureValue mustBe false
      repository.collection
        .updateMany(
          Filters.empty(),
          Updates.combine(
            Updates.set("status", ProcessingStatus.PermanentlyFailed),
            Updates.set("item.expiresAt", clock.instant().minusSeconds(1))
          )
        )
        .toFuture()
        .futureValue
      repository.replaySdes(submission.data.orchestrationId, "test-run").futureValue mustBe false
    }
  }

  "Configured PDF replay" must {
    "rebuild the ZIP before notifying SDES at the configured UK time, even when both lists contain the ID" in {
      clock.set(Instant.parse("2030-07-01T08:59:00Z"))
      val oldPackage = preparedSdesSubmission.copy(checksum = "old-checksum")
      val newPackage = oldPackage.copy(checksum = "new-checksum", contentLength = 200L)
      val submission = command(EnsurePdf).copy(data = data().copy(prepared = Some(oldPackage)))
      seed(submission)
      run(EnsurePdf)
      run(PackageDocumentum)
      doReturn(Future.failed(SubmissionFailures.response(FORBIDDEN)))
        .when(mockSubmissionOperations)
        .execute(any())(using any())
      run(NotifySdes)
      val replayConfig = Configuration(
        "work-items.replay.replay-time"                  -> "10:00:00",
        "work-items.replay.time-zone"                    -> "Europe/London",
        "work-items.replay.pdf-replay-orchestration-ids" -> Seq(
          submission.data.orchestrationId,
          submission.data.orchestrationId
        ),
        "work-items.replay.sdes-replay-orchestration-ids" -> Seq(submission.data.orchestrationId)
      )
      def configuredWorker: SubmissionWorker = workerWithConfig(replayConfig)
      configuredWorker.maintain().futureValue
      entries(EnsurePdf).head.status mustBe ProcessingStatus.Succeeded
      entries(EnsurePdf).head.item.pdfReplayRequestedAt mustBe None
      clock.advance(60)
      configuredWorker.maintain().futureValue
      val replay = entries(EnsurePdf).head
      replay.status mustBe ProcessingStatus.Failed
      replay.item.pdfReplayRequestedAt mustBe defined
      replay.item.operationFinished mustBe false
      entries(NotifySdes).head.status mustBe ProcessingStatus.PermanentlyFailed
      doAnswer { invocation =>
        val item = invocation.getArgument[SubmissionCommand](0)
        Future.successful(
          item.copy(
            operationFinished = true,
            data = if item.step == PackageDocumentum then item.data.copy(prepared = Some(newPackage)) else item.data
          )
        )
      }.when(mockSubmissionOperations).execute(any())(using any())
      run(EnsurePdf)
      entries(PackageDocumentum).head.status mustBe ProcessingStatus.Failed
      entries(NotifySdes).head.status mustBe ProcessingStatus.PermanentlyFailed
      run(PackageDocumentum)
      val notification = entries(NotifySdes).head
      notification.status mustBe ProcessingStatus.Failed
      notification.item.data.prepared mustBe Some(newPackage)
      notification.item.failure mustBe None
      notification.item.pdfReplayRequestedAt mustBe replay.item.pdfReplayRequestedAt
      notification.item.expiresAt mustBe replay.item.expiresAt
      run(NotifySdes)
      entries(NotifySdes).head.status mustBe ProcessingStatus.Succeeded
      // Repeated handoff after a crash must not reset already completed replay steps.
      Future.traverse(1 to 8)(_ => queues(NotifySdes).enqueue(notification.item)).futureValue
      entries(NotifySdes).size mustBe 1
      entries(NotifySdes).head.status mustBe ProcessingStatus.Succeeded
      entries(SendEmail) mustBe empty
      entries(SubmitDps) mustBe empty
      clock.advance(1.day.toSeconds)
      configuredWorker.maintain().futureValue
      entries(EnsurePdf).head.status mustBe ProcessingStatus.Succeeded
      val nextRun = workerWithConfig(replayConfig ++ Configuration("work-items.replay.run-id" -> "second-run"))
      nextRun.maintain().futureValue
      Seq(EnsurePdf, PackageDocumentum, NotifySdes).foreach(run)
      entries(NotifySdes).head.status mustBe ProcessingStatus.Succeeded
      entries(NotifySdes).head.item.replayRuns must contain allOf ("test-run", "second-run")
      verify(mockSubmissionOperations, times(9)).execute(any())(using any())
    }

    "wait for active downstream work and refuse to overwrite an active successor" in {
      val submission = command(EnsurePdf)
      seed(submission)
      run(EnsurePdf)
      val replayWorker = workerWithConfig(
        Configuration("work-items.replay.pdf-replay-orchestration-ids" -> Seq(submission.data.orchestrationId))
      )
      replayWorker.maintain().futureValue
      entries(EnsurePdf).head.item.pdfReplayRequestedAt mustBe None
      val successor = submission.copy(pdfReplayRequestedAt = Some(clock.instant())).next(PackageDocumentum)
      queues(PackageDocumentum).enqueue(successor).failed.futureValue mustBe a[IllegalStateException]
      entries(PackageDocumentum).head.item.pdfReplayRequestedAt mustBe None
      run(PackageDocumentum)
      replayWorker.maintain().futureValue
      entries(EnsurePdf).head.item.pdfReplayRequestedAt mustBe None
      run(NotifySdes)
      replayWorker.maintain().futureValue
      entries(EnsurePdf).head.item.pdfReplayRequestedAt mustBe defined
    }

    "not notify SDES when rebuilding the ZIP fails permanently" in {
      val submission = command(EnsurePdf)
      seed(submission)
      Seq(EnsurePdf, PackageDocumentum, NotifySdes).foreach(run)
      val originalNotification = entries(NotifySdes).head
      val replayWorker         = workerWithConfig(
        Configuration("work-items.replay.pdf-replay-orchestration-ids" -> Seq(submission.data.orchestrationId))
      )
      replayWorker.maintain().futureValue
      run(EnsurePdf)
      doReturn(Future.failed(SubmissionFailures.response(FORBIDDEN)))
        .when(mockSubmissionOperations)
        .execute(any())(using any())
      run(PackageDocumentum)
      entries(PackageDocumentum).head.status mustBe ProcessingStatus.PermanentlyFailed
      entries(NotifySdes).head mustBe originalNotification
      replayWorker.maintain().futureValue
      entries(EnsurePdf).head.status mustBe ProcessingStatus.Succeeded
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
        statusOf(submissionData) mustBe NO_CONTENT
      }
    }

    for kind <- SubmissionKind.values do {
      s"isolate $kind submissions for different subscriptions" in {
        val submissionData = data(kind).copy(reference = None, submittedAt = None)
        service.submit(submissionData).futureValue.header.status mustBe ACCEPTED
        statusOf(submissionData.copy(subscriptionId = "another-subscription")) mustBe NOT_FOUND
        service
          .submit(
            submissionData.copy(subscriptionId = "another-subscription", orchestrationId = UUID.randomUUID().toString)
          )
          .futureValue
          .header
          .status mustBe ACCEPTED
        entries(RetrieveSubscription).size mustBe 2
      }
    }

    "reject a changed payload submitted with an existing idempotency key" in {
      val submissionData = data()
      service.submit(submissionData).futureValue.header.status mustBe ACCEPTED
      val changed = submissionData.copy(notification =
        submissionData.notification.map(
          _.copy(remarks = Some(uk.gov.hmrc.senioraccountingofficer.models.requests.FreeText("changed")))
        )
      )
      service.submit(changed).futureValue.header.status mustBe CONFLICT
    }

    "allow notification and certificate submissions to use the same idempotency key" in {
      service.submit(data()).futureValue.header.status mustBe ACCEPTED
      service.submit(data(SubmissionKind.Certificate)).futureValue.header.status mustBe ACCEPTED
      entries(RetrieveSubscription).size mustBe 2
    }

    "accept a submission with a generated idempotency key" in {
      val generated =
        SubmissionData.notification(
          "subscription",
          "correlation",
          notification.copy(idempotencyKey = None),
          AuditTags.missing("/senior-accounting-officer/notification")
        )
      UUID.fromString(generated.idempotencyKey) mustBe a[UUID]
      service.submit(generated).futureValue.header.status mustBe ACCEPTED
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
        statusOf(submissionData) mustBe NO_CONTENT
        doReturn(Future.failed(new RuntimeException("pdf unavailable")))
          .when(mockSubmissionOperations)
          .execute(any())(using any())
        run(InitialPdf)
        statusOf(submissionData) mustBe OK
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
      statusOf(submission.data) mustBe NOT_FOUND
      run(RetrieveSubscription)
      statusOf(submission.data) mustBe NO_CONTENT
      entries(RetrieveCustomer).size mustBe 1
    }

    "wait for backoff before retrying a 408 customer lookup response" in {
      val submission = command(RetrieveCustomer).copy(data = data().copy(reference = None))
      seed(submission)
      doReturn(Future.failed(SubmissionFailures.response(REQUEST_TIMEOUT, isInitialLookup = true)))
        .when(mockSubmissionOperations)
        .execute(any())(using any())
      run(RetrieveCustomer)
      entries(RetrieveCustomer).head.status mustBe ProcessingStatus.Failed
      run(RetrieveCustomer)
      verify(mockSubmissionOperations, times(1)).execute(any())(using any())
      statusOf(submission.data) mustBe NO_CONTENT
      clock.advance(61)
      run(RetrieveCustomer)
      verify(mockSubmissionOperations, times(2)).execute(any())(using any())
    }

    "use configured retry delays and keep using the last delay" in {
      val submission = command(RetrieveCustomer).copy(data = data().copy(reference = None))
      seed(submission)
      doReturn(Future.failed(SubmissionFailures.response(REQUEST_TIMEOUT, isInitialLookup = true)))
        .when(mockSubmissionOperations)
        .execute(any())(using any())

      run(RetrieveCustomer)
      Seq(60L, 300L, 900L, 3600L, 3600L).zipWithIndex.foreach { case (delay, index) =>
        clock.advance(delay - 1)
        run(RetrieveCustomer)
        verify(mockSubmissionOperations, times(index + 1)).execute(any())(using any())
        clock.advance(2)
        run(RetrieveCustomer)
        verify(mockSubmissionOperations, times(index + 2)).execute(any())(using any())
      }
    }

    for step <- Seq(RetrieveSubscription, RetrieveCustomer, SubmitDps) do {
      s"permanently fail $step after a downstream 503 response" in {
        val submission = command(step).copy(data = data().copy(reference = None))
        seed(submission)
        doReturn(
          Future.failed(
            SubmissionFailures.response(
              SERVICE_UNAVAILABLE,
              isDpsSubmission = step == SubmitDps,
              isInitialLookup = step != SubmitDps
            )
          )
        )
          .when(mockSubmissionOperations)
          .execute(any())(using any())
        run(step)
        entries(step).head.status mustBe ProcessingStatus.PermanentlyFailed
        entries(step).head.failureCount mustBe 0
        statusOf(submission.data) mustBe BAD_GATEWAY
        Seq(RetrieveCustomer, SubmitDps, InitialPdf, Audit).filterNot(_ == step).foreach { next =>
          entries(next) mustBe empty
        }
      }
    }

    "publish a permanent customer lookup failure without submitting to DPS" in {
      val submission = command(RetrieveCustomer).copy(data = data().copy(reference = None))
      seed(submission)
      doReturn(Future.failed(SubmissionFailures.response(UNAUTHORIZED)))
        .when(mockSubmissionOperations)
        .execute(any())(using any())
      run(RetrieveCustomer)
      statusOf(submission.data) mustBe INTERNAL_SERVER_ERROR
      entries(SubmitDps) mustBe empty
    }

    "record an unknown outcome without repeating an interrupted DPS dispatch" in {
      val submission = command(SubmitDps).copy(dispatched = true)
      seed(submission)
      run(SubmitDps)
      verify(mockSubmissionOperations, never()).execute(any())(using any())
      statusOf(submission.data) mustBe BAD_GATEWAY
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
      statusOf(submission.data) mustBe BAD_GATEWAY
    }

    "allow a retry after an explicit DPS rate-limit rejection" in {
      seed(command(SubmitDps))
      doReturn(Future.failed(SubmissionFailures.response(status = TOO_MANY_REQUESTS, isDpsSubmission = true)))
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
      entries(Audit).size mustBe 1
      entries(SubmitDps).head.status mustBe ProcessingStatus.Succeeded
      states.get(submission.data.scopeKey).futureValue.value.reference mustBe Some("REF123")
    }

    "retry audit delivery independently after DPS succeeds" in {
      val submission = command(SubmitDps)
      seed(submission)
      run(SubmitDps)
      entries(Audit).size mustBe 1
      doReturn(Future.failed(new RuntimeException("audit unavailable")))
        .when(mockSubmissionOperations)
        .execute(any())(using any())
      run(Audit)
      entries(Audit).head.status mustBe ProcessingStatus.Failed
      entries(InitialPdf).size mustBe 1
      entries(SubmitDps).head.status mustBe ProcessingStatus.Succeeded
      clock.advance(61)
      reset(mockSubmissionOperations)
      when(mockSubmissionOperations.successors(any())).thenCallRealMethod()
      when(mockSubmissionOperations.execute(any())(using any())).thenAnswer { invocation =>
        Future.successful(invocation.getArgument[SubmissionCommand](0).copy(operationFinished = true))
      }
      run(Audit)
      entries(Audit).head.status mustBe ProcessingStatus.Succeeded
      entries(SubmitDps).head.status mustBe ProcessingStatus.Succeeded
    }

    "complete an interrupted initial PDF attempt without regenerating it" in {
      val submission = command(InitialPdf).copy(dispatched = true)
      seed(submission)
      run(InitialPdf)
      verify(mockSubmissionOperations, never()).execute(any())(using any())
      statusOf(submission.data) mustBe OK
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
        doReturn(Future.failed(SubmissionFailures.response(SERVICE_UNAVAILABLE)))
          .when(mockSubmissionOperations)
          .execute(any())(using any())
        run(step)
        entries(step).head.status mustBe ProcessingStatus.Failed
        statusOf(submission.data) mustBe OK
        states.get(submission.data.scopeKey).futureValue.value mustBe publicOutcome
        clock.advance(61)
        doReturn(Future.failed(SubmissionFailures.response(BAD_REQUEST)))
          .when(mockSubmissionOperations)
          .execute(any())(using any())
        run(step)
        entries(step).head.status mustBe ProcessingStatus.PermanentlyFailed
        entries(step).head.item.failure mustBe Some(SubmissionFailures.response(BAD_REQUEST).failure)
        statusOf(submission.data) mustBe OK
        states.get(submission.data.scopeKey).futureValue.value mustBe publicOutcome
      }
    }
  }

  "SubmissionWorker.maintain" must {
    "expire successful submissions after seven days" in {
      val root =
        SubmissionCommand.start(data().copy(pdfAttempted = true, pdfStored = true)).copy(operationFinished = true)
      doReturn(Seq.empty[SubmissionCommand]).when(mockSubmissionOperations).successors(any())
      seed(root)
      run(RetrieveSubscription)
      worker.maintain().futureValue
      statusOf(root.data) mustBe OK
      clock.advance(retentionSeconds + 1)
      statusOf(root.data) mustBe NOT_FOUND
    }

    "expire failed submissions and allow key reuse after thirty days" in {
      val root =
        SubmissionCommand
          .start(data())
          .copy(operationFinished = true, failure = Some(SubmissionFailures.unknownDpsOutcome))
      seed(root)
      run(RetrieveSubscription)
      worker.maintain().futureValue
      statusOf(root.data) mustBe BAD_GATEWAY
      val before = states.get(root.data.scopeKey).futureValue.value.expiresAt
      service.status(root.data.subscriptionId, root.data.kind, root.data.idempotencyKey).futureValue
      states.get(root.data.scopeKey).futureValue.value.expiresAt mustBe before
      clock.advance(30.days.toSeconds + 1)
      statusOf(root.data) mustBe NOT_FOUND
      val replacement = data()
      service.submit(replacement).futureValue.header.status mustBe ACCEPTED
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
