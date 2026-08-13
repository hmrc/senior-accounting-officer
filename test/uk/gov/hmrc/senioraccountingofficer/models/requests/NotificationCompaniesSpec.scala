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

package uk.gov.hmrc.senioraccountingofficer.models.requests

import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.*
import uk.gov.hmrc.senioraccountingofficer.utils.TestDataGenerator.{generateAlphanumeric, generateCrn, generateUtr}

import java.time.LocalDate

class NotificationCompaniesSpec extends AnyWordSpec with Matchers {

  case class Test(companies: NotificationCompanies)

  object Test {
    given OFormat[Test] = Json.format
  }

  "Json reader for NotificationCompanies" must {
    "return a NotificationCompanies when the value is valid" in {
      val testCompany1 = NotificationCompany(
        crn = Some(Crn(generateCrn)),
        utr = Utr(generateUtr),
        name = CompanyName(generateAlphanumeric(CompanyName.maxCompanyNameLength)),
        accPeriodEnd = LocalDate.now(),
        status = CompanyStatus.Active,
        `type` = CompanyType.PLC
      )

      val testCompany2 = NotificationCompany(
        crn = None,
        utr = Utr(generateUtr),
        name = CompanyName(generateAlphanumeric(CompanyName.maxCompanyNameLength)),
        accPeriodEnd = LocalDate.now(),
        status = CompanyStatus.Active,
        `type` = CompanyType.PLC
      )

      val testCompanies = Json.arr(testCompany1, testCompany2)
      val result        = Json
        .parse(s"""
             |{
             | "companies" : $testCompanies
             |}""".stripMargin)
        .validate[Test]

      result.asEither mustBe Right(Test(NotificationCompanies(List(testCompany1, testCompany2))))
    }

    "return an error" when {
      "the the list is empty with message ARRAY_MIN_ITEMS_NOT_MET" in {
        val result = Json
          .parse("""
              |{
              | "companies" : []
              |}""".stripMargin)
          .validate[Test]

        result.asEither mustBe Left(List((JsPath.\("companies"), List(JsonValidationError("ARRAY_MIN_ITEMS_NOT_MET")))))
      }
    }
  }
}
