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

package uk.gov.hmrc.senioraccountingofficer.controllers
import org.apache.pekko.util.ByteString
import org.scalatest.concurrent.Eventually
import play.api.http.HeaderNames
import play.api.libs.ws.WSResponse
import play.api.libs.ws.readableAsString
import play.api.libs.ws.{BodyWritable, InMemoryBody}
import support.*
import support.MockAuthHelper
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.senioraccountingofficer.controllers.NotificationControllerISpec.*
import uk.gov.hmrc.senioraccountingofficer.utils.TestDataGenerator.*

import java.time.LocalDate
import java.time.format.DateTimeFormatter

class NotificationControllerISpec extends ISpecBase with Eventually {

  given HeaderCarrier = HeaderCarrier()

  override def additionalConfigs: Map[String, Any] = Map(
    "microservice.services.hip.host"                        -> wireMockHost,
    "microservice.services.hip.port"                        -> wireMockPort,
    "microservice.services.object-store.host"               -> wireMockHost,
    "microservice.services.object-store.port"               -> wireMockPort,
    "microservice.services.email.host"                      -> wireMockHost,
    "microservice.services.email.port"                      -> wireMockPort,
    "microservice.services.secure-data-exchange-proxy.host" -> wireMockHost,
    "microservice.services.secure-data-exchange-proxy.port" -> wireMockPort
  )

  given BodyWritable[String] = rawStringWriter("application/json")

  def makeRequest(body: String): WSResponse = {
    wsClient
      .url(s"$baseUrl/senior-accounting-officer/notification")
      .withHttpHeaders(
        HeaderNames.AUTHORIZATION -> MockAuthHelper.testBearerToken,
        "correlationId"           -> correlationId
      )
      .post(body)
      .futureValue
  }

