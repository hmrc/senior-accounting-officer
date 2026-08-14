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

package uk.gov.hmrc.senioraccountingofficer.services

import org.apache.pekko.NotUsed
import org.apache.pekko.stream.scaladsl.Source
import org.apache.pekko.util.ByteString
import org.mockito.ArgumentMatchers.{any, eq as meq}
import org.mockito.Mockito.*
import org.mockito.internal.verification.Times
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatest.time.{Millis, Seconds, Span}
import org.scalatestplus.mockito.MockitoSugar
import play.api.libs.json.Json
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}
import uk.gov.hmrc.senioraccountingofficer.connectors.{CertificateConnector, CrmmConnector, GetSubscriptionConnector}
import uk.gov.hmrc.senioraccountingofficer.models.EmailTemplate
import uk.gov.hmrc.senioraccountingofficer.models.crmm.{RetrieveCustomerRequest, RetrieveCustomerResponse}
import uk.gov.hmrc.senioraccountingofficer.models.documentum.{DocumentumPackageContext, DocumentumPackageResult}
import uk.gov.hmrc.senioraccountingofficer.models.dps.*
import uk.gov.hmrc.senioraccountingofficer.models.requests.*
import uk.gov.hmrc.senioraccountingofficer.services.CertificateService.DownstreamService.*
import uk.gov.hmrc.senioraccountingofficer.services.CertificateServiceSpec.*
import uk.gov.hmrc.senioraccountingofficer.services.documentum.DocumentumPackageService
import uk.gov.hmrc.senioraccountingofficer.utils.TestDataGenerator.*

import scala.concurrent.{ExecutionContext, Future}

import java.util.UUID

import CertificateService.PostCertificateResponse.*

