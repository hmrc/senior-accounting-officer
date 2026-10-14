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
import uk.gov.hmrc.senioraccountingofficer.utils.LineWrapHelper.wrapLines
import org.scalatest.matchers.must.Matchers

class LineWrapHelperSpec extends AnyFreeSpec with Matchers {
  "wrapLines" - {
    "must do no wrapping when input string is shorter than line length" in {
      val result = wrapLines("abcd", 5)
      result mustBe "abcd"
    }
    "must move words onto new lines if one line is exceeding line length" in {
      wrapLines("abcd efgh", 4) mustBe "abcd\nefgh"
    }
  }
}
