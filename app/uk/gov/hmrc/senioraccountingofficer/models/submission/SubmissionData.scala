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

package uk.gov.hmrc.senioraccountingofficer.models.submission

import play.api.libs.json.*
import uk.gov.hmrc.senioraccountingofficer.models.documentum.PreparedSdesSubmission
import uk.gov.hmrc.senioraccountingofficer.models.dps.GetSubscriptionDpsResponse
import uk.gov.hmrc.senioraccountingofficer.models.requests.{CertificateRequest, NotificationRequest}

import java.nio.charset.StandardCharsets.UTF_8
import java.security.MessageDigest
import java.time.{Instant, LocalDateTime}
import java.util.UUID

final case class SubmissionData(
    orchestrationId: String,
    subscriptionId: String,
    kind: SubmissionKind,
    idempotencyKey: String,
    correlationId: String,
    notification: Option[NotificationRequest],
    certificate: Option[CertificateRequest],
    subscription: Option[GetSubscriptionDpsResponse] = None,
    customerNumber: Option[String] = None,
    reference: Option[String] = None,
    submittedAt: Option[LocalDateTime] = None,
    pdfAttempted: Boolean = false,
    pdfStored: Boolean = false,
    prepared: Option[PreparedSdesSubmission] = None,
    auditTags: AuditTags,
    auditGeneratedAt: Option[Instant] = None
) {

  def scopeKey: String = SubmissionData.scopeKey(subscriptionId, kind, idempotencyKey)

  def payload: JsValue = kind match {
    case SubmissionKind.Notification =>
      Json.toJson(
        notification
          .getOrElse(throw new IllegalStateException("Missing required submission field: notification"))
          .copy(idempotencyKey = None)
      )
    case SubmissionKind.Certificate =>
      Json.toJson(
        certificate
          .getOrElse(throw new IllegalStateException("Missing required submission field: certificate"))
          .copy(idempotencyKey = None)
      )
  }
}
object SubmissionData {

  given Format[Instant]         = uk.gov.hmrc.mongo.play.json.formats.MongoJavatimeFormats.instantFormat
  given OFormat[SubmissionData] = new OFormat[SubmissionData] {
    private val underlying = Json.format[SubmissionData]

    override def reads(json: JsValue): JsResult[SubmissionData] = {
      val normalized = json match {
        case obj: JsObject if !obj.keys.contains("auditTags") =>
          obj.value.get("kind") match {
            case Some(kind) =>
              kind.validate[SubmissionKind].map { submissionKind =>
                val path = submissionKind match {
                  case SubmissionKind.Notification => "/senior-accounting-officer/notification"
                  case SubmissionKind.Certificate  => "/senior-accounting-officer/certificate"
                }
                obj + ("auditTags" -> Json.toJson(AuditTags.missing(path)))
              }
            case None => JsError("Missing required submission field: kind")
          }
        case obj: JsObject => JsSuccess(obj)
        case _             => JsError("Expected submission object")
      }
      normalized.flatMap(underlying.reads)
    }

    override def writes(data: SubmissionData): JsObject = underlying.writes(data)
  }

  def scopeKey(subscriptionId: String, kind: SubmissionKind, key: String): String =
    hash(Json.arr(subscriptionId, kind.toString, key).toString)

  def hash(value: String): String =
    MessageDigest.getInstance("SHA-256").digest(value.getBytes(UTF_8)).map(b => f"${b & 0xff}%02x").mkString

  def notification(
      subscriptionId: String,
      correlationId: String,
      request: NotificationRequest,
      auditTags: AuditTags
  ): SubmissionData =
    SubmissionData(
      orchestrationId = UUID.randomUUID().toString,
      subscriptionId = subscriptionId,
      kind = SubmissionKind.Notification,
      idempotencyKey = request.idempotencyKey.getOrElse(UUID.randomUUID().toString),
      correlationId = correlationId,
      notification = Some(request.copy(idempotencyKey = None)),
      certificate = None,
      auditTags = auditTags
    )

  def certificate(
      subscriptionId: String,
      correlationId: String,
      request: CertificateRequest,
      auditTags: AuditTags
  ): SubmissionData =
    SubmissionData(
      orchestrationId = UUID.randomUUID().toString,
      subscriptionId = subscriptionId,
      kind = SubmissionKind.Certificate,
      idempotencyKey = request.idempotencyKey.getOrElse(UUID.randomUUID().toString),
      correlationId = correlationId,
      notification = None,
      certificate = Some(request.copy(idempotencyKey = None)),
      auditTags = auditTags
    )
}
