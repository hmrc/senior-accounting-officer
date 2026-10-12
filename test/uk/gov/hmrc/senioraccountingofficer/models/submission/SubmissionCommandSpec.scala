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

import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec
import uk.gov.hmrc.senioraccountingofficer.models.submission.SubmissionStep.*
import uk.gov.hmrc.senioraccountingofficer.services.submission.SubmissionTestData.*

class SubmissionCommandSpec extends AnyWordSpec with Matchers {
  "next" must {
    "return the same command key for the same submission and step" in {
      val root = SubmissionCommand.start(data())

      root.next(RetrieveCustomer).commandKey mustBe root.next(RetrieveCustomer).commandKey
    }

    "return different command keys for different steps" in {
      val root = SubmissionCommand.start(data())

      root.next(RetrieveCustomer).commandKey must not be root.next(SubmitDps).commandKey
    }

    "return different command keys for different submissions" in {
      val first  = SubmissionCommand.start(data())
      val second = SubmissionCommand.start(data())

      first.next(RetrieveCustomer).commandKey must not be second.next(RetrieveCustomer).commandKey
    }

    "return the same email command key for the same recipient and template" in {
      val root      = SubmissionCommand.start(data())
      val recipient = SubmissionEmail("Recipient", "recipient@example.com", "notification")

      root.next(SendEmail, Some(recipient)).commandKey mustBe root.next(SendEmail, Some(recipient)).commandKey
    }

    "return different email command keys for different recipients" in {
      val root             = SubmissionCommand.start(data())
      val recipient        = SubmissionEmail("Recipient", "recipient@example.com", "notification")
      val anotherRecipient = recipient.copy(address = "another@example.com")

      root.next(SendEmail, Some(recipient)).commandKey must not be root
        .next(SendEmail, Some(anotherRecipient))
        .commandKey
    }

    "return different email command keys for different templates" in {
      val root            = SubmissionCommand.start(data())
      val recipient       = SubmissionEmail("Recipient", "recipient@example.com", "notification")
      val anotherTemplate = recipient.copy(template = "sao")

      root.next(SendEmail, Some(recipient)).commandKey must not be root
        .next(SendEmail, Some(anotherTemplate))
        .commandKey
    }

    "return different email command keys for the same recipient in different submissions" in {
      val first     = SubmissionCommand.start(data())
      val second    = SubmissionCommand.start(data())
      val recipient = SubmissionEmail("Recipient", "recipient@example.com", "notification")

      first.next(SendEmail, Some(recipient)).commandKey must not be second.next(SendEmail, Some(recipient)).commandKey
    }
  }
}
