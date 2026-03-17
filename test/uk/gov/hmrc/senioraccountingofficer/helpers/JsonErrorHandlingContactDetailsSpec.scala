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

package uk.gov.hmrc.senioraccountingofficer.helpers

import org.scalactic.Prettifier.default
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.Json

class JsonErrorHandlingContactDetailsSpec extends AnyWordSpec with Matchers {

  private def contactDetailsErrors(json: String): Seq[JsonErrorHandling.ApiError] =
    JsonErrorHandling.Validators.validateContactDetails(Json.parse(json))

  "ContactDetails validation" when {

    "given a valid single contact" should {
      "return no errors" in {
        contactDetailsErrors("""[{"name":"Alice","email":"alice@example.com"}]""") shouldBe empty
      }
    }

    "given two valid contacts" should {
      "return no errors" in {
        contactDetailsErrors("""[
            {"name":"Alice","email":"alice@example.com"},
            {"name":"Bob","email":"bob@example.com"}
          ]""") shouldBe empty
      }
    }

    "given a contact missing name" should {
      "return MISSING_REQUIRED_FIELD for name" in {
        val errors = contactDetailsErrors("""[{"email":"alice@example.com"}]""")
        errors.map(_.reason) should contain("MISSING_REQUIRED_FIELD")
        errors.flatMap(_.path) should contain("[0].name")
        errors.size shouldBe 1
      }
    }

    "given a contact missing email" should {
      "return MISSING_REQUIRED_FIELD for email" in {
        val errors = contactDetailsErrors("""[{"name":"Alice"}]""")
        errors.map(_.reason) should contain("MISSING_REQUIRED_FIELD")
        errors.flatMap(_.path) should contain("[0].email")
        errors.size shouldBe 1
      }
    }

    "given a contact where name is not a string" should {
      "return INVALID_DATA_TYPE for name" in {
        val errors = contactDetailsErrors("""[{"name":123,"email":"alice@example.com"}]""")
        errors.map(_.reason) should contain("INVALID_DATA_TYPE")
        errors.flatMap(_.path) should contain("[0].name")
        errors.size shouldBe 1
      }
    }

    "given an unexpected additional property" should {
      "return INVALID_DATA_TYPE for the extra field path" in {
        val errors = contactDetailsErrors(
          """[{"name":"Alice","email":"alice@example.com","unexpected":"value"}]"""
        )
        errors.map(_.reason) should contain("INVALID_DATA_TYPE")
        errors.flatMap(_.path) should contain("[0].unexpected")
        errors.size shouldBe 1
      }
    }

    "given a contact with an invalid email" should {
      "return INVALID_FORMAT for email" in {
        val errors = contactDetailsErrors("""[{"name":"Alice","email":"not-an-email"}]""")
        errors.map(_.reason) should contain("INVALID_FORMAT")
        errors.flatMap(_.path) should contain("[0].email")
        errors.size shouldBe 1
      }
    }

    "given a contact with an empty name string" should {
      "return CANNOT_BE_EMPTY for name" in {
        val errors = contactDetailsErrors("""[{"name":"","email":"alice@example.com"}]""")
        errors.map(_.reason) should contain("CANNOT_BE_EMPTY")
        errors.flatMap(_.path) should contain("[0].name")
        errors.size shouldBe 1
      }
    }

    "given a contact with an empty email string" should {
      "return CANNOT_BE_EMPTY for email" in {
        val errors = contactDetailsErrors("""[{"name":"Alice","email":""}]""")
        errors.map(_.reason) should (contain("CANNOT_BE_EMPTY") and contain("INVALID_FORMAT"))
        errors.flatMap(_.path) should contain("[0].email")
        errors.size shouldBe 2
      }
    }

    "given a name exceeding 120 characters" should {
      "return LENGTH_OUT_OF_BOUNDS for name" in {
        val errors = contactDetailsErrors(
          s"""[{"name":"${"A" * 121}","email":"alice@example.com"}]"""
        )
        errors.map(_.reason) should contain("LENGTH_OUT_OF_BOUNDS")
        errors.flatMap(_.path) should contain("[0].name")
        errors.size shouldBe 1
      }
    }

    "given an email exceeding 254 characters" should {
      "return LENGTH_OUT_OF_BOUNDS for email" in {
        val errors = contactDetailsErrors(
          s"""[{"name":"Alice","email":"${"a" * 250}@example.com"}]"""
        )
        errors.map(_.reason) should (contain("LENGTH_OUT_OF_BOUNDS") and contain("INVALID_FORMAT"))
        errors.flatMap(_.path) should contain("[0].email")
        errors.size shouldBe 2
      }
    }

    "given an empty array" should {
      "return ARRAY_MIN_ITEMS_NOT_MET" in {
        contactDetailsErrors("[]").map(_.reason) should contain("ARRAY_MIN_ITEMS_NOT_MET")
      }
    }

    "given an array with 3 contacts" should {
      "return LENGTH_OUT_OF_BOUNDS" in {
        val errors = contactDetailsErrors("""[
            {"name":"A","email":"a@a.com"},
            {"name":"B","email":"b@b.com"},
            {"name":"C","email":"c@c.com"}
          ]""")
        errors.map(_.reason) should contain("LENGTH_OUT_OF_BOUNDS")
        errors.size shouldBe 1
      }
    }
  }
}
