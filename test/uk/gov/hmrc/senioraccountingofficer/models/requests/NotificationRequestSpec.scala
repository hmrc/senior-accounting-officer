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

class NotificationRequestSpec extends AnyWordSpec with Matchers {

  "Json reader for NotificationRequest" must {

    "return a NotificationRequest when all of the fields exist" in {
      val testCompany = NotificationCompany(
        crn = Some(Crn(generateCrn)),
        utr = Utr(generateUtr),
        name = CompanyName(generateAlphanumeric(CompanyName.maxCompanyNameLength)),
        accPeriodEnd = LocalDate.now(),
        status = CompanyStatus.Active,
        `type` = CompanyType.PLC
      )

      val testCompanies = NotificationCompanies(List(testCompany))

      val testSao = Sao(
        name = PersonName(generateAlphanumeric(PersonName.maxPersonNameLength)),
        fromDate = Some(LocalDate.now()),
        toDate = Some(LocalDate.now())
      )
      val testSaos = Saos(List(testSao))

      val remarks = generateAlphanumeric(FreeText.maxFreeTextLength)

      val result = Json
        .parse(s"""
             |{
             | "saos": ${Json.toJson(testSaos)},
             | "companies" : ${Json.toJson(testCompanies)},
             | "remarks": "$remarks"
             |}""".stripMargin)
        .validate[NotificationRequest]

      result.asEither mustBe Right(
        NotificationRequest(
          saos = Saos(List(testSao)),
          companies = testCompanies,
          remarks = Some(FreeText(remarks))
        )
      )
    }

    "return a NotificationRequest when the optional fields are empty" in {
      val testCompany = NotificationCompany(
        crn = None,
        utr = Utr(generateUtr),
        name = CompanyName(generateAlphanumeric(CompanyName.maxCompanyNameLength)),
        accPeriodEnd = LocalDate.now(),
        status = CompanyStatus.Active,
        `type` = CompanyType.PLC
      )

      val testCompanies = NotificationCompanies(List(testCompany))

      val testSao = Sao(
        name = PersonName(generateAlphanumeric(PersonName.maxPersonNameLength)),
        fromDate = Some(LocalDate.now()),
        toDate = Some(LocalDate.now())
      )
      val testSaos = Saos(List(testSao))

      val result = Json
        .parse(s"""
             |{
             | "saos": ${Json.toJson(testSaos)},
             | "companies" : ${Json.toJson(testCompanies)}
             |}""".stripMargin)
        .validate[NotificationRequest]

      result.asEither mustBe Right(
        NotificationRequest(
          saos = Saos(List(testSao)),
          companies = testCompanies,
          remarks = None
        )
      )
    }

  }

}
