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

class JsonErrorHandlingNotificationSpec extends AnyWordSpec with Matchers with OptionValues {

  private def notificationErrors(json: String): Seq[JsonErrorHandling.ApiError] =
    JsonErrorHandling.Validators.validateNotification(Json.parse(json))

  private val validNotification =
    """{
      |  "companies": [
      |    {
      |      "name": "Example Ltd",
      |      "utr": "1234567890",
      |      "crn": "AB123456",
      |      "type": "LTD",
      |      "accPeriodEnd": "2024-12-31",
      |      "status": "COMPLIANT"
      |     }
      |    ],
      |    "saos": [
      |        {
      |          "name": "Firstname Lastname",
      |          "email": "Firstname.Lastname@example.com",
      |          "fromDate": "2024-04-01",
      |          "toDate": "2025-03-31"
      |        }
      |    ],
      |  "additionalInformation": "non-empty string"
      |}""".stripMargin

  "Notification validation" when {

    "given a fully valid payload" should {
      "return no errors" in {
        notificationErrors(validNotification) shouldBe empty
      }
    }

    "given a payload missing companies" should {
      "return MISSING_REQUIRED_FIELD pointing at companies" in {
        val remover        = (__ \ "companies").json.prune
        val updatedJsonStr = Json.parse(validNotification).transform(remover).asOpt.value.toString
        println(updatedJsonStr)
        val errors = notificationErrors(updatedJsonStr)
        errors.map(_.reason) should contain("MISSING_REQUIRED_FIELD")
        errors.flatMap(_.path) should contain("companies")
        errors.size shouldBe 1
      }
    }

    "given a company missing utr" should {
      "return MISSING_REQUIRED_FIELD pointing at companies[0].utr" in {
        val errors = notificationErrors(validNotification.replace(""""utr": "1234567890",""", ""))
        errors.map(_.reason) should contain("MISSING_REQUIRED_FIELD")
        errors.flatMap(_.path) should contain("companies[0].utr")
        errors.size shouldBe 1
      }
    }

    "given a sao missing fromDate" should {
      "return MISSING_REQUIRED_FIELD pointing at saos[0].fromDate" in {
        val errors = notificationErrors(validNotification.replace(""""fromDate": "2024-04-01",""", ""))
        errors.map(_.reason) should contain("MISSING_REQUIRED_FIELD")
        errors.flatMap(_.path) should contain("saos[0].fromDate")
        errors.size shouldBe 1
      }
    }

    "given an invalid accPeriodEnd format" should {
      "return INVALID_FORMAT pointing at accPeriodEnd" in {
        val errors = notificationErrors(
          validNotification.replace(""""accPeriodEnd": "2024-12-31"""", """"accPeriodEnd": "-"""")
        )
        errors.map(_.reason) should contain("INVALID_FORMAT")
        errors.flatMap(_.path) should contain("companies[0].accPeriodEnd")
        errors.size shouldBe 1
      }
    }

    "given an invalid enum for company type" should {
      "return INVALID_ENUM_VALUE pointing at type" in {
        val errors = notificationErrors(
          validNotification.replace(""""type": "LTD"""", """"type": """"")
        )
        errors.map(_.reason) should contain("INVALID_ENUM_VALUE")
        errors.flatMap(_.path) should contain("companies[0].type")
        errors.size shouldBe 1
      }
    }
  }
}