  "POST /notification" when {

    "Succeeds" when {
      "returns 200" must {
        "CRMM gives us a customer id" in {
          MockAuthHelper.mockAuthOk()
          GetSubscriptionHelper.mock(MockAuthHelper.testSubscriptionId, 200, Some(getSubscriptionResponse))
          RetrieveCustomerHelper.mock(200, Some(retrieveCustomerResponseCustomerFound))
          SubmitNotificationHelper.mock(MockAuthHelper.testSubscriptionId, 201, Some(submitNotificationResponse))
          EmailHelper.mock(200)
          ObjectStoreHelper.mockPdfUpload(
            pdfFilename,
            200,
            Some(objectStoreUploadResponse)
          )
          ObjectStoreHelper.mockPdfRetrieval(pdfFilename, 200, Some(objectStoreUploadResponse))
          ObjectStoreHelper.mockZipUpload(
            notificationReference,
            200,
            Some(objectStoreUploadResponse)
          )
          SdesHelper.mock(200, None)

          val response = makeRequest(requestBody)

          response.status mustBe 200
          response.body[String] mustBe controllerSuccessResponse

          GetSubscriptionHelper.verifyCalled(MockAuthHelper.testSubscriptionId, 1)
          RetrieveCustomerHelper.verifyCalled(retrieveCustomerRequest, 1)
          SubmitNotificationHelper.verifyCalled(
            MockAuthHelper.testSubscriptionId,
            Some(submitNotificationRequestWithCustomerId),
            1
          )

          eventually {
            EmailHelper.verifyCalled(None, 2)
            EmailHelper.verifyCalled(Some(emailRequestFirstContact), 1)
            EmailHelper.verifyCalled(Some(emailRequestSecondContact), 1)
            ObjectStoreHelper.verifyPdfUpload(pdfFilename, 1)
            ObjectStoreHelper.verifyPdfRetrieval(pdfFilename, 1)
            ObjectStoreHelper.verifyZipUpload(notificationReference, 1)
            SdesHelper.verifyCalled(sdesRequest, 1)
          }
        }

        "CRMM does not give us a customer id" in {
          MockAuthHelper.mockAuthOk()
          GetSubscriptionHelper.mock(MockAuthHelper.testSubscriptionId, 200, Some(getSubscriptionResponse))
          RetrieveCustomerHelper.mock(200, Some(retrieveCustomerResponseCustomerNotFound))
          SubmitNotificationHelper.mock(MockAuthHelper.testSubscriptionId, 201, Some(submitNotificationResponse))
          EmailHelper.mock(200)
          ObjectStoreHelper.mockPdfUpload(
            pdfFilename,
            200,
            Some(objectStoreUploadResponse)
          )
          ObjectStoreHelper.mockPdfRetrieval(pdfFilename, 200, Some(objectStoreUploadResponse))
          ObjectStoreHelper.mockZipUpload(
            notificationReference,
            200,
            Some(objectStoreUploadResponse)
          )
          SdesHelper.mock(200, None)

          val response = makeRequest(requestBody)

          response.status mustBe 200
          response.body[String] mustBe controllerSuccessResponse

          GetSubscriptionHelper.verifyCalled(MockAuthHelper.testSubscriptionId, 1)
          RetrieveCustomerHelper.verifyCalled(retrieveCustomerRequest, 1)
          SubmitNotificationHelper.verifyCalled(
            MockAuthHelper.testSubscriptionId,
            Some(submitNotificationRequestWithoutCustomerId),
            1
          )

          eventually {
            EmailHelper.verifyCalled(None, 2)
            EmailHelper.verifyCalled(Some(emailRequestFirstContact), 1)
            EmailHelper.verifyCalled(Some(emailRequestSecondContact), 1)
            ObjectStoreHelper.verifyPdfUpload(pdfFilename, 1)
            ObjectStoreHelper.verifyPdfRetrieval(pdfFilename, 1)
            ObjectStoreHelper.verifyZipUpload(notificationReference, 1)
            SdesHelper.verifyCalled(sdesRequest, 1)
          }
        }
      }
    }

    "GetSubscription" when {
      "returns 200 with a response body that cannot be parsed" must {
        "return a 500 response reason DOWNSTREAM_SERVICE_MISALIGNMENT" in {
          MockAuthHelper.mockAuthOk()
          GetSubscriptionHelper.mock(MockAuthHelper.testSubscriptionId, 200, Some(unparsableResponse))

          val response = makeRequest(requestBody)

          response.status mustBe 500
          response.body[String] mustBe s"""{"reason":"DOWNSTREAM_SERVICE_MISALIGNMENT"}"""

          GetSubscriptionHelper.verifyCalled(MockAuthHelper.testSubscriptionId, 1)
          RetrieveCustomerHelper.verifyCalled(retrieveCustomerRequest, 0)
          SubmitNotificationHelper.verifyCalled(MockAuthHelper.testSubscriptionId, None, 0)

          eventually {
            EmailHelper.verifyCalled(None, 0)
            ObjectStoreHelper.verifyPdfUpload(pdfFilename, 0)
            ObjectStoreHelper.verifyPdfRetrieval(pdfFilename, 0)
            ObjectStoreHelper.verifyZipUpload(zipFilename, 0)
            SdesHelper.verifyCalled(sdesRequest, 0)
          }
        }
      }

      List(
        (400, 500, "DOWNSTREAM_SERVICE_MISALIGNMENT"),
        (401, 500, "SERVICE_MISCONFIGURATION"),
        (403, 500, "SERVICE_MISCONFIGURATION"),
        (500, 502, "DOWNSTREAM_SERVICE_ERROR"),
        (503, 502, "DOWNSTREAM_SERVICE_UNAVAILABLE"),
        (618, 502, "DOWNSTREAM_SERVICE_MISALIGNMENT")
      ).foreach((downstreamResponseCode, expectedResponseCode, expectedReason) => {
        s"returns $downstreamResponseCode" must {
          s"return a $expectedResponseCode response reason $expectedReason" in {
            MockAuthHelper.mockAuthOk()
            GetSubscriptionHelper.mock(MockAuthHelper.testSubscriptionId, downstreamResponseCode, None)

            val response = makeRequest(requestBody)

            response.status mustBe expectedResponseCode
            response.body[String] mustBe s"""{"reason":"$expectedReason"}"""

            GetSubscriptionHelper.verifyCalled(MockAuthHelper.testSubscriptionId, 1)
            RetrieveCustomerHelper.verifyCalled(retrieveCustomerRequest, 0)
            SubmitNotificationHelper.verifyCalled(MockAuthHelper.testSubscriptionId, None, 0)

            eventually {
              EmailHelper.verifyCalled(None, 0)
              ObjectStoreHelper.verifyPdfUpload(pdfFilename, 0)
              ObjectStoreHelper.verifyPdfRetrieval(pdfFilename, 0)
              ObjectStoreHelper.verifyZipUpload(zipFilename, 0)
              SdesHelper.verifyCalled(sdesRequest, 0)
            }
          }
        }
      })
    }

    "RetrieveCustomer" when {
      "returns 200 with a response body that cannot be parsed" must {
        "return a 500 response reason DOWNSTREAM_SERVICE_MISALIGNMENT" in {
          MockAuthHelper.mockAuthOk()
          GetSubscriptionHelper.mock(MockAuthHelper.testSubscriptionId, 200, Some(getSubscriptionResponse))
          RetrieveCustomerHelper.mock(200, Some(unparsableResponse))

          val response = makeRequest(requestBody)

          response.status mustBe 500
          response.body[String] mustBe s"""{"reason":"DOWNSTREAM_SERVICE_MISALIGNMENT"}"""

          GetSubscriptionHelper.verifyCalled(MockAuthHelper.testSubscriptionId, 1)
          RetrieveCustomerHelper.verifyCalled(retrieveCustomerRequest, 1)
          SubmitNotificationHelper.verifyCalled(MockAuthHelper.testSubscriptionId, None, 0)

          eventually {
            EmailHelper.verifyCalled(None, 0)
            ObjectStoreHelper.verifyPdfUpload(pdfFilename, 0)
            ObjectStoreHelper.verifyPdfRetrieval(pdfFilename, 0)
            ObjectStoreHelper.verifyZipUpload(zipFilename, 0)
            SdesHelper.verifyCalled(sdesRequest, 0)
          }
        }
      }

      List(
        (400, 500, "DOWNSTREAM_SERVICE_MISALIGNMENT"),
        (401, 500, "SERVICE_MISCONFIGURATION"),
        (403, 500, "SERVICE_MISCONFIGURATION"),
        (500, 502, "DOWNSTREAM_SERVICE_ERROR"),
        (503, 502, "DOWNSTREAM_SERVICE_UNAVAILABLE"),
        (618, 502, "DOWNSTREAM_SERVICE_MISALIGNMENT")
      ).foreach((downstreamResponseCode, expectedResponseCode, expectedReason) => {
        s"returns $downstreamResponseCode" must {
          s"return a $expectedResponseCode response reason $expectedReason" in {
            MockAuthHelper.mockAuthOk()
            GetSubscriptionHelper.mock(MockAuthHelper.testSubscriptionId, 200, Some(getSubscriptionResponse))
            RetrieveCustomerHelper.mock(downstreamResponseCode, None)

            val response = makeRequest(requestBody)

            response.status mustBe expectedResponseCode
            response.body[String] mustBe s"""{"reason":"$expectedReason"}"""

            GetSubscriptionHelper.verifyCalled(MockAuthHelper.testSubscriptionId, 1)
            RetrieveCustomerHelper.verifyCalled(retrieveCustomerRequest, 1)
            SubmitNotificationHelper.verifyCalled(MockAuthHelper.testSubscriptionId, None, 0)

            eventually {
              EmailHelper.verifyCalled(None, 0)
              ObjectStoreHelper.verifyPdfUpload(pdfFilename, 0)
              ObjectStoreHelper.verifyPdfRetrieval(pdfFilename, 0)
              ObjectStoreHelper.verifyZipUpload(zipFilename, 0)
              SdesHelper.verifyCalled(sdesRequest, 0)
            }
          }
        }
      })
    }

    "SubmitNotification" when {
      "returns 201 with a response body that cannot be parsed" must {
        "return a 500 response reason DOWNSTREAM_SERVICE_MISALIGNMENT" in {
          MockAuthHelper.mockAuthOk()
          GetSubscriptionHelper.mock(MockAuthHelper.testSubscriptionId, 200, Some(getSubscriptionResponse))
          RetrieveCustomerHelper.mock(200, Some(retrieveCustomerResponseCustomerFound))
          SubmitNotificationHelper.mock(MockAuthHelper.testSubscriptionId, 201, Some(unparsableResponse))

          val response = makeRequest(requestBody)

          response.status mustBe 500
          response.body[String] mustBe s"""{"reason":"DOWNSTREAM_SERVICE_MISALIGNMENT"}"""

          GetSubscriptionHelper.verifyCalled(MockAuthHelper.testSubscriptionId, 1)
          RetrieveCustomerHelper.verifyCalled(retrieveCustomerRequest, 1)
          SubmitNotificationHelper.verifyCalled(
            MockAuthHelper.testSubscriptionId,
            Some(submitNotificationRequestWithCustomerId),
            1
          )

          eventually {
            EmailHelper.verifyCalled(None, 0)
            ObjectStoreHelper.verifyPdfUpload(pdfFilename, 0)
            ObjectStoreHelper.verifyPdfRetrieval(pdfFilename, 0)
            ObjectStoreHelper.verifyZipUpload(zipFilename, 0)
            SdesHelper.verifyCalled(sdesRequest, 0)
          }
        }
      }

      List(
        (400, 500, "DOWNSTREAM_SERVICE_MISALIGNMENT"),
        (401, 500, "SERVICE_MISCONFIGURATION"),
        (403, 500, "SERVICE_MISCONFIGURATION"),
        (500, 502, "DOWNSTREAM_SERVICE_ERROR"),
        (503, 502, "DOWNSTREAM_SERVICE_UNAVAILABLE"),
        (618, 502, "DOWNSTREAM_SERVICE_MISALIGNMENT")
      ).foreach((downstreamResponseCode, expectedResponseCode, expectedReason) => {
        s"returns $downstreamResponseCode" must {
          s"return a $expectedResponseCode response reason $expectedReason" in {
            MockAuthHelper.mockAuthOk()
            GetSubscriptionHelper.mock(MockAuthHelper.testSubscriptionId, 200, Some(getSubscriptionResponse))
            RetrieveCustomerHelper.mock(200, Some(retrieveCustomerResponseCustomerFound))
            SubmitNotificationHelper.mock(MockAuthHelper.testSubscriptionId, downstreamResponseCode, None)

            val response = makeRequest(requestBody)

            response.status mustBe expectedResponseCode
            response.body[String] mustBe s"""{"reason":"$expectedReason"}"""

            GetSubscriptionHelper.verifyCalled(MockAuthHelper.testSubscriptionId, 1)
            RetrieveCustomerHelper.verifyCalled(retrieveCustomerRequest, 1)
            SubmitNotificationHelper.verifyCalled(
              MockAuthHelper.testSubscriptionId,
              Some(submitNotificationRequestWithCustomerId),
              1
            )

            eventually {
              EmailHelper.verifyCalled(None, 0)
              ObjectStoreHelper.verifyPdfUpload(pdfFilename, 0)
              ObjectStoreHelper.verifyPdfRetrieval(pdfFilename, 0)
              ObjectStoreHelper.verifyZipUpload(zipFilename, 0)
              SdesHelper.verifyCalled(sdesRequest, 0)
            }
          }
        }
      })
    }

    "Email" when {
      "Fails" must {
        "despite the failure return 200 and continue to upload to object store" in {
          MockAuthHelper.mockAuthOk()
          GetSubscriptionHelper.mock(MockAuthHelper.testSubscriptionId, 200, Some(getSubscriptionResponse))
          RetrieveCustomerHelper.mock(200, Some(retrieveCustomerResponseCustomerFound))
          SubmitNotificationHelper.mock(MockAuthHelper.testSubscriptionId, 201, Some(submitNotificationResponse))
          EmailHelper.mock(400)
          ObjectStoreHelper.mockPdfUpload(pdfFilename, 400, None)

          val response = makeRequest(requestBody)

          response.status mustBe 200
          response.body[String] mustBe controllerSuccessResponse

          GetSubscriptionHelper.verifyCalled(MockAuthHelper.testSubscriptionId, 1)
          RetrieveCustomerHelper.verifyCalled(retrieveCustomerRequest, 1)
          SubmitNotificationHelper.verifyCalled(
            MockAuthHelper.testSubscriptionId,
            Some(submitNotificationRequestWithCustomerId),
            1
          )

          eventually {
            EmailHelper.verifyCalled(Some(emailRequestFirstContact), 1)
            ObjectStoreHelper.verifyPdfUpload(pdfFilename, 1)
            ObjectStoreHelper.verifyPdfRetrieval(pdfFilename, 0)
            ObjectStoreHelper.verifyZipUpload(zipFilename, 0)
            SdesHelper.verifyCalled(sdesRequest, 0)
          }
        }
      }
    }

    "ObjectStore" when {
      "Uploading the pdf" when {
        "Fails" must {
          "return 200 despite the failure" in {
            MockAuthHelper.mockAuthOk()
            GetSubscriptionHelper.mock(MockAuthHelper.testSubscriptionId, 200, Some(getSubscriptionResponse))
            RetrieveCustomerHelper.mock(200, Some(retrieveCustomerResponseCustomerFound))
            SubmitNotificationHelper.mock(MockAuthHelper.testSubscriptionId, 201, Some(submitNotificationResponse))
            EmailHelper.mock(200)
            ObjectStoreHelper.mockPdfUpload(pdfFilename, 400, None)

            val response = makeRequest(requestBody)

            response.status mustBe 200
            response.body[String] mustBe controllerSuccessResponse

            GetSubscriptionHelper.verifyCalled(MockAuthHelper.testSubscriptionId, 1)
            RetrieveCustomerHelper.verifyCalled(retrieveCustomerRequest, 1)
            SubmitNotificationHelper.verifyCalled(
              MockAuthHelper.testSubscriptionId,
              Some(submitNotificationRequestWithCustomerId),
              1
            )

            eventually {
              EmailHelper.verifyCalled(None, 2)
              EmailHelper.verifyCalled(Some(emailRequestFirstContact), 1)
              EmailHelper.verifyCalled(Some(emailRequestSecondContact), 1)
              ObjectStoreHelper.verifyPdfUpload(pdfFilename, 1)
              ObjectStoreHelper.verifyPdfRetrieval(pdfFilename, 0)
              ObjectStoreHelper.verifyZipUpload(zipFilename, 0)
              SdesHelper.verifyCalled(sdesRequest, 0)
            }
          }
        }
      }

      "Retrieving the pdf" when {
        "Fails" must {
          "return 200 despite the failure" in {
            MockAuthHelper.mockAuthOk()
            GetSubscriptionHelper.mock(MockAuthHelper.testSubscriptionId, 200, Some(getSubscriptionResponse))
            RetrieveCustomerHelper.mock(200, Some(retrieveCustomerResponseCustomerFound))
            SubmitNotificationHelper.mock(MockAuthHelper.testSubscriptionId, 201, Some(submitNotificationResponse))
            EmailHelper.mock(200)
            ObjectStoreHelper.mockPdfUpload(
              pdfFilename,
              200,
              Some(objectStoreUploadResponse)
            )
            ObjectStoreHelper.mockPdfRetrieval(pdfFilename, 400, None)

            val response = makeRequest(requestBody)

            response.status mustBe 200
            response.body[String] mustBe controllerSuccessResponse

            GetSubscriptionHelper.verifyCalled(MockAuthHelper.testSubscriptionId, 1)
            RetrieveCustomerHelper.verifyCalled(retrieveCustomerRequest, 1)
            SubmitNotificationHelper.verifyCalled(
              MockAuthHelper.testSubscriptionId,
              Some(submitNotificationRequestWithCustomerId),
              1
            )

            eventually {
              EmailHelper.verifyCalled(None, 2)
              EmailHelper.verifyCalled(Some(emailRequestFirstContact), 1)
              EmailHelper.verifyCalled(Some(emailRequestSecondContact), 1)
              ObjectStoreHelper.verifyPdfUpload(pdfFilename, 1)
              ObjectStoreHelper.verifyPdfRetrieval(pdfFilename, 1)
              ObjectStoreHelper.verifyZipUpload(zipFilename, 0)
              SdesHelper.verifyCalled(sdesRequest, 0)
            }
          }
        }
      }

      "Uploading the zip" when {
        "Fails" must {
          "return 200 despite the failure" in {
            MockAuthHelper.mockAuthOk()
            GetSubscriptionHelper.mock(MockAuthHelper.testSubscriptionId, 200, Some(getSubscriptionResponse))
            RetrieveCustomerHelper.mock(200, Some(retrieveCustomerResponseCustomerFound))
            SubmitNotificationHelper.mock(MockAuthHelper.testSubscriptionId, 201, Some(submitNotificationResponse))
            EmailHelper.mock(200)
            ObjectStoreHelper.mockPdfUpload(
              pdfFilename,
              200,
              Some(objectStoreUploadResponse)
            )
            ObjectStoreHelper.mockPdfRetrieval(pdfFilename, 200, Some(objectStoreUploadResponse))
            ObjectStoreHelper.mockZipUpload(
              notificationReference,
              400,
              Some(objectStoreUploadResponse)
            )

            val response = makeRequest(requestBody)

            response.status mustBe 200
            response.body[String] mustBe controllerSuccessResponse

            GetSubscriptionHelper.verifyCalled(MockAuthHelper.testSubscriptionId, 1)
            RetrieveCustomerHelper.verifyCalled(retrieveCustomerRequest, 1)
            SubmitNotificationHelper.verifyCalled(
              MockAuthHelper.testSubscriptionId,
              Some(submitNotificationRequestWithCustomerId),
              1
            )

            eventually {
              EmailHelper.verifyCalled(None, 2)
              EmailHelper.verifyCalled(Some(emailRequestFirstContact), 1)
              EmailHelper.verifyCalled(Some(emailRequestSecondContact), 1)
              ObjectStoreHelper.verifyPdfUpload(pdfFilename, 1)
              ObjectStoreHelper.verifyPdfRetrieval(pdfFilename, 1)
              ObjectStoreHelper.verifyZipUpload(notificationReference, 1)
              SdesHelper.verifyCalled(sdesRequest, 0)
            }
          }
        }
      }
    }

    "SDES" when {
      "Fails" must {
        "return 200 despite the failure" in {
          MockAuthHelper.mockAuthOk()
          GetSubscriptionHelper.mock(MockAuthHelper.testSubscriptionId, 200, Some(getSubscriptionResponse))
          RetrieveCustomerHelper.mock(200, Some(retrieveCustomerResponseCustomerFound))
          SubmitNotificationHelper.mock(MockAuthHelper.testSubscriptionId, 201, Some(submitNotificationResponse))
          EmailHelper.mock(200)
          ObjectStoreHelper.mockPdfUpload(
            pdfFilename,
            200,
            Some(objectStoreUploadResponse)
          )
          ObjectStoreHelper.mockPdfRetrieval(pdfFilename, 200, Some(objectStoreUploadResponse))
          ObjectStoreHelper.mockZipUpload(
            notificationReference,
            200,
            Some(objectStoreUploadResponse)
          )
          SdesHelper.mock(400, None)

          val response = makeRequest(requestBody)

          response.status mustBe 200
          response.body[String] mustBe controllerSuccessResponse

          GetSubscriptionHelper.verifyCalled(MockAuthHelper.testSubscriptionId, 1)
          RetrieveCustomerHelper.verifyCalled(retrieveCustomerRequest, 1)
          SubmitNotificationHelper.verifyCalled(
            MockAuthHelper.testSubscriptionId,
            Some(submitNotificationRequestWithCustomerId),
            1
          )

          eventually {
            EmailHelper.verifyCalled(None, 2)
            EmailHelper.verifyCalled(Some(emailRequestFirstContact), 1)
            EmailHelper.verifyCalled(Some(emailRequestSecondContact), 1)
            ObjectStoreHelper.verifyPdfUpload(pdfFilename, 1)
            ObjectStoreHelper.verifyPdfRetrieval(pdfFilename, 1)
            ObjectStoreHelper.verifyZipUpload(notificationReference, 1)
            SdesHelper.verifyCalled(sdesRequest, 1)
          }
        }
      }
    }
  }
}

