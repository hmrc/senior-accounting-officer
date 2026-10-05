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

import org.apache.pekko.actor.ActorSystem
import play.api.{Configuration, Logging}
import play.api.inject.ApplicationLifecycle
import uk.gov.hmrc.senioraccountingofficer.models.submission.SubmissionStep
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.{Inject, Provider, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration.*
import scala.util.control.NonFatal

@Singleton
class SubmissionPoller @Inject() (
    config: Configuration,
    system: ActorSystem,
    worker: Provider[SubmissionWorker],
    lifecycle: ApplicationLifecycle
)(using ExecutionContext)
    extends Logging {
  private val stopped = new AtomicBoolean(false)
  private val tasks   = if config.get[Boolean]("work-items.enabled") then {
    def schedule(interval: FiniteDuration)(operation: => Future[Unit]) = {
      val busy = new AtomicBoolean(false)
      system.scheduler.scheduleWithFixedDelay(5.seconds, interval) { () =>
        if !stopped.get() && busy.compareAndSet(false, true) then
          Future.unit
            .flatMap(_ => operation)
            .recover { case NonFatal(error) => logger.error("[Submission][PollerFailed]", error) }
            .andThen { case _ => busy.set(false) }
        ()
      }
    }
    SubmissionStep.values.toSeq.map(step =>
      schedule(config.get[Long]("work-items.poll-interval-seconds").seconds)(worker.get().processNext(step))
    ) :+
      schedule(1.minute)(worker.get().maintain())
  } else Seq.empty
  lifecycle.addStopHook { () => stopped.set(true); tasks.foreach(_.cancel()); Future.unit }
}
