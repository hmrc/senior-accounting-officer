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
import org.apache.pekko.pattern.after
import play.api.{Configuration, Logging}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.mongo.workitem.WorkItem
import uk.gov.hmrc.senioraccountingofficer.models.submission.*
import uk.gov.hmrc.senioraccountingofficer.models.submission.SubmissionStep.*
import uk.gov.hmrc.senioraccountingofficer.repositories.submission.*
import java.time.Clock
import java.util.concurrent.TimeoutException
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration.*
import scala.util.control.NonFatal

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
  private val queueCounts = scala.collection.concurrent.TrieMap.empty[String, java.util.concurrent.atomic.AtomicInteger]
  private val timeout     = config.get[Long]("work-items.operation-timeout-seconds").seconds
  private val lease       = config.get[Long]("work-items.lease-seconds")
  require(timeout.toSeconds < lease, "Operation timeout must be shorter than the work item lease")

  def processNext(step: SubmissionStep): Future[Unit] = {
    val repository = queues(step)
    repository.claim().flatMap {
      case None       => Future.unit
      case Some(work) =>
        given HeaderCarrier = HeaderCarrier(extraHeaders = Seq("correlationId" -> work.item.data.correlationId))
        val heartbeat = actorSystem.scheduler.scheduleWithFixedDelay((lease / 3).seconds, (lease / 3).seconds) { () =>
          repository.heartbeat(work).recover { case NonFatal(_) => () }; ()
        }
        val processing = for {
          _        <- if step == RetrieveSubscription then states.initialize(work.item.data) else Future.unit
          finished <- if work.item.operationFinished then Future.successful(work) else perform(repository, work)
          _        <- repository.heartbeat(finished)
          // Results are durable before status publication or successor creation.
          _ <-
            if Set(RetrieveSubscription, RetrieveCustomer, SubmitDps, InitialPdf).contains(step) then
              states.publish(finished.item)
            else Future.unit
          _ <- Future.traverse(operations.successors(finished.item))(next =>
            repository.heartbeat(finished).flatMap(_ => queues(next.step).enqueue(next))
          )
          _ <- repository.finish(finished)
        } yield {
          val metric = if finished.item.failure.isDefined then "permanent-failure" else "completed"
          metrics.counter(s"submission.$step.$metric").inc()
          if finished.item.failure.exists(_.ambiguous) then metrics.counter("submission.dps.ambiguous").inc()
          logger.info(
            s"[Submission][$step][$metric][OrchestrationId=${work.item.data.orchestrationId}][CorrelationId=${work.item.data.correlationId}]"
          )
        }
        processing
          .recoverWith { case NonFatal(error) =>
            metrics.counter(s"submission.$step.retry").inc()
            logger.warn(
              s"[Submission][$step][Retry][OrchestrationId=${work.item.data.orchestrationId}][CorrelationId=${work.item.data.correlationId}]",
              error
            )
            val base  = config.get[Long]("work-items.retry-base-seconds")
            val cap   = config.get[Long]("work-items.retry-max-seconds")
            val delay = math.min(cap.toDouble, base.toDouble * math.pow(2, math.min(work.failureCount, 20))).toLong
            repository.retry(work, delay, error.getClass.getSimpleName)
          }
          .andThen { case _ => heartbeat.cancel() }
    }
  }

  private def perform(repository: SubmissionWorkRepository, work: WorkItem[SubmissionCommand])(using
      HeaderCarrier
  ): Future[WorkItem[SubmissionCommand]] = {
    val command = work.item
    if command.dispatched && command.step == SubmitDps then
      repository.save(work, command.copy(operationFinished = true, failure = Some(SubmissionFailures.ambiguous)))
    else if command.dispatched && command.step == InitialPdf then
      repository.save(
        work,
        command.copy(operationFinished = true, data = command.data.copy(pdfAttempted = true, pdfStored = false))
      )
    else {
      val marked =
        if command.step == SubmitDps || command.step == InitialPdf then
          repository.save(work, command.copy(dispatched = true))
        else Future.successful(work)
      marked.flatMap { claimed =>
        val attempt = Future.unit.flatMap(_ => operations.execute(claimed.item))
        Future
          .firstCompletedOf(
            Seq(
              attempt,
              after(timeout, actorSystem.scheduler)(
                Future.failed(new TimeoutException("Submission operation timed out"))
              )
            )
          )
          .recoverWith {
            case NonFatal(_) if command.step == InitialPdf =>
              Future.successful(
                claimed.item
                  .copy(operationFinished = true, data = command.data.copy(pdfAttempted = true, pdfStored = false))
              )
            case rejection: OperationRejected if rejection.retriable && command.step == SubmitDps =>
              repository.save(claimed, claimed.item.copy(dispatched = false)).flatMap(_ => Future.failed(rejection))
            case rejection: OperationRejected if !rejection.retriable =>
              Future.successful(claimed.item.copy(operationFinished = true, failure = Some(rejection.failure)))
            case NonFatal(_) if command.step == SubmitDps =>
              Future.successful(
                claimed.item.copy(operationFinished = true, failure = Some(SubmissionFailures.ambiguous))
              )
          }
          .flatMap(result => repository.save(claimed, result))
      }
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

  private def hasActiveGraph(orchestrationId: String): Future[Boolean] =
    retentionOrder.foldLeft(Future.successful(false)) { (previous, step) =>
      previous.flatMap { active =>
        if active then Future.successful(true)
        else queues(step).hasActive(orchestrationId)
      }
    }

  // A parent only finishes after its successors are persisted. Read parents before successors,
  // awaiting each result, so a handoff cannot move active work behind the retention scan.
  def maintain(): Future[Unit] = {
    val root = queues(RetrieveSubscription)
    for {
      _ <- Future.traverse(queues.all.values.toSeq)(repo =>
        repo.metrics.map(_.foreach { (name, count) =>
          val value = queueCounts.getOrElseUpdate(
            name, {
              val counter = new java.util.concurrent.atomic.AtomicInteger()
              metrics.register(
                s"submission.queue.$name",
                new com.codahale.metrics.Gauge[Int] { def getValue: Int = counter.get() }
              )
              counter
            }
          )
          value.set(count)
        })
      )
      candidates <- root.retentionCandidates()
      _          <- Future.traverse(candidates) { work =>
        hasActiveGraph(work.item.data.orchestrationId).flatMap { active =>
          if active then Future.unit
          else {
            val expires = clock.instant().plusSeconds(config.get[Long]("work-items.retention-seconds"))
            for {
              persistedExpiry <- states.expire(work.item.data.orchestrationId, expires)
              _               <- Future.traverse(queues.all.values.filterNot(_ == root).toSeq)(
                _.expire(work.item.data.orchestrationId, persistedExpiry)
              )
              _ <- root.expire(work.item.data.orchestrationId, persistedExpiry)
            } yield ()
          }
        }
      }
    } yield ()
  }
}
