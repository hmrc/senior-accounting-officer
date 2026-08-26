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
import org.scalatest.time.{Millis, Seconds, Span}
import play.api.http.HeaderNames
import play.api.libs.ws.WSResponse
import play.api.libs.ws.readableAsString
import play.api.libs.ws.{BodyWritable, InMemoryBody}
import support.*
import support.MockAuthHelper
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.senioraccountingofficer.controllers.CertificateControllerISpec.*
import uk.gov.hmrc.senioraccountingofficer.utils.TestDataGenerator.*

import java.time.LocalDate
import java.time.format.DateTimeFormatter

class CertificateControllerISpec extends ISpecBase with Eventually {

  override implicit val patienceConfig: PatienceConfig = PatienceConfig(
    timeout = scaled(Span(20, Seconds)),
    interval = scaled(Span(150, Millis))
  )

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
      .url(s"$baseUrl/senior-accounting-officer/certificate")
      .withHttpHeaders(
        HeaderNames.AUTHORIZATION -> MockAuthHelper.testBearerToken,
        "correlationId"           -> correlationId
      )
      .post(body)
      .futureValue
  }

  "POST /certificate" when {

    "Succeeds" when {
      "returns 201" must {
        "CRMM gives us a customer id" in {
          MockAuthHelper.mockAuthOk()
          GetSubscriptionHelper.mock(MockAuthHelper.testSubscriptionId, 200, Some(getSubscriptionResponse))
          RetrieveCustomerHelper.mock(200, Some(retrieveCustomerResponseCustomerFound))
          SubmitCertificateHelper.mock(MockAuthHelper.testSubscriptionId, 201, Some(submitCertificateResponse))
          EmailHelper.mock(200)
          ObjectStoreHelper.mockPdfUpload(
            pdfFilename,
            200,
            Some(objectStoreUploadResponse)
          )
          ObjectStoreHelper.mockPdfRetrieval(pdfFilename, 200, Some(objectStoreUploadResponse))
          ObjectStoreHelper.mockCertificateZipUpload(
            certificateReference,
            200,
            Some(objectStoreUploadResponse)
          )
          SdesHelper.mock(200, None)

          val response = makeRequest(requestBody)

          response.status mustBe 201
          response.body[String] mustBe controllerSuccessResponse

          GetSubscriptionHelper.verifyCalled(MockAuthHelper.testSubscriptionId, 1)
          RetrieveCustomerHelper.verifyCalled(retrieveCustomerRequest, 1)
          SubmitCertificateHelper.verifyCalled(
            MockAuthHelper.testSubscriptionId,
            Some(submitCertificateRequest),
            1
          )

          eventually {
            EmailHelper.verifyCalled(None, 3)
            EmailHelper.verifyCalled(Some(EmailRequests.firstUserConfirmationForSao), 1)
            EmailHelper.verifyCalled(Some(EmailRequests.secondUserConfirmationForSao), 1)
            EmailHelper.verifyCalled(Some(EmailRequests.thirdUserConfirmationForSao), 1)
            ObjectStoreHelper.verifyPdfUpload(pdfFilename, 1)
            ObjectStoreHelper.verifyPdfRetrieval(pdfFilename, 1)
            ObjectStoreHelper.verifyCertificateZipUpload(certificateReference, 1)
            SdesHelper.verifyCalled(sdesRequest, 1)
          }
        }

        "CRMM does not give us a customer id" in {
          MockAuthHelper.mockAuthOk()
          GetSubscriptionHelper.mock(MockAuthHelper.testSubscriptionId, 200, Some(getSubscriptionResponse))
          RetrieveCustomerHelper.mock(200, Some(retrieveCustomerResponseCustomerNotFound))
          SubmitCertificateHelper.mock(MockAuthHelper.testSubscriptionId, 201, Some(submitCertificateResponse))
          EmailHelper.mock(200)
          ObjectStoreHelper.mockPdfUpload(
            pdfFilename,
            200,
            Some(objectStoreUploadResponse)
          )
          ObjectStoreHelper.mockPdfRetrieval(pdfFilename, 200, Some(objectStoreUploadResponse))
          ObjectStoreHelper.mockCertificateZipUpload(
            certificateReference,
            200,
            Some(objectStoreUploadResponse)
          )
          SdesHelper.mock(200, None)

          val response = makeRequest(requestBody)

          response.status mustBe 201
          response.body[String] mustBe controllerSuccessResponse

          GetSubscriptionHelper.verifyCalled(MockAuthHelper.testSubscriptionId, 1)
          RetrieveCustomerHelper.verifyCalled(retrieveCustomerRequest, 1)
          SubmitCertificateHelper.verifyCalled(
            MockAuthHelper.testSubscriptionId,
            Some(submitCertificateRequestWithoutCustomerId),
            1
          )

          eventually {
            EmailHelper.verifyCalled(None, 3)
            EmailHelper.verifyCalled(Some(EmailRequests.firstUserConfirmationForSao), 1)
            EmailHelper.verifyCalled(Some(EmailRequests.secondUserConfirmationForSao), 1)
            EmailHelper.verifyCalled(Some(EmailRequests.thirdUserConfirmationForSao), 1)
            ObjectStoreHelper.verifyPdfUpload(pdfFilename, 1)
            ObjectStoreHelper.verifyPdfRetrieval(pdfFilename, 1)
            ObjectStoreHelper.verifyCertificateZipUpload(certificateReference, 1)
            SdesHelper.verifyCalled(sdesRequest, 1)
          }
        }

        "submitterName is provided in the request therefore the dsao_certificate_confirmation_for_submitter template is used" in {
          MockAuthHelper.mockAuthOk()
          GetSubscriptionHelper.mock(MockAuthHelper.testSubscriptionId, 200, Some(getSubscriptionResponse))
          RetrieveCustomerHelper.mock(200, Some(retrieveCustomerResponseCustomerNotFound))
          SubmitCertificateHelper.mock(MockAuthHelper.testSubscriptionId, 201, Some(submitCertificateResponse))
          EmailHelper.mock(200)
          ObjectStoreHelper.mockPdfUpload(
            pdfFilename,
            200,
            Some(objectStoreUploadResponse)
          )
          ObjectStoreHelper.mockPdfRetrieval(pdfFilename, 200, Some(objectStoreUploadResponse))
          ObjectStoreHelper.mockCertificateZipUpload(
            certificateReference,
            200,
            Some(objectStoreUploadResponse)
          )
          SdesHelper.mock(200, None)

          val response = makeRequest(requestBodyWithSubmitterName)

          response.status mustBe 201
          response.body[String] mustBe controllerSuccessResponse

          GetSubscriptionHelper.verifyCalled(MockAuthHelper.testSubscriptionId, 1)
          RetrieveCustomerHelper.verifyCalled(retrieveCustomerRequest, 1)
          SubmitCertificateHelper.verifyCalled(
            MockAuthHelper.testSubscriptionId,
            Some(submitCertificateRequestWithSubmitterName),
            1
          )

          eventually {
            EmailHelper.verifyCalled(None, 3)
            EmailHelper.verifyCalled(Some(EmailRequests.firstUserConfirmationForSubmitter), 1)
            EmailHelper.verifyCalled(Some(EmailRequests.secondUserConfirmationForSubmitter), 1)
            EmailHelper.verifyCalled(Some(EmailRequests.thirdUserConfirmationForSubmitter), 1)
            ObjectStoreHelper.verifyPdfUpload(pdfFilename, 1)
            ObjectStoreHelper.verifyPdfRetrieval(pdfFilename, 1)
            ObjectStoreHelper.verifyCertificateZipUpload(certificateReference, 1)
            SdesHelper.verifyCalled(sdesRequest, 1)
          }
        }

        "submitterName is not provided in the request therefore the dsao_certificate_confirmation_for_sao template is used" in {
          MockAuthHelper.mockAuthOk()
          GetSubscriptionHelper.mock(MockAuthHelper.testSubscriptionId, 200, Some(getSubscriptionResponse))
          RetrieveCustomerHelper.mock(200, Some(retrieveCustomerResponseCustomerNotFound))
          SubmitCertificateHelper.mock(MockAuthHelper.testSubscriptionId, 201, Some(submitCertificateResponse))
          EmailHelper.mock(200)
          ObjectStoreHelper.mockPdfUpload(
            pdfFilename,
            200,
            Some(objectStoreUploadResponse)
          )
          ObjectStoreHelper.mockPdfRetrieval(pdfFilename, 200, Some(objectStoreUploadResponse))
          ObjectStoreHelper.mockCertificateZipUpload(
            certificateReference,
            200,
            Some(objectStoreUploadResponse)
          )
          SdesHelper.mock(200, None)

          val response = makeRequest(requestBody)

          response.status mustBe 201
          response.body[String] mustBe controllerSuccessResponse

          GetSubscriptionHelper.verifyCalled(MockAuthHelper.testSubscriptionId, 1)
          RetrieveCustomerHelper.verifyCalled(retrieveCustomerRequest, 1)
          SubmitCertificateHelper.verifyCalled(
            MockAuthHelper.testSubscriptionId,
            Some(submitCertificateRequestWithoutCustomerId),
            1
          )

          eventually {
            EmailHelper.verifyCalled(None, 3)
            EmailHelper.verifyCalled(Some(EmailRequests.firstUserConfirmationForSao), 1)
            EmailHelper.verifyCalled(Some(EmailRequests.secondUserConfirmationForSao), 1)
            EmailHelper.verifyCalled(Some(EmailRequests.thirdUserConfirmationForSao), 1)
            ObjectStoreHelper.verifyPdfUpload(pdfFilename, 1)
            ObjectStoreHelper.verifyPdfRetrieval(pdfFilename, 1)
            ObjectStoreHelper.verifyCertificateZipUpload(certificateReference, 1)
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

          val response = makeRequest(requestBodyWithSubmitterName)

          response.status mustBe 500
          response.body[String] mustBe s"""{"reason":"DOWNSTREAM_SERVICE_MISALIGNMENT"}"""

          GetSubscriptionHelper.verifyCalled(MockAuthHelper.testSubscriptionId, 1)
          RetrieveCustomerHelper.verifyCalled(retrieveCustomerRequest, 0)
          SubmitCertificateHelper.verifyCalled(MockAuthHelper.testSubscriptionId, None, 0)

          eventually {
            EmailHelper.verifyCalled(None, 0)
            ObjectStoreHelper.verifyPdfUpload(pdfFilename, 0)
            ObjectStoreHelper.verifyPdfRetrieval(pdfFilename, 0)
            ObjectStoreHelper.verifyCertificateZipUpload(zipLocation, 0)
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

            val response = makeRequest(requestBodyWithSubmitterName)

            response.status mustBe expectedResponseCode
            response.body[String] mustBe s"""{"reason":"$expectedReason"}"""

            GetSubscriptionHelper.verifyCalled(MockAuthHelper.testSubscriptionId, 1)
            RetrieveCustomerHelper.verifyCalled(retrieveCustomerRequest, 0)
            SubmitCertificateHelper.verifyCalled(MockAuthHelper.testSubscriptionId, None, 0)

            eventually {
              EmailHelper.verifyCalled(None, 0)
              ObjectStoreHelper.verifyPdfUpload(pdfFilename, 0)
              ObjectStoreHelper.verifyPdfRetrieval(pdfFilename, 0)
              ObjectStoreHelper.verifyCertificateZipUpload(certificateReference, 0)
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

          val response = makeRequest(requestBodyWithSubmitterName)

          response.status mustBe 500
          response.body[String] mustBe s"""{"reason":"DOWNSTREAM_SERVICE_MISALIGNMENT"}"""

          GetSubscriptionHelper.verifyCalled(MockAuthHelper.testSubscriptionId, 1)
          RetrieveCustomerHelper.verifyCalled(retrieveCustomerRequest, 1)
          SubmitCertificateHelper.verifyCalled(MockAuthHelper.testSubscriptionId, None, 0)

          eventually {
            EmailHelper.verifyCalled(None, 0)
            ObjectStoreHelper.verifyPdfUpload(pdfFilename, 0)
            ObjectStoreHelper.verifyPdfRetrieval(pdfFilename, 0)
            ObjectStoreHelper.verifyCertificateZipUpload(certificateReference, 0)
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

            val response = makeRequest(requestBodyWithSubmitterName)

            response.status mustBe expectedResponseCode
            response.body[String] mustBe s"""{"reason":"$expectedReason"}"""

            GetSubscriptionHelper.verifyCalled(MockAuthHelper.testSubscriptionId, 1)
            RetrieveCustomerHelper.verifyCalled(retrieveCustomerRequest, 1)
            SubmitCertificateHelper.verifyCalled(MockAuthHelper.testSubscriptionId, None, 0)

            eventually {
              EmailHelper.verifyCalled(None, 0)
              ObjectStoreHelper.verifyPdfUpload(pdfFilename, 0)
              ObjectStoreHelper.verifyPdfRetrieval(pdfFilename, 0)
              ObjectStoreHelper.verifyCertificateZipUpload(certificateReference, 0)
              SdesHelper.verifyCalled(sdesRequest, 0)
            }
          }
        }
      })
    }

    "SubmitCertificate" when {
      "returns 201 with a response body that cannot be parsed" must {
        "return a 500 response reason DOWNSTREAM_SERVICE_MISALIGNMENT" in {
          MockAuthHelper.mockAuthOk()
          GetSubscriptionHelper.mock(MockAuthHelper.testSubscriptionId, 200, Some(getSubscriptionResponse))
          RetrieveCustomerHelper.mock(200, Some(retrieveCustomerResponseCustomerFound))
          SubmitCertificateHelper.mock(MockAuthHelper.testSubscriptionId, 201, Some(unparsableResponse))

          val response = makeRequest(requestBody)

          response.status mustBe 500
          response.body[String] mustBe s"""{"reason":"DOWNSTREAM_SERVICE_MISALIGNMENT"}"""

          GetSubscriptionHelper.verifyCalled(MockAuthHelper.testSubscriptionId, 1)
          RetrieveCustomerHelper.verifyCalled(retrieveCustomerRequest, 1)
          SubmitCertificateHelper.verifyCalled(
            MockAuthHelper.testSubscriptionId,
            Some(submitCertificateRequest),
            1
          )

          eventually {
            EmailHelper.verifyCalled(None, 0)
            ObjectStoreHelper.verifyPdfUpload(pdfFilename, 0)
            ObjectStoreHelper.verifyPdfRetrieval(pdfFilename, 0)
            ObjectStoreHelper.verifyCertificateZipUpload(certificateReference, 0)
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
            SubmitCertificateHelper.mock(MockAuthHelper.testSubscriptionId, downstreamResponseCode, None)

            val response = makeRequest(requestBody)

            response.status mustBe expectedResponseCode
            response.body[String] mustBe s"""{"reason":"$expectedReason"}"""

            GetSubscriptionHelper.verifyCalled(MockAuthHelper.testSubscriptionId, 1)
            RetrieveCustomerHelper.verifyCalled(retrieveCustomerRequest, 1)
            SubmitCertificateHelper.verifyCalled(
              MockAuthHelper.testSubscriptionId,
              Some(submitCertificateRequest),
              1
            )

            eventually {
              EmailHelper.verifyCalled(None, 0)
              ObjectStoreHelper.verifyPdfUpload(pdfFilename, 0)
              ObjectStoreHelper.verifyPdfRetrieval(pdfFilename, 0)
              ObjectStoreHelper.verifyCertificateZipUpload(certificateReference, 0)
              SdesHelper.verifyCalled(sdesRequest, 0)
            }
          }
        }
      })
    }

    "Email" when {
      "Fails" must {
        "despite the failure return 201 and continue to upload to object store" in {
          MockAuthHelper.mockAuthOk()
          GetSubscriptionHelper.mock(MockAuthHelper.testSubscriptionId, 200, Some(getSubscriptionResponse))
          RetrieveCustomerHelper.mock(200, Some(retrieveCustomerResponseCustomerFound))
          SubmitCertificateHelper.mock(MockAuthHelper.testSubscriptionId, 201, Some(submitCertificateResponse))
          EmailHelper.mock(400)
          ObjectStoreHelper.mockPdfUpload(pdfFilename, 400, None)

          val response = makeRequest(requestBody)

          response.status mustBe 201
          response.body[String] mustBe controllerSuccessResponse

          GetSubscriptionHelper.verifyCalled(MockAuthHelper.testSubscriptionId, 1)
          RetrieveCustomerHelper.verifyCalled(retrieveCustomerRequest, 1)
          SubmitCertificateHelper.verifyCalled(
            MockAuthHelper.testSubscriptionId,
            Some(submitCertificateRequest),
            1
          )

          eventually {
            EmailHelper.verifyCalled(None, 3)
            EmailHelper.verifyCalled(Some(EmailRequests.firstUserConfirmationForSao), 1)
            EmailHelper.verifyCalled(Some(EmailRequests.secondUserConfirmationForSao), 1)
            EmailHelper.verifyCalled(Some(EmailRequests.thirdUserConfirmationForSao), 1)
            ObjectStoreHelper.verifyPdfUpload(pdfFilename, 1)
            ObjectStoreHelper.verifyPdfRetrieval(pdfFilename, 0)
            ObjectStoreHelper.verifyCertificateZipUpload(certificateReference, 0)
            SdesHelper.verifyCalled(sdesRequest, 0)
          }
        }
      }
    }

    "ObjectStore" when {
      "Uploading the pdf" when {
        "Fails" must {
          "return 201 despite the failure" in {
            MockAuthHelper.mockAuthOk()
            GetSubscriptionHelper.mock(MockAuthHelper.testSubscriptionId, 200, Some(getSubscriptionResponse))
            RetrieveCustomerHelper.mock(200, Some(retrieveCustomerResponseCustomerFound))
            SubmitCertificateHelper.mock(MockAuthHelper.testSubscriptionId, 201, Some(submitCertificateResponse))
            EmailHelper.mock(200)
            ObjectStoreHelper.mockPdfUpload(pdfFilename, 400, None)

            val response = makeRequest(requestBody)

            response.status mustBe 201
            response.body[String] mustBe controllerSuccessResponse

            GetSubscriptionHelper.verifyCalled(MockAuthHelper.testSubscriptionId, 1)
            RetrieveCustomerHelper.verifyCalled(retrieveCustomerRequest, 1)
            SubmitCertificateHelper.verifyCalled(
              MockAuthHelper.testSubscriptionId,
              Some(submitCertificateRequest),
              1
            )

            eventually {
              EmailHelper.verifyCalled(None, 3)
              EmailHelper.verifyCalled(Some(EmailRequests.firstUserConfirmationForSao), 1)
              EmailHelper.verifyCalled(Some(EmailRequests.secondUserConfirmationForSao), 1)
              EmailHelper.verifyCalled(Some(EmailRequests.thirdUserConfirmationForSao), 1)
              ObjectStoreHelper.verifyPdfUpload(pdfFilename, 1)
              ObjectStoreHelper.verifyPdfRetrieval(pdfFilename, 0)
              ObjectStoreHelper.verifyCertificateZipUpload(certificateReference, 0)
              SdesHelper.verifyCalled(sdesRequest, 0)
            }
          }
        }
      }

      "Retrieving the pdf" when {
        "Fails" must {
          "return 201 despite the failure" in {
            MockAuthHelper.mockAuthOk()
            GetSubscriptionHelper.mock(MockAuthHelper.testSubscriptionId, 200, Some(getSubscriptionResponse))
            RetrieveCustomerHelper.mock(200, Some(retrieveCustomerResponseCustomerFound))
            SubmitCertificateHelper.mock(MockAuthHelper.testSubscriptionId, 201, Some(submitCertificateResponse))
            EmailHelper.mock(200)
            ObjectStoreHelper.mockPdfUpload(
              pdfFilename,
              200,
              Some(objectStoreUploadResponse)
            )
            ObjectStoreHelper.mockPdfRetrieval(pdfFilename, 400, None)

            val response = makeRequest(requestBody)

            response.status mustBe 201
            response.body[String] mustBe controllerSuccessResponse

            GetSubscriptionHelper.verifyCalled(MockAuthHelper.testSubscriptionId, 1)
            RetrieveCustomerHelper.verifyCalled(retrieveCustomerRequest, 1)
            SubmitCertificateHelper.verifyCalled(
              MockAuthHelper.testSubscriptionId,
              Some(submitCertificateRequest),
              1
            )

            eventually {
              EmailHelper.verifyCalled(None, 3)
              EmailHelper.verifyCalled(Some(EmailRequests.firstUserConfirmationForSao), 1)
              EmailHelper.verifyCalled(Some(EmailRequests.secondUserConfirmationForSao), 1)
              EmailHelper.verifyCalled(Some(EmailRequests.thirdUserConfirmationForSao), 1)
              ObjectStoreHelper.verifyPdfUpload(pdfFilename, 1)
              ObjectStoreHelper.verifyPdfRetrieval(pdfFilename, 1)
              ObjectStoreHelper.verifyCertificateZipUpload(certificateReference, 0)
              SdesHelper.verifyCalled(sdesRequest, 0)
            }
          }
        }
      }

      "Uploading the zip" when {
        "Fails" must {
          "return 201 despite the failure" in {
            MockAuthHelper.mockAuthOk()
            GetSubscriptionHelper.mock(MockAuthHelper.testSubscriptionId, 200, Some(getSubscriptionResponse))
            RetrieveCustomerHelper.mock(200, Some(retrieveCustomerResponseCustomerFound))
            SubmitCertificateHelper.mock(MockAuthHelper.testSubscriptionId, 201, Some(submitCertificateResponse))
            EmailHelper.mock(200)
            ObjectStoreHelper.mockPdfUpload(
              pdfFilename,
              200,
              Some(objectStoreUploadResponse)
            )
            ObjectStoreHelper.mockPdfRetrieval(pdfFilename, 200, Some(objectStoreUploadResponse))
            ObjectStoreHelper.mockCertificateZipUpload(
              certificateReference,
              400,
              Some(objectStoreUploadResponse)
            )

            val response = makeRequest(requestBody)

            response.status mustBe 201
            response.body[String] mustBe controllerSuccessResponse

            GetSubscriptionHelper.verifyCalled(MockAuthHelper.testSubscriptionId, 1)
            RetrieveCustomerHelper.verifyCalled(retrieveCustomerRequest, 1)
            SubmitCertificateHelper.verifyCalled(
              MockAuthHelper.testSubscriptionId,
              Some(submitCertificateRequest),
              1
            )

            eventually {
              EmailHelper.verifyCalled(None, 3)
              EmailHelper.verifyCalled(Some(EmailRequests.firstUserConfirmationForSao), 1)
              EmailHelper.verifyCalled(Some(EmailRequests.secondUserConfirmationForSao), 1)
              EmailHelper.verifyCalled(Some(EmailRequests.thirdUserConfirmationForSao), 1)
              ObjectStoreHelper.verifyPdfUpload(pdfFilename, 1)
              ObjectStoreHelper.verifyPdfRetrieval(pdfFilename, 1)
              ObjectStoreHelper.verifyCertificateZipUpload(certificateReference, 1)
              SdesHelper.verifyCalled(sdesRequest, 0)
            }
          }
        }
      }
    }

    "SDES" when {
      "Fails" must {
        "return 201 despite the failure" in {
          MockAuthHelper.mockAuthOk()
          GetSubscriptionHelper.mock(MockAuthHelper.testSubscriptionId, 200, Some(getSubscriptionResponse))
          RetrieveCustomerHelper.mock(200, Some(retrieveCustomerResponseCustomerFound))
          SubmitCertificateHelper.mock(MockAuthHelper.testSubscriptionId, 201, Some(submitCertificateResponse))
          EmailHelper.mock(200)
          ObjectStoreHelper.mockPdfUpload(
            pdfFilename,
            200,
            Some(objectStoreUploadResponse)
          )
          ObjectStoreHelper.mockPdfRetrieval(pdfFilename, 200, Some(objectStoreUploadResponse))
          ObjectStoreHelper.mockCertificateZipUpload(
            certificateReference,
            200,
            Some(objectStoreUploadResponse)
          )
          SdesHelper.mock(400, None)

          val response = makeRequest(requestBody)

          response.status mustBe 201
          response.body[String] mustBe controllerSuccessResponse

          GetSubscriptionHelper.verifyCalled(MockAuthHelper.testSubscriptionId, 1)
          RetrieveCustomerHelper.verifyCalled(retrieveCustomerRequest, 1)
          SubmitCertificateHelper.verifyCalled(
            MockAuthHelper.testSubscriptionId,
            Some(submitCertificateRequest),
            1
          )

          eventually {
            EmailHelper.verifyCalled(None, 3)
            EmailHelper.verifyCalled(Some(EmailRequests.firstUserConfirmationForSao), 1)
            EmailHelper.verifyCalled(Some(EmailRequests.secondUserConfirmationForSao), 1)
            EmailHelper.verifyCalled(Some(EmailRequests.thirdUserConfirmationForSao), 1)
            ObjectStoreHelper.verifyPdfUpload(pdfFilename, 1)
            ObjectStoreHelper.verifyPdfRetrieval(pdfFilename, 1)
            ObjectStoreHelper.verifyCertificateZipUpload(certificateReference, 1)
            SdesHelper.verifyCalled(sdesRequest, 1)
          }
        }
      }
    }
  }
}

