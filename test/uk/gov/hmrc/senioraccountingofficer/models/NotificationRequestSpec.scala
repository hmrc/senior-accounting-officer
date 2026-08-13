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
import uk.gov.hmrc.senioraccountingofficer.models.requests.*
import uk.gov.hmrc.senioraccountingofficer.utils.TestDataGenerator.*

import java.time.LocalDate

class NotificationRequestSpec extends AnyWordSpec with Matchers with OptionValues {
  "toNotificationDpsRequest" should {
    "map from NotificationRequest to NotificationDpsRequest" in {
      val sut = NotificationRequest(
        companies = NotificationCompanies(
          List(
            NotificationCompany(
              crn = Some(Crn(crn)),
              utr = Utr(utr),
              name = CompanyName(companyName),
              accPeriodEnd = accPeriodEnd,
              status = status,
              `type` = companyType
            )
          )
        ),
        saos = Saos(List(Sao(name = PersonName(saoName), fromDate = Some(fromDate), toDate = Some(toDate)))),
        remarks = Some(FreeText(remarks))
      )

      val expected = NotificationDpsRequest(
        companies = List(
          DpsCompany(
            crn = Some(crn),
            utr = utr,
            name = companyName,
            accPeriodEnd = accPeriodEnd.toString,
            status = status,
            `type` = companyType
          )
        ),
        customerId = None,
        saos = List(DpsSao(name = saoName, fromDate = Some(fromDate.toString), toDate = Some(toDate.toString))),
        remarks = Some(remarks),
        staffPID = None
      )

      sut.toNotificationDpsRequest() shouldBe expected
    }
  }
}

object NotificationRequestSpec {
  val subscriptionId = "example subscription id"
  val remarks        = "example additional information"

  val crn                     = generateCrn
  val utr                     = generateUtr
  val companyName             = "example company name"
  val accPeriodEnd: LocalDate = LocalDate.parse("2026-02-01")
  val status                  = CompanyStatus.Active
  val companyType             = CompanyType.LTD

  val saoName             = "example sao name"
  val fromDate: LocalDate = LocalDate.parse("2026-01-01")
  val email               = "example email"
  val toDate: LocalDate   = LocalDate.parse("2026-03-01")
}
