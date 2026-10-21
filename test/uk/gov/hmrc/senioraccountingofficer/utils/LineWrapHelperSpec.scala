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
  "wrapLines" in {
    wrapLines("abcd", 5) mustBe "abcd"
    wrapLines("abcd efgh", 4) mustBe "abcd\nefgh"
    wrapLines("abcd efgh", 3) mustBe "abc-\nd e-\nfgh"
    wrapLines("abcd efgh", 2) mustBe "ab-\ncd\nefg-\nh"
  }
}
