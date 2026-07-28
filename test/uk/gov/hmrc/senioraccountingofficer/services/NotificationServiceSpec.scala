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
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.eq as meq
import org.mockito.ArgumentMatchers.isNull
import org.mockito.Mockito.*
import org.mockito.Mockito.when
import org.mockito.internal.verification.Times
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatest.time.{Millis, Seconds, Span}
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.mockito.MockitoSugar
import play.api.libs.json.Json
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}
import uk.gov.hmrc.objectstore.client.Md5Hash
import uk.gov.hmrc.objectstore.client.ObjectSummaryWithMd5
import uk.gov.hmrc.objectstore.client.Path
import uk.gov.hmrc.objectstore.client.play.PlayObjectStoreClient
import uk.gov.hmrc.senioraccountingofficer.connectors.*
import uk.gov.hmrc.senioraccountingofficer.models.NotificationRequest
import uk.gov.hmrc.senioraccountingofficer.models.crmm.RetrieveCustomerRequest
import uk.gov.hmrc.senioraccountingofficer.models.crmm.RetrieveCustomerResponse
import uk.gov.hmrc.senioraccountingofficer.models.dps.GetSubscriptionDpsResponse
import uk.gov.hmrc.senioraccountingofficer.models.dps.NominatedCompany
import uk.gov.hmrc.senioraccountingofficer.models.dps.NotificationDpsRequest
import uk.gov.hmrc.senioraccountingofficer.models.dps.NotificationDpsResponse
import uk.gov.hmrc.senioraccountingofficer.services.NotificationService.DownstreamService
import uk.gov.hmrc.senioraccountingofficer.services.NotificationService.DownstreamService.*
import uk.gov.hmrc.senioraccountingofficer.utils.TestDataGenerator.generateCrn
import uk.gov.hmrc.senioraccountingofficer.utils.TestDataGenerator.generateUtr

import scala.concurrent.{ExecutionContext, Future}

