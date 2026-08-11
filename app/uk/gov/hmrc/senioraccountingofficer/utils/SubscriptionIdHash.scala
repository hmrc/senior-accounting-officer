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

import java.nio.charset.StandardCharsets
import java.security.MessageDigest

object SubscriptionIdHash {

  def hex(saoSubscriptionId: String): String =
    MessageDigest
      .getInstance("MD5")
      .digest(saoSubscriptionId.getBytes(StandardCharsets.UTF_8))
      .map(byte => String.format("%02x", Byte.box(byte)))
      .mkString
}
