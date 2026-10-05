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
import uk.gov.hmrc.mongo.play.json.formats.MongoJavatimeFormats
import uk.gov.hmrc.senioraccountingofficer.models.ApiError
import uk.gov.hmrc.senioraccountingofficer.models.documentum.PreparedSdesSubmission
import uk.gov.hmrc.senioraccountingofficer.models.dps.GetSubscriptionDpsResponse
import uk.gov.hmrc.senioraccountingofficer.models.requests.{CertificateRequest, NotificationRequest}
import java.time.{Instant, LocalDateTime}
import java.nio.charset.StandardCharsets.UTF_8
import java.security.MessageDigest
import java.util.UUID

enum SubmissionKind {
  case Notification, Certificate
}
object SubmissionKind {
  given Format[SubmissionKind] = JsonFormats.enumFormat(SubmissionKind.valueOf)
}

enum SubmissionStep {
  case RetrieveSubscription, RetrieveCustomer, SubmitDps, InitialPdf, SendEmail, EnsurePdf, PackageDocumentum,
    NotifySdes
}
object SubmissionStep {
  given Format[SubmissionStep] = JsonFormats.enumFormat(SubmissionStep.valueOf)
}

object JsonFormats {
  def enumFormat[A](parse: String => A): Format[A] = Format(
    Reads.StringReads.flatMap(value =>
      Reads(_ => scala.util.Try(parse(value)).fold(_ => JsError("Invalid enum"), JsSuccess(_)))
    ),
    Writes(value => JsString(value.toString))
  )
}

case class SubmissionFailure(httpStatus: Int, error: ApiError, ambiguous: Boolean = false)
object SubmissionFailure {
  given OFormat[SubmissionFailure] = Json.format
}

// Persist the exact recipient/template choice so retries do not send again to successful recipients.
case class SubmissionEmail(recipientName: String, address: String, template: String)
object SubmissionEmail {
  given OFormat[SubmissionEmail] = Json.format
}

case class SubmissionData(
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
    prepared: Option[PreparedSdesSubmission] = None
) {
  def scopeKey: String = SubmissionData.scopeKey(subscriptionId, kind, idempotencyKey)
  def payload: JsValue = kind match {
    case SubmissionKind.Notification => Json.toJson(notification.get.copy(idempotencyKey = None))
    case SubmissionKind.Certificate  => Json.toJson(certificate.get.copy(idempotencyKey = None))
  }
}
object SubmissionData {
  given OFormat[SubmissionData]                                                   = Json.format
  def scopeKey(subscriptionId: String, kind: SubmissionKind, key: String): String =
    hash(Json.arr(subscriptionId, kind.toString, key).toString)
  def hash(value: String): String =
    MessageDigest.getInstance("SHA-256").digest(value.getBytes(UTF_8)).map(b => f"${b & 0xff}%02x").mkString
  def notification(subscriptionId: String, correlationId: String, request: NotificationRequest): SubmissionData =
    SubmissionData(
      UUID.randomUUID().toString,
      subscriptionId,
      SubmissionKind.Notification,
      request.idempotencyKey.getOrElse(UUID.randomUUID().toString),
      correlationId,
      Some(request.copy(idempotencyKey = None)),
      None
    )
  def certificate(subscriptionId: String, correlationId: String, request: CertificateRequest): SubmissionData =
    SubmissionData(
      UUID.randomUUID().toString,
      subscriptionId,
      SubmissionKind.Certificate,
      request.idempotencyKey.getOrElse(UUID.randomUUID().toString),
      correlationId,
      None,
      Some(request.copy(idempotencyKey = None))
    )
}

case class SubmissionCommand(
    commandKey: String,
    step: SubmissionStep,
    data: SubmissionData,
    email: Option[SubmissionEmail] = None,
    dispatched: Boolean = false,
    operationFinished: Boolean = false,
    failure: Option[SubmissionFailure] = None,
    lastError: Option[String] = None,
    leaseToken: Option[String] = None,
    expiresAt: Option[Instant] = None
) {
  def next(step: SubmissionStep, email: Option[SubmissionEmail] = None): SubmissionCommand =
    SubmissionCommand(
      s"${data.orchestrationId}/$step/${email.map(e => SubmissionData.hash(e.address + e.template)).getOrElse("")}",
      step,
      data,
      email
    )
}
object SubmissionCommand {
  given Format[Instant]                              = MongoJavatimeFormats.instantFormat
  given OFormat[SubmissionCommand]                   = Json.format
  def start(data: SubmissionData): SubmissionCommand =
    SubmissionCommand(data.scopeKey, SubmissionStep.RetrieveSubscription, data)
}

case class SubmissionState(
    _id: String,
    scopeKey: String,
    reference: Option[String] = None,
    pdfAttempted: Boolean = false,
    pdfStored: Boolean = false,
    failure: Option[SubmissionFailure] = None,
    terminalAt: Option[Instant] = None,
    expiresAt: Option[Instant] = None
)
object SubmissionState {
  given Format[Instant]          = MongoJavatimeFormats.instantFormat
  given OFormat[SubmissionState] = Json.using[Json.WithDefaultValues].format
}
