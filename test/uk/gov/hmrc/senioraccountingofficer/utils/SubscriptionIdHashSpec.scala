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

package uk.gov.hmrc.senioraccountingofficer.utils

import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers

class SubscriptionIdHashSpec extends AnyFreeSpec with Matchers {

  "SubscriptionIdHash.hex" - {
    "must match the shared cross repo vectors" in {
      SubscriptionIdHash.hex("SAOSUB123456789") mustBe "6ac88a7cea0ce7c3e8c3827c9287b17b"
      SubscriptionIdHash.hex("123") mustBe "202cb962ac59075b964b07152d234b70"
      SubscriptionIdHash.hex("240") mustBe "335f5352088d7d9bf74191e006d8e24c"
    }

    "must be lower case hex of a fixed length" in {
      val hashed = SubscriptionIdHash.hex("SAOSUB000000001")
      hashed must have length 32
      hashed mustBe hashed.toLowerCase
      hashed must fullyMatch regex "[0-9a-f]{32}"
    }
  }
}