object CertificateControllerISpec {
  def rawStringWriter(contentType: String): BodyWritable[String] =
    BodyWritable(
      str => InMemoryBody(ByteString(str)),
      contentType
    )

  val unparsableResponse = "{"

  val crn                            = generateCrn
  val utr                            = generateUtr
  val firstUserName                  = "Firstname Lastname"
  val firstUserEmail                 = "Firstname.Lastname@example.com"
  val secondUserName                 = "Firstname Lastname II"
  val secondUserEmail                = "Firstname.Lastname.II@example.com"
  val thirdUserName                  = "Firstname Lastname III"
  val thirdUserEmail                 = "Firstname.Lastname.III@example.com"
  val fourthUserName                 = "Firstname Lastname IV"
  val companyName                    = "Example Subsidiary Ltd"
  val staffPid                       = "staffpid_123"
  val accountingPeriodEnd            = "2025-03-31"
  val remarks                        = "This is remarkable."
  val status                         = "Dormant"
  val companyType                    = "LTD"
  val isCorporationTaxQualified      = false
  val isVatQualified                 = true
  val isPayeQualified                = true
  val isInsurancePremiumTaxQualified = true
  val isStampDutyLandTaxQualified    = true
  val isStampDutyReserveTaxQualified = true
  val isPetroleumRevenueTaxQualified = true
  val isCustomsDutiesQualified       = true
  val isExciseDutiesQualified        = true
  val isBankLevyQualified            = true
  val qualificationStatement         = "test statement"

