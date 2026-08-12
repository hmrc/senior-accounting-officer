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

class SaosSpec extends AnyWordSpec with Matchers {

  case class Test(saos: Saos)

  object Test {
    given OFormat[Test] = Json.format
  }

  "Json reader for Saos" must {
    "return a Saos when the value is valid" in {
      val fromDate = LocalDate.of(1, 12, 31)
      val toDate   = LocalDate.of(2, 12, 31)

      val testSao1 = Sao(
        name = PersonName(generateAlphanumeric(PersonName.maxPersonNameLength)),
        fromDate = Some(fromDate),
        toDate = Some(toDate)
      )

      val testSao2 = Sao(
        name = PersonName(generateAlphanumeric(PersonName.maxPersonNameLength)),
        fromDate = None,
        toDate = None
      )

      val testSaos = Json.arr(testSao1, testSao2)
      val result   = Json
        .parse(s"""
             |{
             | "saos" : $testSaos
             |}""".stripMargin)
        .validate[Test]

      result.asEither mustBe Right(Test(Saos(List(testSao1, testSao2))))
    }

    "return an error" when {
      "the the list is empty with message ARRAY_MIN_ITEMS_NOT_MET" in {
        val result = Json
          .parse("""
              |{
              | "saos" : []
              |}""".stripMargin)
          .validate[Test]

        result.asEither mustBe Left(List((JsPath.\("saos"), List(JsonValidationError("ARRAY_MIN_ITEMS_NOT_MET")))))
      }
    }
  }

}
