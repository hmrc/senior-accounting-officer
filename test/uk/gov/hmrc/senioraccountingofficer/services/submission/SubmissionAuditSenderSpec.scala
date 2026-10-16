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

import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.*
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatestplus.mockito.MockitoSugar
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.audit.http.connector.{AuditConnector, AuditResult}
import uk.gov.hmrc.play.audit.model.ExtendedDataEvent
import uk.gov.hmrc.senioraccountingofficer.models.submission.AuditTags

import scala.concurrent.{ExecutionContext, Future}

import java.time.{Clock, Instant, ZoneOffset}

import SubmissionTestData.*

class SubmissionAuditSenderSpec extends AnyFreeSpec with Matchers with ScalaFutures with MockitoSugar {
  given ExecutionContext = ExecutionContext.global
  given HeaderCarrier    = HeaderCarrier()

  private val clock     = Clock.fixed(Instant.parse("2026-10-15T09:00:00Z"), ZoneOffset.UTC)
  private val auditTags = AuditTags.missing(path = "/senior-accounting-officer/notification")

  "send a legacy notification directly through the audit connector" in {
    val connector = mock[AuditConnector]
    val sender    = new SubmissionAuditSender(connector, clock)
    when(connector.sendExtendedEvent(any())(using any(), any()))
      .thenReturn(Future.successful(AuditResult.Success))

    sender
      .notification(
        subscriptionId = "subscription",
        request = notification,
        reference = "REF123",
        customerId = Some("customer"),
        tags = auditTags
      )
      .futureValue

    val event = ArgumentCaptor.forClass(classOf[ExtendedDataEvent])
    verify(connector).sendExtendedEvent(event.capture())(using any(), any())
    event.getValue.auditType mustBe "NotificationSubmitted"
    event.getValue.detail.as[play.api.libs.json.JsObject].value("notificationReference") mustBe
      play.api.libs.json.Json.toJson("REF123")
  }

  "surface an audit delivery failure in the legacy request flow" in {
    val connector = mock[AuditConnector]
    val sender    = new SubmissionAuditSender(connector, clock)
    when(connector.sendExtendedEvent(any())(using any(), any()))
      .thenReturn(Future.successful(AuditResult.Failure("unavailable")))

    sender
      .certificate(
        subscriptionId = "subscription",
        request = certificate,
        reference = "REF123",
        customerId = None,
        tags = auditTags
      )
      .failed
      .futureValue
      .getMessage mustBe "Submission audit delivery failed"
  }
}
