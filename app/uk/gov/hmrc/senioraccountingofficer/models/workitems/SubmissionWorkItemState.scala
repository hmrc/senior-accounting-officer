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

package uk.gov.hmrc.senioraccountingofficer.models.workitems

import play.api.libs.json.{Format, JsError, JsResult, JsString, JsSuccess, JsValue, Json, Reads, Writes}
import uk.gov.hmrc.mongo.play.json.formats.MongoJavatimeFormats
import uk.gov.hmrc.senioraccountingofficer.models.documentum.PreparedSdesSubmission
import uk.gov.hmrc.senioraccountingofficer.models.dps.GetSubscriptionDpsResponse
import uk.gov.hmrc.senioraccountingofficer.models.requests.{CertificateRequest, NotificationRequest}

import java.time.Instant
import java.util.UUID

enum SubmissionFlowType {
  case Notification, Certificate
}

object SubmissionFlowType {
  given Format[SubmissionFlowType] = enumFormat(SubmissionFlowType.valueOf, _.toString)
}

enum SubmissionStep {
  case GetSubscription, RetrieveCrmmCustomer, SubmitDps, SendEmail, PackageDocumentum, NotifySdes
}

object SubmissionStep {
  given Format[SubmissionStep] = enumFormat(SubmissionStep.valueOf, _.toString)
}

enum SubmissionStepStatus {
  case Pending, Completed, Failed, PermanentlyFailed
}

object SubmissionStepStatus {
  given Format[SubmissionStepStatus] = enumFormat(SubmissionStepStatus.valueOf, _.toString)
}

enum SubmissionStateStatus {
  case Active, PermanentlyFailed
}

object SubmissionStateStatus {
  given Format[SubmissionStateStatus] = enumFormat(SubmissionStateStatus.valueOf, _.toString)
}

final case class SubmissionStepState(
    status: SubmissionStepStatus = SubmissionStepStatus.Pending,
    attempts: Int = 0,
    lastError: Option[String] = None,
    updatedAt: Instant = Instant.now()
)

object SubmissionStepState {
  given Format[Instant]             = MongoJavatimeFormats.instantFormat
  given Format[SubmissionStepState] = Json.format
}

final case class SubmissionSteps(
    getSubscription: SubmissionStepState = SubmissionStepState(),
    retrieveCrmmCustomer: SubmissionStepState = SubmissionStepState(),
    submitDps: SubmissionStepState = SubmissionStepState(),
    sendEmail: SubmissionStepState = SubmissionStepState(),
    packageDocumentum: SubmissionStepState = SubmissionStepState(),
    notifySdes: SubmissionStepState = SubmissionStepState()
) {

  def stateFor(step: SubmissionStep): SubmissionStepState = step match {
    case SubmissionStep.GetSubscription      => getSubscription
    case SubmissionStep.RetrieveCrmmCustomer => retrieveCrmmCustomer
    case SubmissionStep.SubmitDps            => submitDps
    case SubmissionStep.SendEmail            => sendEmail
    case SubmissionStep.PackageDocumentum    => packageDocumentum
    case SubmissionStep.NotifySdes           => notifySdes
  }

  def update(step: SubmissionStep, value: SubmissionStepState): SubmissionSteps = step match {
    case SubmissionStep.GetSubscription      => copy(getSubscription = value)
    case SubmissionStep.RetrieveCrmmCustomer => copy(retrieveCrmmCustomer = value)
    case SubmissionStep.SubmitDps            => copy(submitDps = value)
    case SubmissionStep.SendEmail            => copy(sendEmail = value)
    case SubmissionStep.PackageDocumentum    => copy(packageDocumentum = value)
    case SubmissionStep.NotifySdes           => copy(notifySdes = value)
  }

  def nextIncomplete: Option[SubmissionStep] =
    SubmissionStep.values.find(step => stateFor(step).status != SubmissionStepStatus.Completed)
}

object SubmissionSteps {
  given Format[SubmissionSteps] = Json.format
}

final case class SubmissionWorkItemState(
    jobId: String,
    flowType: SubmissionFlowType,
    subscriptionId: String,
    notificationRequest: Option[NotificationRequest],
    certificateRequest: Option[CertificateRequest],
    subscription: Option[GetSubscriptionDpsResponse] = None,
    customerId: Option[String] = None,
    submissionReference: Option[String] = None,
    preparedSdesSubmission: Option[PreparedSdesSubmission] = None,
    steps: SubmissionSteps = SubmissionSteps(),
    status: SubmissionStateStatus = SubmissionStateStatus.Active,
    lastUpdated: Instant = Instant.now()
)

object SubmissionWorkItemState {
  given Format[Instant]               = MongoJavatimeFormats.instantFormat
  given Format[SubmissionWorkItemState] = Json.format

  def notification(subscriptionId: String, request: NotificationRequest): SubmissionWorkItemState =
    SubmissionWorkItemState(
      jobId = UUID.randomUUID().toString,
      flowType = SubmissionFlowType.Notification,
      subscriptionId = subscriptionId,
      notificationRequest = Some(request),
      certificateRequest = None
    )

  def certificate(subscriptionId: String, request: CertificateRequest): SubmissionWorkItemState =
    SubmissionWorkItemState(
      jobId = UUID.randomUUID().toString,
      flowType = SubmissionFlowType.Certificate,
      subscriptionId = subscriptionId,
      notificationRequest = None,
      certificateRequest = Some(request)
    )
}

private def enumFormat[A](parse: String => A, render: A => String): Format[A] = Format(
  Reads {
    case JsString(value) =>
      try JsSuccess(parse(value))
      catch {
        case _: IllegalArgumentException => JsError(s"Unknown enum value: $value")
      }
    case value           => JsError(s"Expected string enum value but found $value")
  },
  Writes[A](value => JsString(render(value)))
)