class CertificateServiceSpec
    extends AnyFreeSpec
    with Matchers
    with MockitoSugar
    with ScalaFutures
    with BeforeAndAfterEach {

  override given patienceConfig: PatienceConfig =
    PatienceConfig(timeout = Span(5, Seconds), interval = Span(25, Millis))

  given ExecutionContext = ExecutionContext.global
  given HeaderCarrier    = HeaderCarrier()

  val mockCertificateDpsConnector: CertificateConnector      = mock[CertificateConnector]
  val mockGetSubscriptionConnector: GetSubscriptionConnector = mock[GetSubscriptionConnector]
  val mockCrmmConnector: CrmmConnector                       = mock[CrmmConnector]
  val mockDocumentumPackageService: DocumentumPackageService = mock[DocumentumPackageService]
  val mockPdfService: PdfService                             = mock[PdfService]
  val mockEmailService: EmailService                         = mock[EmailService]

  val service =
    new CertificateService(
      mockGetSubscriptionConnector,
      mockCrmmConnector,
      mockCertificateDpsConnector,
      mockDocumentumPackageService,
      mockPdfService,
      mockEmailService
    )

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockCertificateDpsConnector)
    reset(mockGetSubscriptionConnector)
    reset(mockCrmmConnector)
    reset(mockDocumentumPackageService)
    reset(mockPdfService)
    reset(mockEmailService)
    when(mockEmailService.sendCertificateEmail(any(), any(), any(), any(), any(), any())(using any()))
      .thenReturn(Future.successful(()))
  }

  def configureSubscriptionResponse(
      httpStatusCode: Int = 200,
      responseBody: String = Json.stringify(
        Json.toJson(
          GetSubscriptionDpsResponse(
            etmpSafeId = exampleSafeId,
            nominatedCompany = NominatedCompany(crn = Some(exampleCrn), name = exampleCompanyName, utr = exampleUtr),
            contacts = Nil
          )
        )
      )
  ): Unit = {
    when(mockGetSubscriptionConnector.getSubscription(meq(exampleSubscriptionId))(using any()))
      .thenReturn(
        Future.successful(
          HttpResponse(
            httpStatusCode,
            responseBody
          )
        )
      )
  }

  def configureCrmmResponse(
      httpStatusCode: Int = 200,
      responseBody: String = Json.stringify(
        Json.toJson(
          RetrieveCustomerResponse(
            customerId = Some(exampleCustomerId),
            errorDescription = None,
            existingCustomer = true,
            status = "Success"
          )
        )
      )
  ): Unit = {
    when(
      mockCrmmConnector.retrieveCustomer(
        meq(
          RetrieveCustomerRequest(
            companyRegistrationNumber = Some(exampleCrn),
            uniqueTaxReference = Some(exampleUtr)
          )
        )
      )(using any())
    )
      .thenReturn(Future.successful(HttpResponse(httpStatusCode, responseBody)))
  }

  def configureDpsResponse(
      httpStatusCode: Int = 201,
      responseBody: String = Json.stringify(
        Json.toJson(
          CertificateDpsResponse(
            certificateRef = exampleCertificateReference
          )
        )
      )
  ): Unit = {
    when(
      mockCertificateDpsConnector.postCertificate(
        meq(exampleSubscriptionId),
        any()
      )(using any())
    )
      .thenReturn(Future.successful(HttpResponse(httpStatusCode, responseBody)))
  }

  def configureDocumentumPackageService(): Unit = {
    when(mockDocumentumPackageService.packageAndSubmit(any(), any())(using any()))
      .thenReturn(Future.successful(DocumentumPackageResult(packageAvailable = true, Some(exampleZipFilename))))
  }

  def configurePdfGeneration(): Unit = {
    when(mockPdfService.generateCertificatePdf(any(), any())).thenReturn(objectStoreFileContent)
  }

  "postCertificate" - {
    "DPS get subscription endpoint response is" - {
      "200 OK" - {
        "Parseable response; Continue down happy path" in {
          configureSubscriptionResponse()
          configureCrmmResponse()
          configureDpsResponse()
          configurePdfGeneration()
          configureDocumentumPackageService()

          service.postCertificate(exampleSubscriptionId, incomingRequest).futureValue

          verify(
            mockCrmmConnector,
            Times(1)
          ).retrieveCustomer(meq(RetrieveCustomerRequest(Some(exampleCrn), Some(exampleUtr))))(using
            any()
          )
        }

        "Unparsable response; Return malformed response error" in {
          configureSubscriptionResponse(200, "{")

          val result = service.postCertificate(exampleSubscriptionId, incomingRequest).futureValue

          result mustBe MalformedResponse(Subscription)
        }
      }

      "204 No Content; Return subscription not found error" in {
        configureSubscriptionResponse(204)

        val result = service.postCertificate(exampleSubscriptionId, incomingRequest).futureValue

        result mustBe NotFoundFailure(Subscription)
      }

      "400 Bad Request; Return misalignment error" in {
        configureSubscriptionResponse(400)

        val result = service.postCertificate(exampleSubscriptionId, incomingRequest).futureValue

        result mustBe Misalignment(Subscription)
      }

      "401 Unauthorized; Return service misconfiguration error" in {
        configureSubscriptionResponse(401)

        val result = service.postCertificate(exampleSubscriptionId, incomingRequest).futureValue

        result mustBe Misconfiguration(Subscription, 401)
      }

      "403 Forbidden; Return service misconfiguration error" in {
        configureSubscriptionResponse(403)

        val result = service.postCertificate(exampleSubscriptionId, incomingRequest).futureValue

        result mustBe Misconfiguration(Subscription, 403)
      }

      "500 Internal Server Error; Return \"downstream service error\" error" in {
        configureSubscriptionResponse(500)

        val result = service.postCertificate(exampleSubscriptionId, incomingRequest).futureValue

        result mustBe DownstreamServiceError(Subscription)
      }

      "503 Service Unavailable; Return downstream service unavailable error" in {
        configureSubscriptionResponse(503)

        val result = service.postCertificate(exampleSubscriptionId, incomingRequest).futureValue

        result mustBe DownstreamServiceUnavailable(Subscription)
      }

      "an unknown response code; Return unknown failure error" in {
        configureSubscriptionResponse(618)

        val result = service.postCertificate(exampleSubscriptionId, incomingRequest).futureValue

        result mustBe UnknownFailure(Subscription, 618)
      }
    }

    "CRMM retrieve customer endpoint response is" - {
      "200 OK" - {
        "Parsable response" - {
          "Success response indicating customer found; Continue down happy path with a customer id" in {
            val expectedCustomerId = UUID.randomUUID().toString
            configureSubscriptionResponse()
            configureCrmmResponse(responseBody =
              Json.stringify(
                Json.toJson(
                  RetrieveCustomerResponse(
                    customerId = Some(expectedCustomerId),
                    errorDescription = None,
                    existingCustomer = true,
                    status = "Success"
                  )
                )
              )
            )
            configureDpsResponse()
            configurePdfGeneration()
            configureDocumentumPackageService()

            service.postCertificate(exampleSubscriptionId, incomingRequest).futureValue

            verify(
              mockCertificateDpsConnector,
              Times(1)
            ).postCertificate(
              meq(exampleSubscriptionId),
              meq(
                CertificateDpsRequest(
                  submitterName = Some("Firstname Lastname"),
                  saoName = expectedSaoName,
                  saoEmail = expectedSaoEmail,
                  companies = Nil,
                  customerId = Some(expectedCustomerId)
                )
              )
            )(using
              any()
            )
          }

          "Failure response indicating customer not found; Continue down happy path with no customer id" in {
            configureSubscriptionResponse()
            configureCrmmResponse(responseBody =
              Json.stringify(
                Json.toJson(
                  RetrieveCustomerResponse(
                    customerId = None,
                    errorDescription = Some("customer not found"),
                    existingCustomer = false,
                    status = "Failure"
                  )
                )
              )
            )
            configureDpsResponse()
            configurePdfGeneration()
            configureDocumentumPackageService()

            service.postCertificate(exampleSubscriptionId, incomingRequest).futureValue

            verify(
              mockCertificateDpsConnector,
              Times(1)
            ).postCertificate(
              meq(exampleSubscriptionId),
              meq(
                CertificateDpsRequest(
                  submitterName = Some("Firstname Lastname"),
                  saoName = expectedSaoName,
                  saoEmail = expectedSaoEmail,
                  companies = Nil
                )
              )
            )(using
              any()
            )
          }

          "Invalid response; Return malformed response error" in {
            configureSubscriptionResponse(200)
            configureCrmmResponse(
              200,
              Json.stringify(
                Json.toJson(
                  RetrieveCustomerResponse(
                    customerId = Some(exampleCustomerId),
                    errorDescription = Some("an error message?!?"),
                    existingCustomer = true,
                    status = "a real status"
                  )
                )
              )
            )

            val result = service.postCertificate(exampleSubscriptionId, incomingRequest).futureValue

            result mustBe MalformedResponse(CRMM)
          }
        }

        "Unparsable response; Return malformed response error" in {
          configureSubscriptionResponse(200)
          configureCrmmResponse(
            200,
            "{"
          )

          val result = service.postCertificate(exampleSubscriptionId, incomingRequest).futureValue

          result mustBe MalformedResponse(CRMM)
        }
      }

      "400 Bad Request; Return misalignment error" in {
        configureSubscriptionResponse(200)
        configureCrmmResponse(400)

        val result = service.postCertificate(exampleSubscriptionId, incomingRequest).futureValue

        result mustBe Misalignment(CRMM)
      }

      "401 Unauthorized; Return service misconfiguration error" in {
        configureSubscriptionResponse(200)
        configureCrmmResponse(401)

        val result = service.postCertificate(exampleSubscriptionId, incomingRequest).futureValue

        result mustBe Misconfiguration(CRMM, 401)
      }

      "403 Forbidden; Return service misconfiguration error" in {
        configureSubscriptionResponse(200)
        configureCrmmResponse(403)

        val result = service.postCertificate(exampleSubscriptionId, incomingRequest).futureValue

        result mustBe Misconfiguration(CRMM, 403)
      }

      "404 Not Found; Return misalignment error" in {
        configureSubscriptionResponse(200)
        configureCrmmResponse(404)

        val result = service.postCertificate(exampleSubscriptionId, incomingRequest).futureValue

        result mustBe Misalignment(CRMM)
      }

      "500 Internal Server Error; Return \"downstream service error\" error" in {
        configureSubscriptionResponse(200)
        configureCrmmResponse(500)

        val result = service.postCertificate(exampleSubscriptionId, incomingRequest).futureValue

        result mustBe DownstreamServiceError(CRMM)
      }

      "503 Service Unavailable; Return downstream service unavailable error" in {
        configureSubscriptionResponse(200)
        configureCrmmResponse(503)

        val result = service.postCertificate(exampleSubscriptionId, incomingRequest).futureValue

        result mustBe DownstreamServiceUnavailable(CRMM)
      }

      "an unknown response code; Return unknown failure error" in {
        configureSubscriptionResponse(200)
        configureCrmmResponse(618)

        val result = service.postCertificate(exampleSubscriptionId, incomingRequest).futureValue

        result mustBe UnknownFailure(CRMM, 618)
      }
    }

    "DPS post certificate customer endpoint response is" - {
      "201 Created" - {
        "Valid response; Continue down happy path" in {
          configureSubscriptionResponse()
          configureCrmmResponse()
          configureDpsResponse(201, validDpsResponseBody)
          configurePdfGeneration()
          configureDocumentumPackageService()

          val result = service.postCertificate(exampleSubscriptionId, incomingRequest).futureValue

          result mustBe Success(exampleCertificateReference)

          val expectedDpsRequest = baseDpsRequest.copy(customerId = Some(exampleCustomerId))

          verify(mockCertificateDpsConnector)
            .postCertificate(meq(exampleSubscriptionId), meq(expectedDpsRequest))(using any())

          verify(mockDocumentumPackageService)
            .packageAndSubmit(
              meq(
                DocumentumPackageContext
                  .certificate(exampleCertificateReference, exampleSubscriptionId, expectedDpsRequest)
              ),
              meq(objectStoreFileContent)
            )(using any())
        }

        "Valid response with submitter; Send submitter confirmation emails to subscription contacts" in {
          configureSubscriptionResponse(responseBody =
            Json.stringify(
              Json.toJson(
                GetSubscriptionDpsResponse(
                  etmpSafeId = exampleSafeId,
                  nominatedCompany =
                    NominatedCompany(crn = Some(exampleCrn), name = exampleCompanyName, utr = exampleUtr),
                  contacts = exampleContacts
                )
              )
            )
          )
          configureCrmmResponse()
          configureDpsResponse(201, validDpsResponseBody)
          configurePdfGeneration()
          configureDocumentumPackageService()

          service.postCertificate(exampleSubscriptionId, incomingRequest).futureValue

          exampleContacts.foreach { contact =>
            verify(mockEmailService, Times(1)).sendCertificateEmail(
              EmailTemplate.CertificateConfirmationSubmitter,
              contact.email,
              exampleCompanyName,
              exampleCertificateReference,
              "Firstname Lastname",
              Some(expectedSaoName)
            )
          }
        }

        "Valid response without submitter; Send SAO confirmation email to SAO" in {
          configureSubscriptionResponse()
          configureCrmmResponse()
          configureDpsResponse(201, validDpsResponseBody)
          configurePdfGeneration()
          configureDocumentumPackageService()

          service.postCertificate(exampleSubscriptionId, incomingRequest.copy(submitterName = None)).futureValue

          verify(mockEmailService, Times(1)).sendCertificateEmail(
            EmailTemplate.CertificateConfirmationSAO,
            expectedSaoEmail,
            exampleCompanyName,
            exampleCertificateReference,
            expectedSaoName,
            None
          )
        }

        "Invalid response; Return malformed response error" in {
          configureSubscriptionResponse()
          configureCrmmResponse()
          configureDpsResponse(201, "{")

          val result = service.postCertificate(exampleSubscriptionId, incomingRequest).futureValue

          verify(mockEmailService, Times(0)).sendCertificateEmail(any(), any(), any(), any(), any(), any())(using
            any()
          )
          result mustBe MalformedResponse(DPS)
        }
      }

      "400 Bad Request; Return misalignment error" in {
        configureSubscriptionResponse()
        configureCrmmResponse()
        configureDpsResponse(400)

        val result = service.postCertificate(exampleSubscriptionId, incomingRequest).futureValue

        result mustBe Misalignment(DPS)
      }

      "401 Unauthorized; Return service misconfiguration error" in {
        configureSubscriptionResponse()
        configureCrmmResponse()
        configureDpsResponse(401)

        val result = service.postCertificate(exampleSubscriptionId, incomingRequest).futureValue

        result mustBe Misconfiguration(DPS, 401)
      }

      "403 Forbidden; Return service misconfiguration error" in {
        configureSubscriptionResponse()
        configureCrmmResponse()
        configureDpsResponse(403)

        val result = service.postCertificate(exampleSubscriptionId, incomingRequest).futureValue

        result mustBe Misconfiguration(DPS, 403)
      }

      "404 Not Found; Return misalignment error" in {
        configureSubscriptionResponse()
        configureCrmmResponse()
        configureDpsResponse(404)

        val result = service.postCertificate(exampleSubscriptionId, incomingRequest).futureValue

        result mustBe Misalignment(DPS)
      }

      "500 Internal Server Error; Return \"downstream service error\" error" in {
        configureSubscriptionResponse()
        configureCrmmResponse()
        configureDpsResponse(500)

        val result = service.postCertificate(exampleSubscriptionId, incomingRequest).futureValue

        result mustBe DownstreamServiceError(DPS)
      }

      "503 Service Unavailable; Return downstream service unavailable error" in {
        configureSubscriptionResponse()
        configureCrmmResponse()
        configureDpsResponse(503)

        val result = service.postCertificate(exampleSubscriptionId, incomingRequest).futureValue

        result mustBe DownstreamServiceUnavailable(DPS)
      }

      "an unknown response code; Return unknown failure error" in {
        configureSubscriptionResponse()
        configureCrmmResponse()
        configureDpsResponse(618)

        val result = service.postCertificate(exampleSubscriptionId, incomingRequest).futureValue

        result mustBe UnknownFailure(DPS, 618)
      }
    }
  }
}

