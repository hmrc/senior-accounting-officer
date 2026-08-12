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

class StaffIdSpec extends AnyWordSpec with Matchers {

  case class Test(id: StaffId)

  object Test {
    given OFormat[Test] = Json.format
  }

  "Json reader for StaffId" must {
    "return a StaffId when the value is valid" in {
      val testStaffId = generateAlphanumeric(30)
      val result = Json
        .parse(
          s"""
             |{
             | "id" : "$testStaffId"
             |}""".stripMargin)
        .validate[Test]

      result.asEither mustBe Right(Test(StaffId(testStaffId)))
    }

    "return an error" when {
      "the value is too short with message CANNOT_BE_EMPTY" in {
        val result = Json
          .parse(
            """
              |{
              | "id" : ""
              |}""".stripMargin)
          .validate[Test]

        result.asEither mustBe Left(List((JsPath.\("id"), List(JsonValidationError("CANNOT_BE_EMPTY")))))
      }

      "the value is too long with message INVALID_FORMAT" in {
        val result = Json
          .parse(
            s"""
              |{
              | "id" : "${generateAlphanumeric(31)}"
              |}""".stripMargin)
          .validate[Test]

        result.asEither mustBe Left(List((JsPath.\("id"), List(JsonValidationError("INVALID_FORMAT")))))
      }
    }
  }
}
