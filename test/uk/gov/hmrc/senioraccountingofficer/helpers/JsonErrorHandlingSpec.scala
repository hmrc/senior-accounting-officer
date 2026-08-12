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
import org.scalatest.{EitherValues, OptionValues}
import uk.gov.hmrc.senioraccountingofficer.models.ApiError
import uk.gov.hmrc.senioraccountingofficer.models.ApiError.*

class JsonErrorHandlingSpec extends AnyWordSpec with Matchers with OptionValues with EitherValues {

  "badRequest" should {

    "return status 400" in {
      JsonErrorHandling
        .badRequest(Seq(ApiError(reason = Reason.MALFORMED_REQUEST)))
        .header
        .status shouldBe 400
    }

    "accept errors with a path" in {
      JsonErrorHandling
        .badRequest(Seq(ApiError(reason = Reason.MISSING_REQUIRED_FIELD, path = Some("companies.0.companyName"))))
        .header
        .status shouldBe 400
    }

    "accept errors without a path" in {
      JsonErrorHandling
        .badRequest(Seq(ApiError(reason = Reason.MALFORMED_REQUEST)))
        .header
        .status shouldBe 400
    }

    "accept a mix of errors with and without paths" in {
      JsonErrorHandling
        .badRequest(
          Seq(
            ApiError(reason = Reason.INVALID_FORMAT, path = Some("safeId")),
            ApiError(reason = Reason.MALFORMED_REQUEST)
          )
        )
        .header
        .status shouldBe 400
    }
  }
}
