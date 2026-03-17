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

class JsonErrorHandlingCertificateSpec extends AnyWordSpec with Matchers with OptionValues {

  private def certificateErrors(json: String): Seq[JsonErrorHandling.ApiError] =
    JsonErrorHandling.Validators.validateCertificate(Json.parse(json))

  private val validCertificate =
    """{
      |  "declaration": {
      |    "seniorAccountingOfficer": {
      |      "name": "John Doe",
      |      "email": "john.doe@example.com"
      |    },
      |    "proxy": {
      |      "name": "Jane Smith"
      |    }
      |  },
      |  "companies": [
      |    {
      |      "companyName": "Example Ltd",
      |      "uniqueTaxReference": "1234567890",
      |      "companyReferenceNumber": "AB123456",
      |      "companyType": "LTD",
      |      "financialYearEndDate": "2024-12-31",
      |      "seniorAccountingOfficers": [
      |        {
      |          "name": "Firstname Lastname",
      |          "email": "Firstname.Lastname@example.com",
      |          "startDate": "2024-04-01",
      |          "endDate": "2025-03-31"
      |        }
      |      ]
      |    }
      |  ],
      |  "additionalInformation": "non-empty string"
      |}""".stripMargin

  "Certificate validation" when {

    "given a fully valid payload" should {
      "return no errors" in {
        certificateErrors(validCertificate) shouldBe empty
      }
    }

    "given a valid letter-prefix companyRegistrationNumber" should {
      "return no errors" in {
        certificateErrors(
          validCertificate.replace(
            """"companyRegistrationNumber": "12345678"""",
            """"companyRegistrationNumber": "AB123456""""
          )
        ) shouldBe empty
      }
    }

    "given a certificate is missing declaration" should {
      "return MISSING_REQUIRED_FIELD pointing at declaration" in {
        val remover        = (__ \ "declaration").json.prune
        val updatedJsonStr = Json.parse(validCertificate).transform(remover).asOpt.value.toString
        val errors         = certificateErrors(updatedJsonStr)
        errors.map(_.reason) should contain("MISSING_REQUIRED_FIELD")
        errors.flatMap(_.path) should contain("declaration")
        errors.size shouldBe 1
      }
    }

    "given a certificate is missing companies" should {
      "return MISSING_REQUIRED_FIELD pointing at companies" in {
        val remover        = (__ \ "companies").json.prune
        val updatedJsonStr = Json.parse(validCertificate).transform(remover).asOpt.value.toString
        val errors         = certificateErrors(updatedJsonStr)
        errors.map(_.reason) should contain("MISSING_REQUIRED_FIELD")
        errors.flatMap(_.path) should contain("companies")
        errors.size shouldBe 1
      }
    }
  }
}
