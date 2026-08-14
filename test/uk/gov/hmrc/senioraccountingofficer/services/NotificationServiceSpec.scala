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
import uk.gov.hmrc.senioraccountingofficer.connectors.*
import uk.gov.hmrc.senioraccountingofficer.models.crmm.{RetrieveCustomerRequest, RetrieveCustomerResponse}
import uk.gov.hmrc.senioraccountingofficer.models.documentum.{DocumentumPackageContext, DocumentumPackageResult}
import uk.gov.hmrc.senioraccountingofficer.models.dps.*
import uk.gov.hmrc.senioraccountingofficer.models.requests.*
import uk.gov.hmrc.senioraccountingofficer.services.NotificationService.DownstreamService
import uk.gov.hmrc.senioraccountingofficer.services.NotificationService.DownstreamService.*
import uk.gov.hmrc.senioraccountingofficer.services.documentum.DocumentumPackageService
import uk.gov.hmrc.senioraccountingofficer.utils.TestDataGenerator.{generateCrn, generateUtr}

import scala.concurrent.{ExecutionContext, Future}

import java.util.UUID

import NotificationService.PostNotificationResponse.*
import NotificationServiceSpec.*

class NotificationServiceSpec
    extends AnyFreeSpec
    with Matchers
    with MockitoSugar
    with ScalaFutures
    with BeforeAndAfterEach {

  override given patienceConfig: PatienceConfig =
    PatienceConfig(timeout = Span(5, Seconds), interval = Span(25, Millis))

  given ExecutionContext = ExecutionContext.global
  given HeaderCarrier    = HeaderCarrier()

  val mockNotificationDpsConnector: NotificationConnector    = mock[NotificationConnector]
  val mockGetSubscriptionConnector: GetSubscriptionConnector = mock[GetSubscriptionConnector]
  val mockCrmmConnector: CrmmConnector                       = mock[CrmmConnector]
  val mockDocumentumPackageService: DocumentumPackageService = mock[DocumentumPackageService]
  val mockPdfService: PdfService                             = mock[PdfService]
  val mockEmailService: EmailService                         = mock[EmailService]

  val service = new NotificationService(
    mockNotificationDpsConnector,
    mockGetSubscriptionConnector,
    mockCrmmConnector,
    mockDocumentumPackageService,
    mockPdfService,
    mockEmailService
  )

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockNotificationDpsConnector)
    reset(mockGetSubscriptionConnector)
    reset(mockCrmmConnector)
    reset(mockEmailService)
    reset(mockDocumentumPackageService)
    reset(mockPdfService)
  }

  def configureSubscriptionResponse(
      httpStatusCode: Int = 200,
      responseBody: String = Json.stringify(
        Json.toJson(
          GetSubscriptionDpsResponse(
            etmpSafeId = exampleSafeId,
            nominatedCompany = exampleNominatedCompany,
            contacts = exampleContacts
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
          NotificationDpsResponse(
            notificationRef = exampleNotificationReference
          )
        )
      )
  ): Unit = {
    when(
      mockNotificationDpsConnector.postNotification(
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
    when(mockPdfService.generateNotificationPdf(any())).thenReturn(objectStoreFileContent)
  }

  "postNotification" - {
    "DPS get subscription endpoint response is" - {
      "200 OK" - {
        "Parseable response; Continue down happy path" in {
          configureSubscriptionResponse()
          configureCrmmResponse()
          configureDpsResponse()
          configurePdfGeneration()
          configureDocumentumPackageService()

          service.postNotification(exampleSubscriptionId, incomingRequest).futureValue

          verify(mockEmailService, Times(1)).sendNotificationEmail(
            exampleContacts,
            exampleNominatedCompany.name,
            exampleNotificationReference
          )
          verify(
            mockCrmmConnector,
            Times(1)
          ).retrieveCustomer(meq(RetrieveCustomerRequest(Some(exampleCrn), Some(exampleUtr))))(using
            any()
          )
        }

        "Unparsable response; Return malformed response error" in {
          configureSubscriptionResponse(200, "{")

          val result = service.postNotification(exampleSubscriptionId, incomingRequest).futureValue

          verify(mockEmailService, Times(0)).sendNotificationEmail(any(), any(), any())(using any)
          result mustBe MalformedResponse(Subscription)
        }
      }

      "400 Bad Request; Return misalignment error" in {
        configureSubscriptionResponse(400)

        val result = service.postNotification(exampleSubscriptionId, incomingRequest).futureValue

        result mustBe Misalignment(Subscription)
      }

      "401 Unauthorized; Return downstream unauthorised error" in {
        configureSubscriptionResponse(401)

        val result = service.postNotification(exampleSubscriptionId, incomingRequest).futureValue

        result mustBe DownstreamUnauthorised(Subscription)
      }

      "403 Forbidden; Return downstream forbidden error" in {
        configureSubscriptionResponse(403)

        val result = service.postNotification(exampleSubscriptionId, incomingRequest).futureValue

        result mustBe DownstreamForbidden(Subscription)
      }

      "500 Internal Server Error; Return \"downstream service error\" error" in {
        configureSubscriptionResponse(500)

        val result = service.postNotification(exampleSubscriptionId, incomingRequest).futureValue

        result mustBe DownstreamServiceError(Subscription)
      }

      "503 Service Unavailable; Return downstream service unavailable error" in {
        configureSubscriptionResponse(503)

        val result = service.postNotification(exampleSubscriptionId, incomingRequest).futureValue

        result mustBe DownstreamServiceUnavailable(Subscription)
      }

      "an unknown response code; Return unknown failure error" in {
        configureSubscriptionResponse(618)

        val result = service.postNotification(exampleSubscriptionId, incomingRequest).futureValue

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

            service.postNotification(exampleSubscriptionId, incomingRequest).futureValue

            verify(
              mockNotificationDpsConnector,
              Times(1)
            ).postNotification(
              meq(exampleSubscriptionId),
              meq(
                NotificationDpsRequest(
                  companies = Nil,
                  customerId = Some(expectedCustomerId),
                  saos = Nil,
                  remarks = None,
                  staffPID = None
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

            service.postNotification(exampleSubscriptionId, incomingRequest).futureValue

            verify(
              mockNotificationDpsConnector,
              Times(1)
            ).postNotification(
              meq(exampleSubscriptionId),
              meq(
                NotificationDpsRequest(
                  companies = Nil,
                  customerId = None,
                  saos = Nil,
                  remarks = None,
                  staffPID = None
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

            val result = service.postNotification(exampleSubscriptionId, incomingRequest).futureValue

            result mustBe MalformedResponse(CRMM)
          }
        }

        "Unparsable response; Return malformed response error" in {
          configureSubscriptionResponse(200)
          configureCrmmResponse(
            200,
            "{"
          )

          val result = service.postNotification(exampleSubscriptionId, incomingRequest).futureValue

          result mustBe MalformedResponse(CRMM)
        }
      }

      "400 Bad Request; Return misalignment error" in {
        configureSubscriptionResponse(200)
        configureCrmmResponse(400)

        val result = service.postNotification(exampleSubscriptionId, incomingRequest).futureValue

        result mustBe Misalignment(CRMM)
      }

      "401 Unauthorized; Return downstream unauthorised error" in {
        configureSubscriptionResponse(200)
        configureCrmmResponse(401)

        val result = service.postNotification(exampleSubscriptionId, incomingRequest).futureValue

        result mustBe DownstreamUnauthorised(CRMM)
      }

      "403 Forbidden; Return downstream forbidden error" in {
        configureSubscriptionResponse(200)
        configureCrmmResponse(403)

        val result = service.postNotification(exampleSubscriptionId, incomingRequest).futureValue

        result mustBe DownstreamForbidden(CRMM)
      }

      "500 Internal Server Error; Return \"downstream service error\" error" in {
        configureSubscriptionResponse(200)
        configureCrmmResponse(500)

        val result = service.postNotification(exampleSubscriptionId, incomingRequest).futureValue

        result mustBe DownstreamServiceError(CRMM)
      }

      "503 Service Unavailable; Return downstream service unavailable error" in {
        configureSubscriptionResponse(200)
        configureCrmmResponse(503)

        val result = service.postNotification(exampleSubscriptionId, incomingRequest).futureValue

        result mustBe DownstreamServiceUnavailable(CRMM)
      }

      "an unknown response code; Return unknown failure error" in {
        configureSubscriptionResponse(200)
        configureCrmmResponse(618)

        val result = service.postNotification(exampleSubscriptionId, incomingRequest).futureValue

        result mustBe UnknownFailure(CRMM, 618)
      }
    }

    "DPS post notification customer endpoint response is" - {
      "201 Created" - {
        "Valid repsonse; Continue down happy path" in {
          configureSubscriptionResponse()
          configureCrmmResponse()
          configureDpsResponse(201, validDpsResponseBody)
          configurePdfGeneration()
          configureDocumentumPackageService()

          val result = service.postNotification(exampleSubscriptionId, incomingRequest).futureValue

          verify(mockEmailService, Times(1)).sendNotificationEmail(
            exampleContacts,
            exampleNominatedCompany.name,
            exampleNotificationReference
          )
          result mustBe Success(exampleNotificationReference, true)

          verify(mockDocumentumPackageService)
            .packageAndSubmit(
              meq(
                DocumentumPackageContext
                  .notification(exampleNotificationReference, exampleSubscriptionId, incomingRequest)
              ),
              meq(objectStoreFileContent)
            )(using any())
        }

        "Invalid response; Return malformed response error" in {
          configureSubscriptionResponse()
          configureCrmmResponse()
          configureDpsResponse(201, "{")

          val result = service.postNotification(exampleSubscriptionId, incomingRequest).futureValue
          verify(mockEmailService, Times(0)).sendNotificationEmail(any(), any(), any())(using any)
          result mustBe MalformedResponse(DPS)
        }
      }

      "400 Bad Request; Return misalignment error" in {
        configureSubscriptionResponse()
        configureCrmmResponse()
        configureDpsResponse(400)

        val result = service.postNotification(exampleSubscriptionId, incomingRequest).futureValue
        verify(mockEmailService, Times(0)).sendNotificationEmail(any(), any(), any())(using any)

        result mustBe Misalignment(DPS)
      }

      "401 Unauthorized; Return downstream unauthorised error" in {
        configureSubscriptionResponse()
        configureCrmmResponse()
        configureDpsResponse(401)

        val result = service.postNotification(exampleSubscriptionId, incomingRequest).futureValue

        verify(mockEmailService, Times(0)).sendNotificationEmail(any(), any(), any())(using any)
        result mustBe DownstreamUnauthorised(DPS)
      }

      "403 Forbidden; Return downstream forbidden error" in {
        configureSubscriptionResponse()
        configureCrmmResponse()
        configureDpsResponse(403)

        val result = service.postNotification(exampleSubscriptionId, incomingRequest).futureValue

        verify(mockEmailService, Times(0)).sendNotificationEmail(any(), any(), any())(using any)
        result mustBe DownstreamForbidden(DPS)
      }

      "500 Internal Server Error; Return \"downstream service error\" error" in {
        configureSubscriptionResponse()
        configureCrmmResponse()
        configureDpsResponse(500)

        val result = service.postNotification(exampleSubscriptionId, incomingRequest).futureValue

        verify(mockEmailService, Times(0)).sendNotificationEmail(any(), any(), any())(using any)
        result mustBe DownstreamServiceError(DPS)
      }

      "503 Service Unavailable; Return downstream service unavailable error" in {
        configureSubscriptionResponse()
        configureCrmmResponse()
        configureDpsResponse(503)

        val result = service.postNotification(exampleSubscriptionId, incomingRequest).futureValue

        verify(mockEmailService, Times(0)).sendNotificationEmail(any(), any(), any())(using any)
        result mustBe DownstreamServiceUnavailable(DPS)
      }

      "an unknown response code; Return unknown failure error" in {
        configureSubscriptionResponse()
        configureCrmmResponse()
        configureDpsResponse(618)

        val result = service.postNotification(exampleSubscriptionId, incomingRequest).futureValue

        verify(mockEmailService, Times(0)).sendNotificationEmail(any(), any(), any())(using any)
        result mustBe UnknownFailure(DPS, 618)
      }
    }
  }
}

object NotificationServiceSpec {
  val incomingRequest: NotificationRequest =
    NotificationRequest(
      NotificationCompanies(List.empty),
      Saos(List.empty),
      None
    )

  val exampleSubscriptionId        = "123"
  val exampleNotificationReference = "NOT0123456789"
  val exampleUtr                   = generateUtr
  val exampleCrn                   = generateCrn
  val exampleCompanyName           = "company name"
  val exampleSafeId                = "safe id"
  val exampleCustomerId            = "customer id"

  val validDpsResponseBody: String = s"""{"notificationRef":"$exampleNotificationReference"}"""
  val objectStorePath: String      = s"/senior-accounting-officer/${exampleNotificationReference}/"
  val objectStoreOwner             = "senior-accounting-officer"
  val objectStoreFileContent: Source[ByteString, NotUsed] = Source.single(ByteString("dummy file content"))

  val examplePdfFilename: String = s"${exampleNotificationReference}_SAO_Notification.pdf"
  val exampleZipFilename: String = s"20260728_${exampleNotificationReference}_SAO_Notification_OFFICIAL_SENSITIVE.ZIP"

  val exampleContacts: List[Contact] =
    List(Contact("name", "email@ex.com", "en", "ACTIVE"), Contact("name2", "email2@ex.com", "en", "ACTIVE"))
  val exampleNominatedCompany: NominatedCompany =
    NominatedCompany(crn = Some(exampleCrn), name = exampleCompanyName, utr = exampleUtr)
}