object NotificationControllerISpec {
  def rawStringWriter(contentType: String): BodyWritable[String] =
    BodyWritable(
      str => InMemoryBody(ByteString(str)),
      contentType
    )

  val unparsableResponse = "{"

  val requestBody = """{
                      |  "companies": [
                      |    {
                      |      "crn": "65476489",
                      |      "utr": "1233456679",
                      |      "name": "TestCompanyName",
                      |      "accPeriodEnd": "2009-09-09",
                      |      "status": "Dormant",
                      |      "type": "LTD"
                      |    }
                      |  ],
                      |  "saos": [
                      |    {
                      |      "name": "Testname",
                      |      "fromDate": "2020-03-10",
                      |      "toDate": "2021-03-10"
                      |    }
                      |  ],
                      |  "remarks":"testremarks"
                      |}""".stripMargin

  val crn = generateCrn

  val utr = generateUtr

  val firstContactName   = "Firstname Lastname"
  val firstContactEmail  = "Firstname.Lastname@example.com"
  val secondContactName  = "Firstname Lastname II"
  val secondContactEmail = "Firstname.Lastname.II@example.com"

  val getSubscriptionResponse = s"""{
                                   |  "etmpSafeId": "1234567890",
                                   |  "contacts": [
                                   |    {
                                   |      "name": "$firstContactName",
                                   |      "email": "$firstContactEmail",
                                   |      "language": "en",
                                   |      "status": "valid"
                                   |    },
                                   |    {
                                   |      "name": "$secondContactName",
                                   |      "email": "$secondContactEmail",
                                   |      "language": "cy",
                                   |      "status": "valid"
                                   |    }
                                   |  ],
                                   |  "nominatedCompany": {
                                   |    "crn": "$crn",
                                   |    "name": "Fake Company Ltd",
                                   |    "utr": "$utr"
                                   |  }
                                   |}""".stripMargin

