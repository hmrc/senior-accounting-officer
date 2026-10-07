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

import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import play.api.libs.json.Json
import uk.gov.hmrc.senioraccountingofficer.services.submission.SubmissionTestData.*

class SubmissionDataSpec extends AnyFreeSpec with Matchers {
  "payload" - {
    "serializes notification data without its idempotency key" in {
      data().copy(notification = Some(notification)).payload mustBe Json.toJson(
        notification.copy(idempotencyKey = None)
      )
    }

    "serializes certificate data without its idempotency key" in {
      data(SubmissionKind.Certificate).copy(certificate = Some(certificate)).payload mustBe
        Json.toJson(certificate.copy(idempotencyKey = None))
    }

    for kind <- SubmissionKind.values do {
      s"identifies a missing $kind payload even when the other payload exists" in {
        val submission =
          if kind == SubmissionKind.Notification then
            data(kind).copy(notification = None, certificate = Some(certificate))
          else data(kind).copy(certificate = None, notification = Some(notification))

        intercept[IllegalStateException](submission.payload).getMessage mustBe
          s"Missing required submission field: ${kind.toString.toLowerCase}"
      }
    }
  }
}
