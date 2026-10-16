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

import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.audit.http.connector.{AuditConnector, AuditResult}
import uk.gov.hmrc.play.audit.model.ExtendedDataEvent
import uk.gov.hmrc.senioraccountingofficer.models.requests.{CertificateRequest, NotificationRequest}
import uk.gov.hmrc.senioraccountingofficer.models.submission.{AuditTags, SubmissionData}

import scala.concurrent.{ExecutionContext, Future}

import java.time.Clock
import javax.inject.Inject

class SubmissionAuditSender @Inject() (connector: AuditConnector, clock: Clock)(using ExecutionContext) {
  def notification(
      subscriptionId: String,
      request: NotificationRequest,
      reference: String,
      customerId: Option[String],
      tags: AuditTags
  )(using HeaderCarrier): Future[Unit] =
    deliver(
      SubmissionAuditEvent.notification(
        subscriptionId = subscriptionId,
        request = request,
        reference = reference,
        customerId = customerId,
        tags = tags,
        now = clock.instant()
      )
    )

  def certificate(
      subscriptionId: String,
      request: CertificateRequest,
      reference: String,
      customerId: Option[String],
      tags: AuditTags
  )(using HeaderCarrier): Future[Unit] =
    deliver(
      SubmissionAuditEvent.certificate(
        subscriptionId = subscriptionId,
        request = request,
        reference = reference,
        customerId = customerId,
        tags = tags,
        now = clock.instant()
      )
    )

  def send(data: SubmissionData)(using HeaderCarrier): Future[Unit] =
    deliver(SubmissionAuditEvent(data = data, now = clock.instant()))

  private def deliver(event: ExtendedDataEvent)(using HeaderCarrier): Future[Unit] =
    connector.sendExtendedEvent(event).flatMap {
      case AuditResult.Success | AuditResult.Disabled => Future.unit
      case _: AuditResult.Failure => Future.failed(new IllegalStateException("Submission audit delivery failed"))
    }
}
