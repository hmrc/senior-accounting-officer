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
import org.scalatest.OptionValues
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.{Json, __}

class JsonErrorHandlingSubscriptionSpec extends AnyWordSpec with Matchers with OptionValues {

  private def subscriptionErrors(json: String): Seq[JsonErrorHandling.ApiError] =
    JsonErrorHandling.Validators.validateSubscription(Json.parse(json))

  private val validSubscription: String =
    s"""{
       |  "safeId": "XE000123456789",
       |  "company": {
       |    "companyName": "Acme Manufacturing Ltd",
       |    "uniqueTaxReference": "1234567890",
       |    "companyRegistrationNumber": "OC123456"
       |  },
       |  "contacts": [
       |    {
       |      "name": "Jane Doe",
       |      "email": "jane.doe@example.com"
       |    }
       |  ]
       |}""".stripMargin

  "ContactDetails validation" when {

    "given a fully valid payload" should {
      "return no errors" in {
        subscriptionErrors(validSubscription) shouldBe empty
      }
    }

    "given companyType PLC" should {
      "return no errors" in {
        subscriptionErrors(
          validSubscription.replace(""""companyType": "LTD"""", """"companyType": "PLC"""")
        ) shouldBe empty
      }
    }

    "given a payload missing company entirely" should {
      "return MISSING_REQUIRED_FIELD pointing at companies" in {
        val remover        = (__ \ "company").json.prune
        val updatedJsonStr = Json.parse(validSubscription).transform(remover).asOpt.value.toString
        val errors         = subscriptionErrors(updatedJsonStr)
        errors.map(_.reason) should contain("MISSING_REQUIRED_FIELD")
        errors.flatMap(_.path) should contain("company")
      }
    }

    "given a payload missing safeId" should {
      "return MISSING_REQUIRED_FIELD pointing at safeId" in {
        val remover        = (__ \ "safeId").json.prune
        val updatedJsonStr = Json.parse(validSubscription).transform(remover).asOpt.value.toString
        val errors         = subscriptionErrors(updatedJsonStr)
        errors.map(_.reason) should contain("MISSING_REQUIRED_FIELD")
        errors.flatMap(_.path) should contain("safeId")
      }
    }
  }
}
