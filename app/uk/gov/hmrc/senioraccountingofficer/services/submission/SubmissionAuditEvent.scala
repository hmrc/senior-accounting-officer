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

import play.api.libs.json.{JsObject, Json}
import uk.gov.hmrc.play.audit.model.ExtendedDataEvent
import uk.gov.hmrc.senioraccountingofficer.models.requests.{CertificateRequest, NotificationRequest}
import uk.gov.hmrc.senioraccountingofficer.models.submission.*

import java.time.{Instant, ZoneId}
import java.util.UUID

object SubmissionAuditEvent {
  def notification(
      subscriptionId: String,
      request: NotificationRequest,
      reference: String,
      customerId: Option[String],
      tags: AuditTags,
      now: Instant
  ): ExtendedDataEvent = event(
    auditType = "NotificationSubmitted",
    eventId = UUID.randomUUID().toString,
    generatedAt = now,
    detail = notificationDetail(
      subscriptionId = subscriptionId,
      request = request,
      reference = reference,
      customerId = customerId
    ),
    tags = tags
  )

  def certificate(
      subscriptionId: String,
      request: CertificateRequest,
      reference: String,
      customerId: Option[String],
      tags: AuditTags,
      now: Instant
  ): ExtendedDataEvent = event(
    auditType = "CertificateSubmitted",
    eventId = UUID.randomUUID().toString,
    generatedAt = now,
    detail = certificateDetail(
      subscriptionId = subscriptionId,
      request = request,
      reference = reference,
      customerId = customerId
    ),
    tags = tags
  )

  def apply(data: SubmissionData, now: Instant): ExtendedDataEvent = {
    val reference =
      data.reference.getOrElse(throw new IllegalStateException("Missing required submission field: reference"))
    val (auditType, path, detail) = data.kind match {
      case SubmissionKind.Notification =>
        (
          "NotificationSubmitted",
          "/senior-accounting-officer/notification",
          notificationDetail(
            subscriptionId = data.subscriptionId,
            request = data.notification.getOrElse(throw new IllegalStateException("Missing notification")),
            reference = reference,
            customerId = data.customerNumber
          )
        )
      case SubmissionKind.Certificate =>
        (
          "CertificateSubmitted",
          "/senior-accounting-officer/certificate",
          certificateDetail(
            subscriptionId = data.subscriptionId,
            request = data.certificate.getOrElse(throw new IllegalStateException("Missing certificate")),
            reference = reference,
            customerId = data.customerNumber
          )
        )
    }
    event(
      auditType = auditType,
      eventId = data.orchestrationId,
      generatedAt = data.auditGeneratedAt
        .orElse(data.submittedAt.map(_.atZone(ZoneId.of("Europe/London")).toInstant))
        .getOrElse(now),
      detail = detail,
      tags = data.auditTags.getOrElse(AuditTags.missing(path = path))
    )
  }

  private def event(
      auditType: String,
      eventId: String,
      generatedAt: Instant,
      detail: JsObject,
      tags: AuditTags
  ): ExtendedDataEvent = ExtendedDataEvent(
    auditProvider = Some("mdtp"),
    auditSource = "senior-accounting-officer",
    auditType = auditType,
    eventId = eventId,
    generatedAt = generatedAt,
    detail = detail,
    tags = tags.asMap
  )

  private def notificationDetail(
      subscriptionId: String,
      request: NotificationRequest,
      reference: String,
      customerId: Option[String]
  ): JsObject =
    Json.obj(
      "etmpSubscriptionId"    -> subscriptionId,
      "notificationReference" -> reference,
      "companies"             -> request.companies.value.map(c =>
        Json.obj(
          "accPeriodEnd" -> c.accPeriodEnd.toString,
          "utr"          -> c.utr.value,
          "name"         -> c.name.value,
          "status"       -> c.status.toString.toLowerCase,
          "type"         -> c.`type`.toString
        ) ++ c.crn.fold(Json.obj())(v => Json.obj("crn" -> v.value))
      ),
      "seniorAccountingOfficer" -> request.saos.value.map(s =>
        Json.obj("name" -> s.name.value) ++
          s.fromDate.fold(Json.obj())(v => Json.obj("responsibleFromDate" -> v.toString)) ++
          s.toDate.fold(Json.obj())(v => Json.obj("responsibleToDate" -> v.toString))
      )
    ) ++ customerId.fold(Json.obj())(v => Json.obj("customerId" -> v)) ++
      request.remarks.fold(Json.obj())(v => Json.obj("customerRemarks" -> v.value))

  private def certificateDetail(
      subscriptionId: String,
      request: CertificateRequest,
      reference: String,
      customerId: Option[String]
  ): JsObject =
    Json.obj(
      "etmpSubscriptionId"           -> subscriptionId,
      "certificateReference"         -> reference,
      "seniorAccountingOfficerName"  -> request.saoName.value,
      "seniorAccountingOfficerEmail" -> request.saoEmail.value,
      "companies"                    -> request.companies.value.map(c =>
        Json.obj(
          "accountingPeriodEnd"            -> c.accPeriodEnd.toString,
          "utr"                            -> c.utr.value,
          "name"                           -> c.name.value,
          "status"                         -> c.status.toString.toLowerCase,
          "type"                           -> c.`type`.toString,
          "isBankLevyQualified"            -> c.isBankLevyQualified,
          "isCorporationTaxQualified"      -> c.isCorporationTaxQualified,
          "isCustomsDutiesQualified"       -> c.isCustomsDutiesQualified,
          "isExciseDutiesQualified"        -> c.isExciseDutiesQualified,
          "isInsurancePremiumTaxQualified" -> c.isInsurancePremiumTaxQualified,
          "isPayeQualified"                -> c.isPayeQualified,
          "isPetroleumRevenueTaxQualified" -> c.isPetroleumRevenueTaxQualified,
          "isStampDutyLandTaxQualified"    -> c.isStampDutyLandTaxQualified,
          "isStampDutyReserveTaxQualified" -> c.isStampDutyReserveTaxQualified,
          "isVatQualified"                 -> c.isVatQualified
        ) ++ c.crn.fold(Json.obj())(v => Json.obj("crn" -> v.value)) ++
          c.qualificationStatement.fold(Json.obj())(v => Json.obj("qualificationStatement" -> v.value))
      )
    ) ++ customerId.fold(Json.obj())(v => Json.obj("customerId" -> v)) ++
      request.staffPid.fold(Json.obj())(v => Json.obj("staffPid" -> v.value)) ++
      request.submitterName.fold(Json.obj())(v => Json.obj("submitterName" -> v.value)) ++
      request.remarks.fold(Json.obj())(v => Json.obj("customerRemarks" -> v.value))
}