import java.time.Instant
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

  val mockConnector: NotificationConnector                   = mock[NotificationConnector]
  val mockDocumentumPackageService: DocumentumPackageService = mock[DocumentumPackageService]
  val mockPdfService: PdfService                             = mock[PdfService]
  val service = new NotificationService(mockConnector, mockDocumentumPackageService, mockPdfService)

  "postNotification" must {
    "return Success if everything was orchestrated successfully" in {
      val mockResponse = HttpResponse(201, validDpsResponseBody)
      when(mockConnector.postNotification(any(), any())(using any())).thenReturn(Future.successful(mockResponse))
      when(mockPdfService.generateNotificationPdf(any())).thenReturn(objectStoreFileContent)
      when(mockDocumentumPackageService.packageAndSubmit(any(), any())(using any()))
        .thenReturn(Future.successful(DocumentumPackageResult(packageAvailable = true, Some(objectStoreFilename))))

      val result = service.postNotification(requestId, testRequest).futureValue

      result mustBe Success(notificationReference, true)
      verify(mockDocumentumPackageService)
        .packageAndSubmit(
          meq(DocumentumPackageContext.notification(notificationReference, requestId, testRequest)),
          meq(objectStoreFileContent)
        )(using any())
    }

    "return MalformedResponse(DPS) for a malformed 201 response from DPS" in {
      val malformedResponseBody = "{"
      val mockResponse          = HttpResponse(201, malformedResponseBody)
      when(mockConnector.postNotification(any(), any())(using any())).thenReturn(Future.successful(mockResponse))

      val result = service.postNotification(requestId, testRequest).futureValue

      result mustBe MalformedResponse(DPS)
    }

    "return MalformedResponse(DPS) for an invalid 201 response from DPS" in {
      val invalidResponseBody = "{}"
      val mockResponse        = HttpResponse(201, invalidResponseBody)
      when(mockConnector.postNotification(any(), any())(using any())).thenReturn(Future.successful(mockResponse))

      val result = service.postNotification(requestId, testRequest).futureValue

      result mustBe MalformedResponse(DPS)
    }

    "return BadRequestFailure(DPS) for an 400 response from DPS" in {
      val mockResponse = HttpResponse(400)
      when(mockConnector.postNotification(any(), any())(using any())).thenReturn(Future.successful(mockResponse))

      val result = service.postNotification(requestId, testRequest).futureValue

      result mustBe BadRequestFailure(DPS)
    }

    "return InternalServerFailure(DPS) for an 500 response from DPS" in {
      val mockResponse = HttpResponse(500)
      when(mockConnector.postNotification(any(), any())(using any())).thenReturn(Future.successful(mockResponse))

      val result = service.postNotification(requestId, testRequest).futureValue

      result mustBe InternalServerFailure(DPS)
    }

    "return ServiceUnavailableFailure(DPS) for an 503 response from DPS" in {
      val requestId    = "123"
      val mockResponse = HttpResponse(503)
      when(mockConnector.postNotification(any(), any())(using any())).thenReturn(Future.successful(mockResponse))

      val result = service.postNotification(requestId, testRequest).futureValue

      result mustBe ServiceUnavailableFailure(DPS)
    }

    "return UnknownFailure(DPS, status) for an unexpected status response from DPS" in {
      val unexpectedStatus = 600
      val mockResponse     = HttpResponse(unexpectedStatus)
      when(mockConnector.postNotification(any(), any())(using any())).thenReturn(Future.successful(mockResponse))

      val result = service.postNotification(requestId, testRequest).futureValue

      result mustBe UnknownFailure(DPS, unexpectedStatus)
    }
  val mockNotificationConnector: NotificationConnector       = mock[NotificationConnector]
  val mockGetSubscriptionConnector: GetSubscriptionConnector = mock[GetSubscriptionConnector]
  val mockCrmmConnector: CrmmConnector                       = mock[CrmmConnector]
  val mockObjectStoreClient: PlayObjectStoreClient           = mock[PlayObjectStoreClient]
  val mockPdfService: PdfService                             = mock[PdfService]
  val service                                                = new NotificationService(
    mockNotificationConnector,
    mockGetSubscriptionConnector,
    mockCrmmConnector,
    mockObjectStoreClient,
    mockPdfService
  )

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockNotificationConnector)
    reset(mockGetSubscriptionConnector)
    reset(mockCrmmConnector)
    reset(mockObjectStoreClient)
    reset(mockPdfService)
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
    when(mockGetSubscriptionConnector.getSubscription(meq(subscriptionId))(using any()))
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
      mockNotificationConnector.postNotification(
        meq(subscriptionId),
        any()
      )(using any())
    )
      .thenReturn(Future.successful(HttpResponse(httpStatusCode, responseBody)))
  }

  def configureObjectStore(): Unit = {
    when(
      mockObjectStoreClient.putObject(
        path = meq(
          Path
            .Directory(objectStorePath)
            .file(objectStoreFilename)
        ),
        content = meq(objectStoreFileContent),
        retentionPeriod = isNull,
        contentType = isNull,
        contentMd5 = isNull,
        owner = meq(objectStoreOwner)
      )(using any(), any())
    )
      .thenReturn(
        Future.successful(ObjectSummaryWithMd5(Path.File(objectStoreFilename), 0, Md5Hash("hash"), Instant.now))
      )
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
          configureObjectStore()

          service.postNotification(subscriptionId, testRequest).futureValue

          verify(
            mockCrmmConnector,
            Times(1)
          ).retrieveCustomer(meq(RetrieveCustomerRequest(Some(exampleCrn), Some(exampleUtr))))(using
            any()
          )
        }

        "Unparsable response; Return malformed response error" in {
          configureSubscriptionResponse(200, "{")

          val result = service.postNotification(subscriptionId, testRequest).futureValue

          result mustBe MalformedResponse(Subscription)
        }
      }

      "204 No Content; Return subscription not found error" in {
        configureSubscriptionResponse(204)

        val result = service.postNotification(subscriptionId, testRequest).futureValue

        result mustBe NotFoundFailure(Subscription)
      }

      "400 Bad Request; Return misalignment error" in {
        configureSubscriptionResponse(400)

        val result = service.postNotification(subscriptionId, testRequest).futureValue

        result mustBe Misalignment(Subscription)
      }

      "401 Unauthorized; Return service misconfiguration error" in {
        configureSubscriptionResponse(401)

        val result = service.postNotification(subscriptionId, testRequest).futureValue

        result mustBe Misconfiguration(Subscription, 401)
      }

      "403 Forbidden; Return service misconfiguration error" in {
        configureSubscriptionResponse(403)

        val result = service.postNotification(subscriptionId, testRequest).futureValue

        result mustBe Misconfiguration(Subscription, 403)
      }

      "500 Internal Server Error; Return \"downstream service error\" error" in {
        configureSubscriptionResponse(500)

        val result = service.postNotification(subscriptionId, testRequest).futureValue

        result mustBe DownstreamServiceError(Subscription)
      }

      "503 Service Unavailable; Return downstream service unavailable error" in {
        configureSubscriptionResponse(503)

        val result = service.postNotification(subscriptionId, testRequest).futureValue

        result mustBe DownstreamServiceUnavailable(Subscription)
      }

      "an unknown response code; Return unknown failure error" in {
        configureSubscriptionResponse(618)

        val result = service.postNotification(subscriptionId, testRequest).futureValue

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
            configureObjectStore()

            service.postNotification(subscriptionId, testRequest).futureValue

            verify(
              mockNotificationConnector,
              Times(1)
            ).postNotification(
              meq(subscriptionId),
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
            configureObjectStore()

            service.postNotification(subscriptionId, testRequest).futureValue

            verify(
              mockNotificationConnector,
              Times(1)
            ).postNotification(
              meq(subscriptionId),
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

            val result = service.postNotification(subscriptionId, testRequest).futureValue

            result mustBe MalformedResponse(CRMM)
          }
        }

        "Unparsable response; Return malformed response error" in {
          configureSubscriptionResponse(200)
          configureCrmmResponse(
            200,
            "{"
          )

          val result = service.postNotification(subscriptionId, testRequest).futureValue

          result mustBe MalformedResponse(CRMM)
        }
      }

      "400 Bad Request; Return misalignment error" in {
        configureSubscriptionResponse(200)
        configureCrmmResponse(400)

        val result = service.postNotification(subscriptionId, testRequest).futureValue

        result mustBe Misalignment(CRMM)
      }

      "401 Unauthorized; Return service misconfiguration error" in {
        configureSubscriptionResponse(200)
        configureCrmmResponse(401)

        val result = service.postNotification(subscriptionId, testRequest).futureValue

        result mustBe Misconfiguration(CRMM, 401)
      }

      "403 Forbidden; Return service misconfiguration error" in {
        configureSubscriptionResponse(200)
        configureCrmmResponse(403)

        val result = service.postNotification(subscriptionId, testRequest).futureValue

        result mustBe Misconfiguration(CRMM, 403)
      }

      "404 Not Found; Return misalignment error" in {
        configureSubscriptionResponse(200)
        configureCrmmResponse(404)

        val result = service.postNotification(subscriptionId, testRequest).futureValue

        result mustBe Misalignment(CRMM)
      }

      "500 Internal Server Error; Return \"downstream service error\" error" in {
        configureSubscriptionResponse(200)
        configureCrmmResponse(500)

        val result = service.postNotification(subscriptionId, testRequest).futureValue

        result mustBe DownstreamServiceError(CRMM)
      }

      "503 Service Unavailable; Return downstream service unavailable error" in {
        configureSubscriptionResponse(200)
        configureCrmmResponse(503)

        val result = service.postNotification(subscriptionId, testRequest).futureValue

        result mustBe DownstreamServiceUnavailable(CRMM)
      }

      "an unknown response code; Return unknown failure error" in {
        configureSubscriptionResponse(200)
        configureCrmmResponse(618)

        val result = service.postNotification(subscriptionId, testRequest).futureValue

        result mustBe UnknownFailure(CRMM, 618)
      }
    }

    "DPS post notification customer endpoint response is" - {
      "201 Created" - {
        "Invalid response; Return malformed response error" in {
          configureSubscriptionResponse()
          configureCrmmResponse()
          configureDpsResponse(201, "{")

          val result = service.postNotification(subscriptionId, testRequest).futureValue

          result mustBe MalformedResponse(DPS)
        }

        "Valid repsonse; Continue down happy path" in {
          configureSubscriptionResponse()
          configureCrmmResponse()
          configureDpsResponse(201, validDpsResponseBody)
          configurePdfGeneration()
          configureObjectStore()

          val result = service.postNotification(subscriptionId, testRequest).futureValue

          result mustBe Success(exampleNotificationReference, true)
        }
      }

      "400 Bad Request; Return misalignment error" in {
        configureSubscriptionResponse()
        configureCrmmResponse()
        configureDpsResponse(400)

        val result = service.postNotification(subscriptionId, testRequest).futureValue

        result mustBe Misalignment(DPS)
      }

      "401 Unauthorized; Return service misconfiguration error" in {
        configureSubscriptionResponse()
        configureCrmmResponse()
        configureDpsResponse(401)

        val result = service.postNotification(subscriptionId, testRequest).futureValue

        result mustBe Misconfiguration(DPS, 401)
      }

      "403 Forbidden; Return service misconfiguration error" in {
        configureSubscriptionResponse()
        configureCrmmResponse()
        configureDpsResponse(403)

        val result = service.postNotification(subscriptionId, testRequest).futureValue

        result mustBe Misconfiguration(DPS, 403)
      }

      "404 Not Found; Return misalignment error" in {
        configureSubscriptionResponse()
        configureCrmmResponse()
        configureDpsResponse(404)

        val result = service.postNotification(subscriptionId, testRequest).futureValue

        result mustBe Misalignment(DPS)
      }

      "500 Internal Server Error; Return \"downstream service error\" error" in {
        configureSubscriptionResponse()
        configureCrmmResponse()
        configureDpsResponse(500)

        val result = service.postNotification(subscriptionId, testRequest).futureValue

        result mustBe DownstreamServiceError(DPS)
      }

      "503 Service Unavailable; Return downstream service unavailable error" in {
        configureSubscriptionResponse()
        configureCrmmResponse()
        configureDpsResponse(503)

        val result = service.postNotification(subscriptionId, testRequest).futureValue

        result mustBe DownstreamServiceUnavailable(DPS)
      }

      "an unknown response code; Return unknown failure error" in {
        configureSubscriptionResponse()
        configureCrmmResponse()
        configureDpsResponse(618)

        val result = service.postNotification(subscriptionId, testRequest).futureValue

        result mustBe UnknownFailure(DPS, 618)
      }
    }
  }
}

