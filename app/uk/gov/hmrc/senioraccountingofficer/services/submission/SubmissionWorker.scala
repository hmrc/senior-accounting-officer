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

import com.codahale.metrics.{Gauge, MetricRegistry}
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.pattern.after
import play.api.{Configuration, Logging}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.mongo.workitem.WorkItem
import uk.gov.hmrc.senioraccountingofficer.models.submission.*
import uk.gov.hmrc.senioraccountingofficer.models.submission.SubmissionStep.*
import uk.gov.hmrc.senioraccountingofficer.repositories.submission.*

import scala.collection.concurrent.TrieMap
import scala.concurrent.duration.*
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

import java.time.Clock
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.{Inject, Singleton}

@Singleton
class SubmissionWorker @Inject() (
    queues: SubmissionQueues,
    states: SubmissionStateRepository,
    operations: SubmissionOperations,
    config: Configuration,
    actorSystem: ActorSystem,
    metrics: MetricRegistry,
    clock: Clock
)(using ExecutionContext)
    extends Logging {

  private val queueCounts = TrieMap.empty[String, AtomicInteger]
  private val timeout     = config.get[Long]("work-items.operation-timeout-seconds").seconds
  private val lease       = config.get[Long]("work-items.lease-seconds")
  require(timeout.toSeconds < lease, "Operation timeout must be shorter than the work item lease")

  def processNext(step: SubmissionStep): Future[Unit] = {
    val repository = queues(step)
    repository.claim().flatMap {
      case Some(work) => process(repository, work)
      case None       => Future.unit
    }
  }

  private def process(repository: SubmissionWorkRepository, work: WorkItem[SubmissionCommand]): Future[Unit] = {
    given HeaderCarrier   = HeaderCarrier(extraHeaders = Seq("correlationId" -> work.item.data.correlationId))
    val heartbeatInterval = (lease / 3).seconds
    val heartbeat         = actorSystem.scheduler.scheduleWithFixedDelay(heartbeatInterval, heartbeatInterval) { () =>
      repository.heartbeat(work).recover { case NonFatal(_) => () }
      ()
    }
    val processing = for {
      _        <- if work.item.step == RetrieveSubscription then states.initialize(work.item.data) else Future.unit
      finished <- if work.item.operationFinished then Future.successful(work) else perform(repository, work)
      _        <- repository.heartbeat(finished)
      _        <- publish(finished.item)
      _        <- Future.traverse(operations.successors(finished.item)) { next =>
        for {
          _ <- repository.heartbeat(finished)
          _ <- queues(next.step).enqueue(next)
        } yield ()
      }
      _ <- repository.finish(finished)
    } yield recordCompletion(finished.item)

    processing
      .recoverWith { case NonFatal(error) => retry(repository, work, error) }
      .andThen { case _ => heartbeat.cancel() }
  }

  // Only submission steps publish public status, email and document delivery failures stay on their work items.
  private def publish(command: SubmissionCommand): Future[Unit] = command.step match {
    case RetrieveSubscription | RetrieveCustomer | SubmitDps | InitialPdf => states.publish(command)
    case _                                                                => Future.unit
  }

  private def recordCompletion(command: SubmissionCommand): Unit = {
    val metric = if command.failure.isDefined then "permanent-failure" else "completed"
    metrics.counter(s"submission.${command.step}.$metric").inc()
    if command.failure.exists(_.dpsOutcomeUnknown) then metrics.counter("submission.dps.outcome-unknown").inc()
    val message =
      s"[Submission][${command.step}][$metric][OrchestrationId=${command.data.orchestrationId}][CorrelationId=${command.data.correlationId}]"
    command.failure match {
      case Some(failure) =>
        logger.warn(s"$message[HttpStatus=${failure.httpStatus}][DpsOutcomeUnknown=${failure.dpsOutcomeUnknown}]")
      case None => logger.info(message)
    }
  }

  private def retry(
      repository: SubmissionWorkRepository,
      work: WorkItem[SubmissionCommand],
      error: Throwable
  ): Future[Unit] = {
    val command = work.item
    metrics.counter(s"submission.${command.step}.retry").inc()
    logger.warn(
      s"[Submission][${command.step}][Retry][OrchestrationId=${command.data.orchestrationId}][CorrelationId=${command.data.correlationId}]",
      error
    )
    val base  = config.get[Long]("work-items.retry-base-seconds")
    val cap   = config.get[Long]("work-items.retry-max-seconds")
    val delay = math.min(cap.toDouble, base.toDouble * math.pow(2, math.min(work.failureCount, 20))).toLong
    repository.retry(work, delay, error.getClass.getSimpleName)
  }

  private def failedPdf(command: SubmissionCommand): SubmissionCommand =
    command.copy(operationFinished = true, data = command.data.copy(pdfAttempted = true, pdfStored = false))

  private def withTimeout[A](operation: => Future[A]): Future[A] =
    Future.firstCompletedOf(
      Seq(
        Future.unit.flatMap(_ => operation),
        after(timeout, actorSystem.scheduler)(Future.failed(new TimeoutException("Submission operation timed out")))
      )
    )

  private def perform(repository: SubmissionWorkRepository, work: WorkItem[SubmissionCommand])(using
      HeaderCarrier
  ): Future[WorkItem[SubmissionCommand]] = {
    val command = work.item
    (command.step, command.dispatched) match {
      case (SubmitDps, true) =>
        repository.save(
          work,
          command.copy(operationFinished = true, failure = Some(SubmissionFailures.unknownDpsOutcome))
        )
      case (InitialPdf, true) => repository.save(work, failedPdf(command))
      case _                  =>
        val marked = command.step match {
          case SubmitDps | InitialPdf => repository.save(work, command.copy(dispatched = true))
          case _                      => Future.successful(work)
        }
        for {
          claimed <- marked
          result  <- withTimeout(operations.execute(claimed.item)).recoverWith {
            case NonFatal(_) if command.step == InitialPdf => Future.successful(failedPdf(claimed.item))
            case rejection: OperationRejected if rejection.retryable && command.step == SubmitDps =>
              repository.save(claimed, claimed.item.copy(dispatched = false)).flatMap(_ => Future.failed(rejection))
            case rejection: OperationRejected if !rejection.retryable =>
              Future.successful(claimed.item.copy(operationFinished = true, failure = Some(rejection.failure)))
            case NonFatal(_) if command.step == SubmitDps =>
              Future.successful(
                claimed.item.copy(operationFinished = true, failure = Some(SubmissionFailures.unknownDpsOutcome))
              )
          }
          saved <- repository.save(claimed, result)
        } yield saved
    }
  }

  private val retentionOrder = List(
    RetrieveSubscription,
    RetrieveCustomer,
    SubmitDps,
    InitialPdf,
    SendEmail,
    EnsurePdf,
    PackageDocumentum,
    NotifySdes
  )

  private def hasActiveGraph(orchestrationId: String): Future[Boolean] = {
    def loop(remaining: List[SubmissionStep]): Future[Boolean] = remaining match {
      case Nil          => Future.successful(false)
      case step :: rest =>
        queues(step).hasActive(orchestrationId).flatMap {
          case true  => Future.successful(true)
          case false => loop(rest)
        }
    }
    loop(retentionOrder)
  }

  def maintain(): Future[Unit] = for {
    _          <- updateQueueMetrics()
    candidates <- queues(RetrieveSubscription).retentionCandidates()
    _          <- Future.traverse(candidates) { work =>
      val orchestrationId = work.item.data.orchestrationId
      hasActiveGraph(orchestrationId).flatMap {
        case true  => Future.unit
        case false => expireGraph(orchestrationId)
      }
    }
  } yield ()

  private def updateQueueMetrics(): Future[Unit] =
    Future
      .traverse(queues.all.values.toSeq) { repository =>
        repository.metrics.map(_.foreach { (name, count) =>
          val value = queueCounts.getOrElseUpdate(
            name, {
              val counter = new AtomicInteger()
              metrics.register(s"submission.queue.$name", new Gauge[Int] { def getValue: Int = counter.get() })
              counter
            }
          )
          value.set(count)
        })
      }
      .map(_ => ())

  private def expireGraph(orchestrationId: String): Future[Unit] = {
    val root    = queues(RetrieveSubscription)
    val expires = clock.instant().plusSeconds(config.get[Long]("work-items.retention-seconds"))
    for {
      persistedExpiry <- states.expire(orchestrationId, expires)
      _ <- Future.traverse(queues.all.values.filterNot(_ == root).toSeq)(_.expire(orchestrationId, persistedExpiry))
      _ <- root.expire(orchestrationId, persistedExpiry)
    } yield ()
  }
}
