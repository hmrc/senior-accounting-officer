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
import play.api.libs.json.JsNull

class JsonErrorHandlingSpec extends AnyWordSpec with Matchers {

  "parseJson" when {

    "given well-formed JSON" should {

      "return Right for a JSON object" in {
        JsonErrorHandling.parseJson("""{"key":"value"}""") shouldBe a[Right[_, _]]
      }

      "return Right for a JSON array" in {
        JsonErrorHandling.parseJson("""[1,2,3]""") shouldBe a[Right[_, _]]
      }

      "return Right for JSON null" in {
        JsonErrorHandling.parseJson("null").toOption.get shouldBe JsNull
      }

      "return Right for an empty object" in {
        JsonErrorHandling.parseJson("{}") shouldBe a[Right[_, _]]
      }
    }

    "given malformed JSON" should {

      "return Left(400) for completely invalid input" in {
        val result = JsonErrorHandling.parseJson("not json")
        result shouldBe a[Left[_, _]]
        result.left.toOption.get.header.status shouldBe 400
      }

      "return Left(400) for an empty string" in {
        val result = JsonErrorHandling.parseJson("")
        result shouldBe a[Left[_, _]]
        result.left.toOption.get.header.status shouldBe 400
      }

      "return Left(400) for truncated JSON" in {
        val result = JsonErrorHandling.parseJson("""{"key":""")
        result shouldBe a[Left[_, _]]
        result.left.toOption.get.header.status shouldBe 400
      }
    }
  }

  "badRequest" should {

    "return status 400" in {
      JsonErrorHandling
        .badRequest(Seq(JsonErrorHandling.ApiError(None, "MALFORMED_REQUEST")))
        .header
        .status shouldBe 400
    }

    "accept errors with a path" in {
      JsonErrorHandling
        .badRequest(Seq(JsonErrorHandling.ApiError(Some("companies.0.companyName"), "MISSING_REQUIRED_FIELD")))
        .header
        .status shouldBe 400
    }

    "accept errors without a path" in {
      JsonErrorHandling
        .badRequest(Seq(JsonErrorHandling.ApiError(None, "MALFORMED_REQUEST")))
        .header
        .status shouldBe 400
    }

    "accept a mix of errors with and without paths" in {
      JsonErrorHandling
        .badRequest(
          Seq(
            JsonErrorHandling.ApiError(Some("safeId"), "INVALID_FORMAT"),
            JsonErrorHandling.ApiError(None, "MALFORMED_REQUEST")
          )
        )
        .header
        .status shouldBe 400
    }
  }
}
