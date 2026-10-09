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

import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.must.Matchers
import play.api.libs.json.Json
import uk.gov.hmrc.senioraccountingofficer.services.submission.SubmissionTestData.*

class SubmissionDataSpec extends AnyWordSpec with Matchers {
  "payload" must {
    "serialize notification data without its idempotency key" in {
      data().copy(notification = Some(notification)).payload mustBe Json.toJson(
        notification.copy(idempotencyKey = None)
      )
    }

    "serialize certificate data without its idempotency key" in {
      data(SubmissionKind.Certificate).copy(certificate = Some(certificate)).payload mustBe
        Json.toJson(certificate.copy(idempotencyKey = None))
    }

    "identify a missing notification payload even when a certificate is present" in {
      val submission = data().copy(notification = None, certificate = Some(certificate))

      val exception = intercept[IllegalStateException](submission.payload)

      exception.getMessage mustBe "Missing required submission field: notification"
    }

    "identify a missing certificate payload even when a notification is present" in {
      val submission = data(SubmissionKind.Certificate).copy(certificate = None, notification = Some(notification))

      val exception = intercept[IllegalStateException](submission.payload)

      exception.getMessage mustBe "Missing required submission field: certificate"
    }
  }
}
