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
import uk.gov.hmrc.senioraccountingofficer.utils.TestDataGenerator.generateAlphanumeric

import java.time.LocalDate

class SaoSpec extends AnyWordSpec with Matchers {

  case class Test(sao: Sao)

  object Test {
    given OFormat[Test] = Json.format
  }

  "Json reader for Sao" must {
    "return a Sao when all of the fields exist" in {
      val fromDate = LocalDate.of(1, 12, 31)
      val toDate   = LocalDate.of(2, 12, 31)
      val testSao  = Sao(
        name = PersonName(generateAlphanumeric(PersonName.maxPersonNameLength)),
        fromDate = Some(fromDate),
        toDate = Some(toDate)
      )
      val testSaoAsJson =
        s"""
           |{
           | "name": "${testSao.name.value}",
           | "fromDate": "0001-12-31",
           | "toDate": "0002-12-31"
           |}""".stripMargin

      val r = Json
        .parse(s"""
             |{
             | "sao" : $testSaoAsJson
             |}""".stripMargin)
        .validate[Test]

      r.asEither mustBe Right(Test(testSao))
    }

    "return a Sao when the optional fields are empty" in {
      val testSao = Sao(
        name = PersonName(generateAlphanumeric(CompanyName.maxCompanyNameLength)),
        fromDate = None,
        toDate = None
      )
      val testSaoAsJson =
        s"""
           |{
           | "name": "${testSao.name.value}"
           |}""".stripMargin

      val r = Json
        .parse(s"""
             |{
             | "sao" : $testSaoAsJson
             |}""".stripMargin)
        .validate[Test]

      r.asEither mustBe Right(Test(testSao))
    }
  }
}