  val requestBody = s"""{
                       |  "saoName": "$thirdUserName",
                       |  "saoEmail": "$thirdUserEmail",
                       |  "companies": [
                       |    {
                       |      "crn": "$crn",
                       |      "utr": "$utr",
                       |      "name": "$companyName",
                       |      "accPeriodEnd": "$accountingPeriodEnd",
                       |      "status": "$status",
                       |      "type": "LTD",
                       |      "isCorporationTaxQualified": $isCorporationTaxQualified,
                       |      "isVatQualified": $isVatQualified,
                       |      "isPayeQualified": $isPayeQualified,
                       |      "isInsurancePremiumTaxQualified": $isInsurancePremiumTaxQualified,
                       |      "isStampDutyLandTaxQualified": $isStampDutyLandTaxQualified,
                       |      "isStampDutyReserveTaxQualified": $isStampDutyReserveTaxQualified,
                       |      "isPetroleumRevenueTaxQualified": $isPetroleumRevenueTaxQualified,
                       |      "isCustomsDutiesQualified": $isCustomsDutiesQualified,
                       |      "isExciseDutiesQualified": $isExciseDutiesQualified,
                       |      "isBankLevyQualified": $isBankLevyQualified,
                       |      "qualificationStatement": "$qualificationStatement"
                       |    }
                       |  ],
                       |  "remarks": "$remarks",
                       |  "staffPid": "$staffPid"
                       |}
                       |""".stripMargin

