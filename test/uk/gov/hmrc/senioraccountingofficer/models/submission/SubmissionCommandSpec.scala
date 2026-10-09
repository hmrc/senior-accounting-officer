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
import uk.gov.hmrc.senioraccountingofficer.models.submission.SubmissionStep.*
import uk.gov.hmrc.senioraccountingofficer.services.submission.SubmissionTestData.*

class SubmissionCommandSpec extends AnyFreeSpec with Matchers {
  "command keys" - {
    "deduplicate a non-email step and distinguish other steps and submissions" in {
      val root = SubmissionCommand.start(data())
      val next = root.next(RetrieveCustomer)
      next.commandKey mustBe s"${root.data.orchestrationId}/RetrieveCustomer/"
      root.next(RetrieveCustomer).commandKey mustBe next.commandKey
      root.next(SubmitDps).commandKey must not be next.commandKey
      SubmissionCommand.start(data()).next(RetrieveCustomer).commandKey must not be next.commandKey
    }

    "deduplicate an email by address and template and distinguish different recipients or templates" in {
      val root      = SubmissionCommand.start(data())
      val recipient = SubmissionEmail("Recipient", "recipient@example.com", "notification")
      val key       = root.next(SendEmail, Some(recipient)).commandKey
      root.next(SendEmail, Some(recipient)).commandKey mustBe key
      root.next(SendEmail, Some(recipient.copy(address = "another@example.com"))).commandKey must not be key
      root.next(SendEmail, Some(recipient.copy(template = "sao"))).commandKey must not be key
    }
  }
}
