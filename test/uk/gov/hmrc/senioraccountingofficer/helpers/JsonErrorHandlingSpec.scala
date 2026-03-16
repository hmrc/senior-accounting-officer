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
import play.api.libs.json.{JsNull, Json, __}

class JsonErrorHandlingSpec extends AnyWordSpec with Matchers {

  // --- basic Json tests
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

  private def certificateErrors(json: String): Seq[JsonErrorHandling.ApiError] =
    JsonErrorHandling.Validators.validateCertificate(Json.parse(json))

  private def contactDetailsErrors(json: String): Seq[JsonErrorHandling.ApiError] =
    JsonErrorHandling.Validators.validateContactDetails(Json.parse(json))

  private def subscriptionErrors(json: String): Seq[JsonErrorHandling.ApiError] =
    JsonErrorHandling.Validators.validateSubscription(Json.parse(json))

  private def notificationErrors(json: String): Seq[JsonErrorHandling.ApiError] =
    JsonErrorHandling.Validators.validateNotification(Json.parse(json))

  "Certificate validation" when {

    val validCertificate =
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
        val updatedJsonStr = Json.parse(validCertificate).transform(remover).get.toString
        val errors         = certificateErrors(updatedJsonStr)
        errors.map(_.reason) should contain("MISSING_REQUIRED_FIELD")
        errors.flatMap(_.path) should contain("declaration")
      }
    }

    "given a certificate is missing companies" should {
      "return MISSING_REQUIRED_FIELD pointing at companies" in {
        val remover        = (__ \ "companies").json.prune
        val updatedJsonStr = Json.parse(validCertificate).transform(remover).get.toString
        val errors         = certificateErrors(updatedJsonStr)
        errors.map(_.reason) should contain("MISSING_REQUIRED_FIELD")
        errors.flatMap(_.path) should contain("companies")
      }
    }
  }

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
      "return MISSING_REQUIRED_FIELD pointing at 0.name" in {
        val errors = contactDetailsErrors("""[{"email":"alice@example.com"}]""")
        errors.map(_.reason) should contain("MISSING_REQUIRED_FIELD")
        errors.flatMap(_.path) should contain("[0].name")
      }
    }

    "given a contact missing email" should {
      "return MISSING_REQUIRED_FIELD pointing at 0.email" in {
        val errors = contactDetailsErrors("""[{"name":"Alice"}]""")
        errors.map(_.reason) should contain("MISSING_REQUIRED_FIELD")
        errors.flatMap(_.path) should contain("[0].email")
      }
    }

    "given a contact where name is not a string" should {
      "return INVALID_DATA_TYPE for 0.name" in {
        val errors = contactDetailsErrors("""[{"name":123,"email":"alice@example.com"}]""")
        errors.map(_.reason) should contain("INVALID_DATA_TYPE")
        errors.flatMap(_.path) should contain("[0].name")
      }
    }

    "given an unexpected additional property" should {
      "return INVALID_DATA_TYPE for the extra field path" in {
        val errors = contactDetailsErrors(
          """[{"name":"Alice","email":"alice@example.com","unexpected":"value"}]"""
        )
        errors.map(_.reason) should contain("INVALID_DATA_TYPE")
        errors.flatMap(_.path) should contain("[0].unexpected")
      }
    }

    "given a contact with an invalid email" should {
      "return INVALID_FORMAT for 0.email" in {
        val errors = contactDetailsErrors("""[{"name":"Alice","email":"not-an-email"}]""")
        errors.map(_.reason) should contain("INVALID_FORMAT")
        errors.flatMap(_.path) should contain("[0].email")
      }
    }

    "given a contact with an empty name string" should {
      "return CANNOT_BE_EMPTY for 0.name" in {
        val errors = contactDetailsErrors("""[{"name":"","email":"alice@example.com"}]""")
        errors.map(_.reason) should contain("CANNOT_BE_EMPTY")
        errors.flatMap(_.path) should contain("[0].name")
      }
    }

    "given a contact with an empty email string" should {
      "return CANNOT_BE_EMPTY for 0.email" in {
        val errors = contactDetailsErrors("""[{"name":"Alice","email":""}]""")
        errors.map(_.reason) should contain("CANNOT_BE_EMPTY")
        errors.flatMap(_.path) should contain("[0].email")
      }
    }

    "given a name exceeding 120 characters" should {
      "return LENGTH_OUT_OF_BOUNDS for 0.name" in {
        val errors = contactDetailsErrors(
          s"""[{"name":"${"A" * 121}","email":"alice@example.com"}]"""
        )
        errors.map(_.reason) should contain("LENGTH_OUT_OF_BOUNDS")
        errors.flatMap(_.path) should contain("[0].name")
      }
    }

    "given an email exceeding 254 characters" should {
      "return LENGTH_OUT_OF_BOUNDS for 0.email" in {
        val errors = contactDetailsErrors(
          s"""[{"name":"Alice","email":"${"a" * 250}@x.com"}]"""
        )
        errors.map(_.reason) should contain("LENGTH_OUT_OF_BOUNDS")
        errors.flatMap(_.path) should contain("[0].email")
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
      }
    }
  }

  "Subscription validation" when {

    val validSubscription =
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
        val updatedJsonStr = Json.parse(validSubscription).transform(remover).get.toString
        val errors         = subscriptionErrors(updatedJsonStr)
        errors.map(_.reason) should contain("MISSING_REQUIRED_FIELD")
        errors.flatMap(_.path) should contain("company")
      }
    }

    "given a payload missing safeId" should {
      "return MISSING_REQUIRED_FIELD pointing at safeId" in {
        val remover        = (__ \ "safeId").json.prune
        val updatedJsonStr = Json.parse(validSubscription).transform(remover).get.toString
        val errors         = subscriptionErrors(updatedJsonStr)
        errors.map(_.reason) should contain("MISSING_REQUIRED_FIELD")
        errors.flatMap(_.path) should contain("safeId")
      }
    }

  }

  "Notification validation" when {

    val validNotification =
      """{
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

    "given a fully valid payload" should {
      "return no errors" in {
        notificationErrors(validNotification) shouldBe empty
      }
    }

    "given a payload missing companies" should {
      "return MISSING_REQUIRED_FIELD pointing at companies" in {
        val remover        = (__ \ "companies").json.prune
        val updatedJsonStr = Json.parse(validNotification).transform(remover).get.toString
        val errors         = notificationErrors(updatedJsonStr)
        errors.map(_.reason) should contain("MISSING_REQUIRED_FIELD")
        errors.flatMap(_.path) should contain("companies")
      }
    }

    "given a company missing uniqueTaxReference" should {
      "return MISSING_REQUIRED_FIELD pointing at companies[0].uniqueTaxReference" in {
        val errors = notificationErrors(validNotification.replace(""""uniqueTaxReference": "1234567890",""", ""))
        errors.map(_.reason) should contain("MISSING_REQUIRED_FIELD")
        errors.flatMap(_.path) should contain("companies[0].uniqueTaxReference")
      }
    }

    "given a seniorAccountingOfficer missing startDate" should {
      "return MISSING_REQUIRED_FIELD pointing at companies[0].seniorAccountingOfficers[0].startDate" in {
        val errors = notificationErrors(validNotification.replace(""""startDate": "2024-04-01",""", ""))
        errors.map(_.reason) should contain("MISSING_REQUIRED_FIELD")
        errors.flatMap(_.path) should contain("companies[0].seniorAccountingOfficers[0].startDate")
      }
    }
  }
}
