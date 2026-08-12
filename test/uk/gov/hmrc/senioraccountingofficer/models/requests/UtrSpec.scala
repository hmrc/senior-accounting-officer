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
import uk.gov.hmrc.senioraccountingofficer.utils.TestDataGenerator.generateUtr

class UtrSpec extends AnyWordSpec with Matchers {

  case class Test(utr: Utr)

  object Test {
    given OFormat[Test] = Json.format
  }

  "Json reader for Utr" must {
    "return a Utr when the value is valid" in {
      val testUtr = generateUtr
      val result  = Json
        .parse(s"""
            |{
            | "utr" : "$testUtr"
            |}""".stripMargin)
        .validate[Test]

      result.asEither mustBe Right(Test(Utr(testUtr)))
    }

    "return an error" when {
      "the value is too short with message CANNOT_BE_EMPTY" in {
        val result = Json
          .parse("""
              |{
              | "utr" : ""
              |}""".stripMargin)
          .validate[Test]

        result.asEither mustBe Left(List((JsPath.\("utr"), List(JsonValidationError("CANNOT_BE_EMPTY")))))
      }

      "the value is too long with message INVALID_FORMAT" in {
        val result = Json
          .parse("""
              |{
              | "utr" : "12345678901"
              |}""".stripMargin)
          .validate[Test]

        result.asEither mustBe Left(List((JsPath.\("utr"), List(JsonValidationError("INVALID_FORMAT")))))
      }
    }
  }
}