  val customerId = "02839521"

  val retrieveCustomerResponseCustomerFound = s"""{
                                                 |  "customerId": "$customerId",
                                                 |  "existingCustomer": true,
                                                 |  "status": "Success"
                                                 |}""".stripMargin

  val retrieveCustomerResponseCustomerNotFound = """{
                                                   |  "errorDescription": "error",
                                                   |  "existingCustomer": false,
                                                   |  "status": "Failure"
                                                   |}""".stripMargin

  val retrieveCustomerRequest = s"""{
                                                   |  "companyRegistrationNumber": "$crn",
                                                   |  "uniqueTaxReference": "$utr"
                                                   |}""".stripMargin

  val notificationReference = "NOT0008470194"

  val correlationId = "d80fd83a-2c50-4967-b596-675f7f11e241"

  val submitNotificationRequestWithoutCustomerId = s"""{
                                                      |  "remarks": "testremarks",
                                                      |  "companies": [
                                                      |    {
                                                      |      "crn": "65476489",
                                                      |      "utr": "1233456679",
                                                      |      "name": "TestCompanyName",
                                                      |      "accPeriodEnd": "2009-09-09",
                                                      |      "status": "Dormant",
                                                      |      "type": "LTD"
                                                      |    }
                                                      |  ],
                                                      |  "saos": [
                                                      |    {
                                                      |      "name": "Testname",
                                                      |      "fromDate": "2020-03-10",
                                                      |      "toDate": "2021-03-10"
                                                      |    }
                                                      |  ]
                                                      |}""".stripMargin

