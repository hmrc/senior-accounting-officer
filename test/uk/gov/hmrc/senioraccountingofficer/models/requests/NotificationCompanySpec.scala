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

class NotificationCompanySpec extends AnyWordSpec with Matchers {

  case class Test(company: NotificationCompany)

  object Test {
    given OFormat[Test] = Json.format
  }

  "Json reader for NotificationCompany" must {
    "return a NotificationCompany when all of the fields exist" in {
      val accPeriodEnd = LocalDate.of(1, 12, 31)
      val testCompany  = NotificationCompany(
        crn = Some(Crn(generateCrn)),
        utr = Utr(generateUtr),
        name = CompanyName(generateAlphanumeric(CompanyName.maxCompanyNameLength)),
        accPeriodEnd = accPeriodEnd,
        status = CompanyStatus.Active,
        `type` = CompanyType.PLC
      )
      val testCompanyAsJson =
        s"""
           |{
           | "crn": "${testCompany.crn.get.value}",
           | "utr": "${testCompany.utr.value}",
           | "name": "${testCompany.name.value}",
           | "accPeriodEnd": "0001-12-31",
           | "status": "${testCompany.status}",
           | "type": "${testCompany.`type`}"
           |}""".stripMargin

      val r = Json
        .parse(s"""
             |{
             | "company" : $testCompanyAsJson
             |}""".stripMargin)
        .validate[Test]

      r.asEither mustBe Right(Test(testCompany))
    }

    "return a NotificationCompany when the optional fields are empty" in {
      val testCompany = NotificationCompany(
        crn = None,
        utr = Utr(generateUtr),
        name = CompanyName(generateAlphanumeric(CompanyName.maxCompanyNameLength)),
        accPeriodEnd = LocalDate.now(),
        status = CompanyStatus.Active,
        `type` = CompanyType.PLC
      )

      val testCompanyAsJson =
        s"""
           |{
           | "utr": "${testCompany.utr.value}",
           | "name": "${testCompany.name.value}",
           | "accPeriodEnd": "${testCompany.accPeriodEnd}",
           | "status": "${testCompany.status}",
           | "type": "${testCompany.`type`}"
           |}""".stripMargin

      val r = Json
        .parse(s"""
             |{
             | "company" : $testCompanyAsJson
             |}""".stripMargin)
        .validate[Test]

      r.asEither mustBe Right(Test(testCompany))
    }
  }
}
