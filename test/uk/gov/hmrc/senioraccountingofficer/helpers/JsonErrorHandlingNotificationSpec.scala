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
import play.api.libs.json.*
import play.api.libs.json.{Json, __}
import uk.gov.hmrc.domain.SaUtrGenerator
import uk.gov.hmrc.senioraccountingofficer.models.ApiError
import uk.gov.hmrc.senioraccountingofficer.models.ApiError.*

import scala.util.Random

class JsonErrorHandlingNotificationSpec extends AnyWordSpec with Matchers with OptionValues {

  private def notificationErrors(json: String): Seq[ApiError] =
    JsonErrorHandling.Validators.validateNotification(Json.parse(json))

  private val validNotification =
    s"""{
      | "subscriptionId": "123",
      |  "companies": [
      |    {
      |      "name": "Example Ltd",
      |      "utr": "$generateUtr",
      |      "crn": "$generateCrn",
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
      |  "remarks": "non-empty string"
      |}""".stripMargin

  private def generateCrn = {
    val num = Random.nextInt(1000000)
    f"$num%08d"
  }

  private def generateUtr = {
    val seed = Random.nextInt(1000000)
    SaUtrGenerator(seed).nextSaUtr
  }

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
        val errors         = notificationErrors(updatedJsonStr)
        errors.map(_.reason) should contain(Reason.MISSING_REQUIRED_FIELD)
        errors.flatMap(_.path) should contain("companies")
        errors.size shouldBe 1
      }
    }

    "given a company missing utr" should {
      "return MISSING_REQUIRED_FIELD pointing at companies[0].utr" in {
        val json = Json.parse(validNotification)
        val utr  = (json \ "companies" \ 0 \ "utr").as[String]

        val errors = notificationErrors(validNotification.replaceAll(s""""utr": "$utr",""", ""))
        errors.map(_.reason) should contain(Reason.MISSING_REQUIRED_FIELD)
        errors.flatMap(_.path) should contain("companies[0].utr")
        errors.size shouldBe 1
      }
    }

    "given a company missing name" should {
      "return MISSING_REQUIRED_FIELD pointing at companies[0].name" in {
        val json        = Json.parse(validNotification)
        val companyName = (json \ "companies" \ 0 \ "name").as[String]

        val errors = notificationErrors(validNotification.replaceAll(s""""name": "$companyName",""", ""))
        errors.map(_.reason) should contain(Reason.MISSING_REQUIRED_FIELD)
        errors.flatMap(_.path) should contain("companies[0].name")
        errors.size shouldBe 1
      }
    }

    "given a company missing accPeriodEnd" should {
      "return MISSING_REQUIRED_FIELD pointing at companies[0].accPeriodEnd" in {
        val errors = notificationErrors(validNotification.replace(""""accPeriodEnd": "2024-12-31",""", ""))
        errors.map(_.reason) should contain(Reason.MISSING_REQUIRED_FIELD)
        errors.flatMap(_.path) should contain("companies[0].accPeriodEnd")
        errors.size shouldBe 1
      }
    }

    "given a company missing status" should {
      "return MISSING_REQUIRED_FIELD pointing at companies[0].status" in {
        val regex               = """,\s+"status": "COMPLIANT"""".r
        val updatedNotification = regex.replaceAllIn(validNotification, "")
        val errors              = notificationErrors(updatedNotification)

        errors.map(_.reason) should contain(Reason.MISSING_REQUIRED_FIELD)
        errors.flatMap(_.path) should contain("companies[0].status")
        errors.size shouldBe 1
      }
    }

    "given a company missing type" should {
      "return MISSING_REQUIRED_FIELD pointing at companies[0].status" in {
        val regex               = """"type": "LTD",""".r
        val updatedNotification = regex.replaceAllIn(validNotification, "")
        val errors              = notificationErrors(updatedNotification)

        errors.map(_.reason) should contain(Reason.MISSING_REQUIRED_FIELD)
        errors.flatMap(_.path) should contain("companies[0].type")
        errors.size shouldBe 1
      }
    }

    "given an invalid accPeriodEnd format" should {
      "return INVALID_FORMAT pointing at accPeriodEnd" in {
        val errors = notificationErrors(
          validNotification.replace(""""accPeriodEnd": "2024-12-31"""", """"accPeriodEnd": "-"""")
        )
        errors.map(_.reason) should contain(Reason.INVALID_FORMAT)
        errors.flatMap(_.path) should contain("companies[0].accPeriodEnd")
        errors.size shouldBe 1
      }
    }

    "given an invalid enum for company type" should {
      "return INVALID_ENUM_VALUE pointing at type" in {
        val errors = notificationErrors(
          validNotification.replace(""""type": "LTD"""", """"type": """"")
        )
        errors.map(_.reason) should contain(Reason.INVALID_ENUM_VALUE)
        errors.flatMap(_.path) should contain("companies[0].type")
        errors.size shouldBe 1
      }
    }

    "given a sao missing name" should {
      "return MISSING_REQUIRED_FIELD pointing at saos[0].name" in {
        val errors = notificationErrors(validNotification.replaceAll(s""""name": "Firstname Lastname",""", ""))
        errors.map(_.reason) should contain(Reason.MISSING_REQUIRED_FIELD)
        errors.flatMap(_.path) should contain("saos[0].name")
        errors.size shouldBe 1
      }
    }
  }
}