  val submitNotificationRequestWithCustomerId = s"""{
                                                   |  "remarks": "testremarks",
                                                   |  "companies": [
                                                   |    {
                                                   |      "crn": "65476489",
                                                   |      "utr": "1233456679",
                                                   |      "name": "TestCompanyName",
                                                   |      "accPeriodEnd": "2009-09-09",
                                                   |      "status": "Dormant",
                                                   |      "type": "LTD"
                                                   |    }
                                                   |  ],
                                                   |  "customerId": "$customerId",
                                                   |  "saos": [
                                                   |    {
                                                   |      "name": "Testname",
                                                   |      "fromDate": "2020-03-10",
                                                   |      "toDate": "2021-03-10"
                                                   |    }
                                                   |  ]
                                                   |}""".stripMargin

  val submitNotificationResponse = s"""{
                                      |  "notificationRef": "$notificationReference"
                                      |}""".stripMargin

  val emailRequestFirstContact = s"""{
                                    |  "to": [
                                    |    "$firstContactEmail"
                                    |  ],
                                    |  "templateId": "dsao_notification_confirmation",
                                    |  "parameters": {
                                    |    "recipientName": "$firstContactName",
                                    |    "companyName": "Fake Company Ltd",
                                    |    "referenceId": "$notificationReference"
                                    |  }
                                    |}""".stripMargin