object CertificateServiceSpec {
  val requestId                           = "123"
  val incomingRequest: CertificateRequest =
    CertificateRequest(
      submitterName = Some(PersonName("Firstname Lastname")),
      saoName = PersonName("Firstname Lastname"),
      saoEmail = Email("firstname.lastname@example.com"),
      companies = CertificateCompanies(List.empty),
      remarks = None,
      staffPid = None
    )
  val baseDpsRequest: CertificateDpsRequest =
    CertificateDpsRequest(
      submitterName = Some("Firstname Lastname"),
      saoName = "Firstname Lastname",
      saoEmail = "firstname.lastname@example.com",
      companies = List.empty
    )

  val exampleSubscriptionId          = "123"
  val exampleCertificateReference    = "CRT0123456789"
  val exampleUtr                     = generateUtr
  val exampleCrn                     = generateCrn
  val exampleCompanyName             = "company name"
  val exampleSafeId                  = "safe id"
  val exampleCustomerId              = "customer id"
  val exampleContacts: List[Contact] = List(
    Contact("contact 1", "contact1@example.com", "en", "active"),
    Contact("contact 2", "contact2@example.com", "en", "active"),
    Contact("contact 3", "contact3@example.com", "en", "active")
  )
  val expectedSaoName  = "Firstname Lastname"
  val expectedSaoEmail = "firstname.lastname@example.com"

  val validDpsResponseBody: String = s"""{"certificateRef":"$exampleCertificateReference"}"""

  val exampleZipFilename: String = s"20260728_${exampleCertificateReference}_SAO_Certificate_OFFICIAL_SENSITIVE.ZIP"
  val examplePdfFilename: String = s"${exampleCertificateReference}_SAO_Certificate.pdf"

  val objectStoreFileContent: Source[ByteString, NotUsed] = Source.single(ByteString("dummy file content"))

}