  val requestBodyWithSubmitterName = s"""{
                                        |  "submitterName": "$fourthUserName",
                                        |  "saoName": "$thirdUserName",
                                        |  "saoEmail": "$thirdUserEmail",
                                        |  "companies": [
                                        |    {
                                        |      "crn": "$crn",
                                        |      "utr": "$utr",
                                        |      "name": "$companyName",
                                        |      "accPeriodEnd": "$accountingPeriodEnd",
                                        |      "status": "$status",
                                        |      "type": "LTD",
                                        |      "isCorporationTaxQualified": $isCorporationTaxQualified,
                                        |      "isVatQualified": $isVatQualified,
                                        |      "isPayeQualified": $isPayeQualified,
                                        |      "isInsurancePremiumTaxQualified": $isInsurancePremiumTaxQualified,
                                        |      "isStampDutyLandTaxQualified": $isStampDutyLandTaxQualified,
                                        |      "isStampDutyReserveTaxQualified": $isStampDutyReserveTaxQualified,
                                        |      "isPetroleumRevenueTaxQualified": $isPetroleumRevenueTaxQualified,
                                        |      "isCustomsDutiesQualified": $isCustomsDutiesQualified,
                                        |      "isExciseDutiesQualified": $isExciseDutiesQualified,
                                        |      "isBankLevyQualified": $isBankLevyQualified,
                                        |      "qualificationStatement": "$qualificationStatement"
                                        |    }
                                        |  ],
                                        |  "remarks": "$remarks",
                                        |  "staffPid": "$staffPid"
                                        |}
                                        |""".stripMargin