  val emailRequestSecondContact = s"""{
                                     |  "to": [
                                     |    "$secondContactEmail"
                                     |  ],
                                     |  "templateId": "dsao_notification_confirmation",
                                     |  "parameters": {
                                     |    "recipientName": "$secondContactName",
                                     |    "companyName": "Fake Company Ltd",
                                     |    "referenceId": "$notificationReference"
                                     |  }
                                     |}""".stripMargin

  val objectStoreUploadResponse = """{
                                    |  "contentLength": 0,
                                    |  "contentMD5": "abc",
                                    |  "lastModified": "2026-08-17T10:32:01Z",
                                    |  "location": "/some/path"
                                    |}""".stripMargin

  val pdfFilename = s"${notificationReference}_SAO_Notification.pdf"

  val zipFilename =
    s"${LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE)}_${notificationReference}_SAO_Notification_OFFICIAL_SENSITIVE.zip"

  val zipLocation =
    s"object-store/object/senior-accounting-officer/sdes/$notificationReference/$zipFilename"

  val controllerSuccessResponse = s"""{"notificationRef":"$notificationReference"}"""

  val sdesRequest = s"""{
                       |  "informationType" : "DSAO",
                       |  "file" : {
                       |    "recipientOrSender" : "Documentum",
                       |    "name" : "$zipFilename",
                       |    "location" : "https://hsopriv.hmrc.gov.uk/$zipLocation",
                       |    "checksum" : {
                       |      "algorithm" : "md5",
                       |      "value" : "69b7"
                       |    },
                       |    "size" : 0,
                       |    "properties" : [ ]
                       |  },
                       |  "audit" : {
                       |    "correlationID" : "$correlationId"
                       |  }
                       |}""".stripMargin
}