object NotificationServiceSpec {
  val requestId                           = "123"
  val testRequest: NotificationDpsRequest = NotificationDpsRequest(List.empty, List.empty)
  val notificationReference               = "NOT0123456789"
  val validDpsResponseBody: String        = s"""{"notificationRef":"$notificationReference"}"""
  val objectStoreFilename: String         = s"20260728_${notificationReference}_SAO_Notification_OFFICIAL_SENSITIVE.ZIP"
  val subscriptionId                   = "123"
  val customerId                       = "example customer id"
  val testRequest: NotificationRequest =
    NotificationRequest(
      List.empty,
      List.empty,
      None
    ) // TODO: do i need to test with no customer id passed?
  val exampleNotificationReference = "NOT0123456789"
  val validDpsResponseBody: String = s"""{"notificationRef":"$exampleNotificationReference"}"""
  val objectStorePath: String      = s"/senior-accounting-officer/${exampleNotificationReference}/"
  val objectStoreFilename: String  = s"${exampleNotificationReference}_SAO_Notification.pdf"
  val objectStoreOwner             = "senior-accounting-officer"
  val objectStoreFileContent: Source[ByteString, NotUsed] = Source.single(ByteString("dummy file content"))

  val exampleUtr         = generateUtr
  val exampleCrn         = generateCrn
  val exampleCompanyName = "company name"
  val exampleSafeId      = "safe id"
  val exampleCustomerId  = "customer id"
}