  val getSubscriptionResponse = s"""{
                                   |  "etmpSafeId": "1234567890",
                                   |  "contacts": [
                                   |    {
                                   |      "name": "$firstUserName",
                                   |      "email": "$firstUserEmail",
                                   |      "language": "en",
                                   |      "status": "valid"
                                   |    },
                                   |    {
                                   |      "name": "$secondUserName",
                                   |      "email": "$secondUserEmail",
                                   |      "language": "cy",
                                   |      "status": "valid"
                                   |    }
                                   |  ],
                                   |  "nominatedCompany": {
                                   |    "crn": "$crn",
                                   |    "name": "$companyName",
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

  val certificateReference = "CERT0008470194"

  val correlationId = "d80fd83a-2c50-4967-b596-675f7f11e241"

  val submitCertificateRequestWithoutCustomerId = s"""{
                                                     |  "saoName": "$thirdUserName",
                                                     |  "saoEmail": "$thirdUserEmail",
                                                     |  "staffPid": "$staffPid",
                                                     |  "remarks": "$remarks",
                                                     |  "companies": [
                                                     |    {
                                                     |      "crn": "$crn",
                                                     |      "utr": "$utr",
                                                     |      "name": "$companyName",
                                                     |      "accPeriodEnd": "$accountingPeriodEnd",
                                                     |      "status": "$status",
                                                     |      "type": "LTD",
                                                     |      "isCorporationTaxQualified": $isCorporationTaxQualified,
                                                     |      "isVatQualified": $isVatQualified,
                                                     |      "isPayeQualified": $isPayeQualified,
                                                     |      "isInsurancePremiumTaxQualified": $isInsurancePremiumTaxQualified,
                                                     |      "isStampDutyLandTaxQualified": $isStampDutyLandTaxQualified,
                                                     |      "isStampDutyReserveTaxQualified": $isStampDutyReserveTaxQualified,
                                                     |      "isPetroleumRevenueTaxQualified": $isPetroleumRevenueTaxQualified,
                                                     |      "isCustomsDutiesQualified": $isCustomsDutiesQualified,
                                                     |      "isExciseDutiesQualified": $isExciseDutiesQualified,
                                                     |      "isBankLevyQualified": $isBankLevyQualified,
                                                     |      "qualificationStatement": "$qualificationStatement"
                                                     |    }
                                                     |  ]
                                                     |}""".stripMargin

  val submitCertificateRequest = s"""{
                                    |  "saoName": "$thirdUserName",
                                    |  "saoEmail": "$thirdUserEmail",
                                    |  "staffPid": "$staffPid",
                                    |  "remarks": "$remarks",
                                    |  "customerId": "$customerId",
                                    |  "companies": [
                                    |    {
                                    |      "crn": "$crn",
                                    |      "utr": "$utr",
                                    |      "name": "$companyName",
                                    |      "accPeriodEnd": "$accountingPeriodEnd",
                                    |      "status": "$status",
                                    |      "type": "LTD",
                                    |      "isCorporationTaxQualified": $isCorporationTaxQualified,
                                    |      "isVatQualified": $isVatQualified,
                                    |      "isPayeQualified": $isPayeQualified,
                                    |      "isInsurancePremiumTaxQualified": $isInsurancePremiumTaxQualified,
                                    |      "isStampDutyLandTaxQualified": $isStampDutyLandTaxQualified,
                                    |      "isStampDutyReserveTaxQualified": $isStampDutyReserveTaxQualified,
                                    |      "isPetroleumRevenueTaxQualified": $isPetroleumRevenueTaxQualified,
                                    |      "isCustomsDutiesQualified": $isCustomsDutiesQualified,
                                    |      "isExciseDutiesQualified": $isExciseDutiesQualified,
                                    |      "isBankLevyQualified": $isBankLevyQualified,
                                    |      "qualificationStatement": "$qualificationStatement"
                                    |    }
                                    |  ]
                                    |}""".stripMargin

  val submitCertificateRequestWithSubmitterName = s"""{
                                                     |  "submitterName": "$fourthUserName",
                                                     |  "saoName": "$thirdUserName",
                                                     |  "saoEmail": "$thirdUserEmail",
                                                     |  "staffPid": "$staffPid",
                                                     |  "remarks": "$remarks",
                                                     |  "companies": [
                                                     |    {
                                                     |      "crn": "$crn",
                                                     |      "utr": "$utr",
                                                     |      "name": "$companyName",
                                                     |      "accPeriodEnd": "$accountingPeriodEnd",
                                                     |      "status": "$status",
                                                     |      "type": "LTD",
                                                     |      "isCorporationTaxQualified": $isCorporationTaxQualified,
                                                     |      "isVatQualified": $isVatQualified,
                                                     |      "isPayeQualified": $isPayeQualified,
                                                     |      "isInsurancePremiumTaxQualified": $isInsurancePremiumTaxQualified,
                                                     |      "isStampDutyLandTaxQualified": $isStampDutyLandTaxQualified,
                                                     |      "isStampDutyReserveTaxQualified": $isStampDutyReserveTaxQualified,
                                                     |      "isPetroleumRevenueTaxQualified": $isPetroleumRevenueTaxQualified,
                                                     |      "isCustomsDutiesQualified": $isCustomsDutiesQualified,
                                                     |      "isExciseDutiesQualified": $isExciseDutiesQualified,
                                                     |      "isBankLevyQualified": $isBankLevyQualified,
                                                     |      "qualificationStatement": "$qualificationStatement"
                                                     |    }
                                                     |  ]
                                                     |}""".stripMargin

  val submitCertificateResponse = s"""{
                                     |  "certificateRef": "$certificateReference"
                                     |}""".stripMargin

  object EmailRequests {
    val firstUserConfirmationForSao = s"""{
                                         |  "to": [
                                         |    "$firstUserEmail"
                                         |  ],
                                         |  "templateId": "dsao_certificate_confirmation_for_sao",
                                         |  "parameters": {
                                         |    "recipientName": "$firstUserName",
                                         |    "companyName": "$companyName",
                                         |    "saoName": "$thirdUserName",
                                         |    "referenceId": "$certificateReference"
                                         |  }
                                         |}""".stripMargin

    val secondUserConfirmationForSao = s"""{
                                          |  "to": [
                                          |    "$secondUserEmail"
                                          |  ],
                                          |  "templateId": "dsao_certificate_confirmation_for_sao",
                                          |  "parameters": {
                                          |    "recipientName": "$secondUserName",
                                          |    "companyName": "$companyName",
                                          |    "saoName": "$thirdUserName",
                                          |    "referenceId": "$certificateReference"
                                          |  }
                                          |}""".stripMargin

    val thirdUserConfirmationForSao = s"""{
                                         |  "to": [
                                         |    "$thirdUserEmail"
                                         |  ],
                                         |  "templateId": "dsao_certificate_confirmation_for_sao",
                                         |  "parameters": {
                                         |    "recipientName": "$thirdUserName",
                                         |    "companyName": "$companyName",
                                         |    "saoName": "$thirdUserName",
                                         |    "referenceId": "$certificateReference"
                                         |  }
                                         |}""".stripMargin

    val firstUserConfirmationForSubmitter = s"""{
                                               |  "to": [
                                               |    "$firstUserEmail"
                                               |  ],
                                               |  "templateId": "dsao_certificate_confirmation_for_submitter",
                                               |  "parameters": {
                                               |    "recipientName": "$firstUserName",
                                               |    "companyName": "$companyName",
                                               |    "submitterName": "$fourthUserName",
                                               |    "saoName": "$thirdUserName",
                                               |    "referenceId": "$certificateReference"
                                               |  }
                                               |}""".stripMargin

    val secondUserConfirmationForSubmitter = s"""{
                                                |  "to": [
                                                |    "$secondUserEmail"
                                                |  ],
                                                |  "templateId": "dsao_certificate_confirmation_for_submitter",
                                                |  "parameters": {
                                                |    "recipientName": "$secondUserName",
                                                |    "companyName": "$companyName",
                                                |    "submitterName": "$fourthUserName",
                                                |    "saoName": "$thirdUserName",
                                                |    "referenceId": "$certificateReference"
                                                |  }
                                                |}""".stripMargin

    val thirdUserConfirmationForSubmitter = s"""{
                                               |  "to": [
                                               |    "$thirdUserEmail"
                                               |  ],
                                               |  "templateId": "dsao_certificate_confirmation_for_submitter",
                                               |  "parameters": {
                                               |    "recipientName": "$thirdUserName",
                                               |    "companyName": "$companyName",
                                               |    "submitterName": "$fourthUserName",
                                               |    "saoName": "$thirdUserName",
                                               |    "referenceId": "$certificateReference"
                                               |  }
                                               |}""".stripMargin
  }

  val objectStoreUploadResponse = """{
                                    |  "contentLength": 0,
                                    |  "contentMD5": "abc",
                                    |  "lastModified": "2026-08-17T10:32:01Z",
                                    |  "location": "/some/path"
                                    |}""".stripMargin

  val pdfFilename = s"${certificateReference}_SAO_Certificate.pdf"

  val zipFilename =
    s"${LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE)}_${certificateReference}_SAO_Certificate_OFFICIAL_SENSITIVE.zip"

  val zipLocation = s"object-store/object/senior-accounting-officer/sdes/$certificateReference/$zipFilename"

  val controllerSuccessResponse = s"""{"certificateRef":"$certificateReference"}"""

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
