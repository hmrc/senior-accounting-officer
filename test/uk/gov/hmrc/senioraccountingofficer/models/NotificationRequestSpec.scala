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

package uk.gov.hmrc.senioraccountingofficer.models

import org.scalatest.OptionValues
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import uk.gov.hmrc.senioraccountingofficer.models.NotificationRequestSpec.*
import uk.gov.hmrc.senioraccountingofficer.models.dps.{Company as DpsCompany, NotificationDpsRequest, Sao as DpsSao}

import java.time.LocalDate

class NotificationRequestSpec extends AnyWordSpec with Matchers with OptionValues {
  val subscriptionId = "example subscription id"
  "toNotificationDpsRequest" should {
    "map from NotificationRequest to NotificationDpsRequest" in {
      val sut = NotificationRequest(
        subscriptionId = subscriptionId,
        companies = List(
          Company(
            crn = Some(crn),
            utr = utr,
            name = companyName,
            accPeriodEnd = accPeriodEnd,
            status = status,
            `type` = companyType
          )
        ),
        saos = List(Sao(name = saoName, fromDate = fromDate, email = Some(email), toDate = toDate)),
        additionalInformation = Some(additionalInformation)
      )

      val expected = NotificationDpsRequest(
        companies = List(
          DpsCompany(
            crn = Some(crn),
            utr = utr,
            name = companyName,
            accPeriodEnd = accPeriodEnd,
            status = status,
            `type` = companyType
          )
        ),
        saos = List(DpsSao(name = saoName, fromDate = fromDate, email = Some(email), toDate = toDate)),
        remarks = Some(additionalInformation),
        staffPID = None
      )

      sut.toNotificationDpsRequest shouldBe expected
    }
  }
}

object NotificationRequestSpec {
  val subscriptionId        = "example subscription id"
  val additionalInformation = "example additional information"

  val crn          = "example crn"
  val utr          = "example utr"
  val companyName  = "example company name"
  val accPeriodEnd = "example accPeriodEnd"
  val status       = "example status"
  val companyType  = "example type"

  val saoName          = "example sao name"
  val fromDate: String = LocalDate.parse("2026-01-01").toString()
  val email            = "example email"
  val toDate: String   = LocalDate.parse("2026-03-01").toString()
}
