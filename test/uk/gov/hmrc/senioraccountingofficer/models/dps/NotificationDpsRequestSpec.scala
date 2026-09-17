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

package uk.gov.hmrc.senioraccountingofficer.models.dps

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import uk.gov.hmrc.senioraccountingofficer.models.requests.*

import java.time.{LocalDate, LocalDateTime}

class NotificationDpsRequestSpec extends AnyWordSpec with Matchers {

  "toPdfNotification" should {
    "format PDF dates without leading zeroes and use 12-hour time" in {
      val subscription = GetSubscriptionDpsResponse(
        etmpSafeId = "safe-id",
        nominatedCompany = NominatedCompany(Some("12345678"), "Nominated company", "1234567890"),
        contacts = Nil,
        created = LocalDateTime.of(2025, 6, 1, 9, 5),
        updated = LocalDateTime.of(2025, 6, 1, 9, 5)
      )
      val request = NotificationRequest(
        companies = NotificationCompanies(
          List(
            NotificationCompany(
              crn = Some(Crn("12345678")),
              utr = Utr("1234567890"),
              name = CompanyName("Company name"),
              accPeriodEnd = LocalDate.of(2025, 6, 1),
              status = CompanyStatus.Active,
              `type` = CompanyType.LTD
            )
          )
        ),
        saos = Saos(
          List(
            Sao(
              name = PersonName("SAO name"),
              fromDate = Some(LocalDate.of(2024, 6, 1)),
              toDate = Some(LocalDate.of(2025, 6, 2))
            )
          )
        ),
        remarks = None
      )

      val result = NotificationDpsRequest.toPdfNotification(
        subscriptionId = "subscription-id",
        dpsSubscription = subscription,
        notificationRef = "notification-reference",
        notificationDateTime = LocalDateTime.of(2025, 6, 2, 14, 42),
        request = request
      )

      result.subscriptionCreationDateTime shouldBe "1 June 2025 9:05am"
      result.submissionDateTime shouldBe "2 June 2025 2:42pm"
      result.saoHistory.head.startDate shouldBe Some("1 June 2024")
      result.saoHistory.head.endDate shouldBe Some("2 June 2025")
      result.companies.head.financialYearEndDate shouldBe "1 June 2025"
    }
  }
}
