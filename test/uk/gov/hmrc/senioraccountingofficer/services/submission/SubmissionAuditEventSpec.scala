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

import org.scalatest.OptionValues
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import play.api.libs.json.{JsArray, JsObject, Json}
import uk.gov.hmrc.senioraccountingofficer.models.requests.*
import uk.gov.hmrc.senioraccountingofficer.models.submission.*

import java.time.Instant

import SubmissionTestData.*

class SubmissionAuditEventSpec extends AnyFreeSpec with Matchers with OptionValues {
  private val at           = Instant.parse("2026-10-15T09:00:00Z")
  private val capturedTags =
    AuditTags(
      requestId = "request",
      sessionId = "session",
      deviceId = "device",
      clientIp = "127.0.0.1",
      clientPort = "1234",
      path = "/senior-accounting-officer/v2/notification"
    )

  "notification audit preserves its CIP detail and request tags" in {
    val submission = data().copy(auditTags = capturedTags, auditGeneratedAt = Some(at))
    val event      = SubmissionAuditEvent(
      data = submission,
      now = at.plusSeconds(60)
    )
    event.auditSource mustBe "senior-accounting-officer"
    event.auditType mustBe "NotificationSubmitted"
    event.eventId mustBe submission.orchestrationId
    event.generatedAt mustBe at
    event.tags("deviceID") mustBe "device"
    event.tags("path") mustBe capturedTags.path
    val detail = event.detail.as[JsObject]
    detail.value("notificationReference") mustBe Json.toJson("REF123")
    detail.value("customerId") mustBe Json.toJson("customer-number")
    val company = detail.value("companies").as[JsArray].value.head.as[JsObject]
    company.value("accountingPeriodEnd") mustBe Json.toJson(date.toString)
    company.keys must not contain "accPeriodEnd"
    detail.value("seniorAccountingOfficer").as[JsArray].value must have size 1
  }

  "certificate audit includes every qualification flag and omits missing optional values" in {
    val event = SubmissionAuditEvent(data = data(SubmissionKind.Certificate), now = at)
    event.auditType mustBe "CertificateSubmitted"
    val detail = event.detail.as[JsObject]
    detail.value("seniorAccountingOfficerName") mustBe Json.toJson("Jane Smith")
    detail.keys must not contain "staffPid"
    detail.keys must not contain "submitterName"
    val company = detail.value("companies").as[JsArray].value.head.as[JsObject]
    company.value("accountingPeriodEnd") mustBe Json.toJson(date.toString)
    company.value("isVatQualified") mustBe Json.toJson(false)
    company.value("isQualificationStatementPresent") mustBe Json.toJson(false)
    company.keys must not contain "qualificationStatement"
    company.keys.count(_.startsWith("is")) mustBe 11
    event.tags("deviceID") mustBe "-"
  }

  "certificate audit records qualification statement presence without its text" in {
    val company = certificate.companies.value.head.copy(
      isVatQualified = true,
      qualificationStatement = Some(FreeText("Private qualification detail"))
    )
    val submission = data(SubmissionKind.Certificate).copy(
      certificate = Some(certificate.copy(companies = CertificateCompanies(List(company))))
    )
    val detail         = SubmissionAuditEvent(data = submission, now = at).detail.as[JsObject]
    val auditedCompany = detail.value("companies").as[JsArray].value.head.as[JsObject]
    auditedCompany.value("isQualificationStatementPresent") mustBe Json.toJson(true)
    auditedCompany.keys must not contain "qualificationStatement"
  }

  "older queued submissions remain readable without audit fields" in {
    for kind <- Seq(SubmissionKind.Notification, SubmissionKind.Certificate) do {
      val original = data(kind)
      val legacy   = Json.toJson(original).as[JsObject] - "auditTags" - "auditGeneratedAt"
      val restored = legacy.as[SubmissionData]
      restored.auditTags mustBe original.auditTags
      restored.auditGeneratedAt mustBe None
    }
  }

  "new queued submissions preserve their request tags" in {
    val submission = data().copy(auditTags = capturedTags)
    Json.toJson(submission).as[SubmissionData].auditTags mustBe capturedTags
  }

  "legacy and queued events use the same notification and certificate detail" in {
    val notificationData   = data()
    val directNotification = SubmissionAuditEvent.notification(
      subscriptionId = notificationData.subscriptionId,
      request = notificationData.notification.value,
      reference = notificationData.reference.value,
      customerId = notificationData.customerNumber,
      tags = capturedTags,
      now = at
    )
    directNotification.detail mustBe SubmissionAuditEvent(data = notificationData, now = at).detail

    val certificateData   = data(SubmissionKind.Certificate)
    val directCertificate = SubmissionAuditEvent.certificate(
      subscriptionId = certificateData.subscriptionId,
      request = certificateData.certificate.value,
      reference = certificateData.reference.value,
      customerId = certificateData.customerNumber,
      tags = capturedTags,
      now = at
    )
    directCertificate.detail mustBe SubmissionAuditEvent(data = certificateData, now = at).detail
  }
}
