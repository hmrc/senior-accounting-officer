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

import play.api.libs.json.*
import uk.gov.hmrc.mongo.play.json.formats.MongoJavatimeFormats
import uk.gov.hmrc.senioraccountingofficer.models.documentum.PreparedSdesSubmission
import uk.gov.hmrc.senioraccountingofficer.models.dps.GetSubscriptionDpsResponse
import uk.gov.hmrc.senioraccountingofficer.models.requests.NotificationRequest

import java.time.temporal.ChronoUnit.MILLIS
import java.time.{Instant, LocalDateTime}

enum NotificationStep {
  case SubmitDps, GeneratePdf, SendEmail, PackageDocumentum, NotifySdes
}

object NotificationStep {
  given Format[NotificationStep] = enumFormat(NotificationStep.valueOf, _.toString)
}

enum NotificationStepStatus {
  case Pending, Completed, Failed, PermanentlyFailed
}

object NotificationStepStatus {
  given Format[NotificationStepStatus] = enumFormat(NotificationStepStatus.valueOf, _.toString)
}

final case class NotificationStepState(
    status: NotificationStepStatus = NotificationStepStatus.Pending,
    attempts: Int = 0,
    lastError: Option[String] = None,
    updatedAt: Instant = Instant.now().truncatedTo(MILLIS)
)

object NotificationStepState {
  given Format[Instant]               = MongoJavatimeFormats.instantFormat
  given Format[NotificationStepState] = Json.format
}

final case class NotificationSteps(
    submitDps: NotificationStepState = NotificationStepState(),
    generatePdf: NotificationStepState = NotificationStepState(),
    sendEmail: NotificationStepState = NotificationStepState(),
    packageDocumentum: NotificationStepState = NotificationStepState(),
    notifySdes: NotificationStepState = NotificationStepState()
) {

  def stateFor(step: NotificationStep): NotificationStepState = step match {
    case NotificationStep.SubmitDps         => submitDps
    case NotificationStep.GeneratePdf       => generatePdf
    case NotificationStep.SendEmail         => sendEmail
    case NotificationStep.PackageDocumentum => packageDocumentum
    case NotificationStep.NotifySdes        => notifySdes
  }

  def update(step: NotificationStep, value: NotificationStepState): NotificationSteps = step match {
    case NotificationStep.SubmitDps         => copy(submitDps = value)
    case NotificationStep.GeneratePdf       => copy(generatePdf = value)
    case NotificationStep.SendEmail         => copy(sendEmail = value)
    case NotificationStep.PackageDocumentum => copy(packageDocumentum = value)
    case NotificationStep.NotifySdes        => copy(notifySdes = value)
  }
}

object NotificationSteps {
  given Format[NotificationSteps] = Json.format
}

final case class NotificationCheckpoint(
    id: String,
    subscriptionId: String,
    subscription: GetSubscriptionDpsResponse,
    request: NotificationRequest,
    customerId: Option[String] = None,
    notificationReference: Option[String] = None,
    notificationDateTime: Option[LocalDateTime] = None,
    preparedSdesSubmission: Option[PreparedSdesSubmission] = None,
    steps: NotificationSteps = NotificationSteps()
)

object NotificationCheckpoint {
  given Format[NotificationCheckpoint] = Json.format

  def start(
      correlationId: String,
      subscriptionId: String,
      subscription: GetSubscriptionDpsResponse,
      request: NotificationRequest
  ): NotificationCheckpoint =
    NotificationCheckpoint(correlationId, subscriptionId, subscription, request)
}

final case class NotificationRetry(failedStep: NotificationStep, checkpoint: NotificationCheckpoint)

object NotificationRetry {
  given Format[NotificationRetry] = Json.format
}

private def enumFormat[A](parse: String => A, render: A => String): Format[A] = Format(
  Reads {
    case JsString(value) =>
      try JsSuccess(parse(value))
      catch {
        case _: IllegalArgumentException => JsError(s"Unknown enum value: $value")
      }
    case value => JsError(s"Expected string enum value but found $value")
  },
  Writes[A](value => JsString(render(value)))
)
