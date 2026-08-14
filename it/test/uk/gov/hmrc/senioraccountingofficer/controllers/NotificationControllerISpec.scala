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

import support.*
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.senioraccountingofficer.config.AppConfig
import play.api.test.FakeRequest
import support.MockAuthHelper.{testBearerToken, testId, testSubscriptionId}
import play.api.http.{HeaderNames, Status}
import support.MockAuthHelper
import play.api.libs.ws.readableAsString
import play.api.inject.guice.GuiceApplicationBuilder
import uk.gov.hmrc.senioraccountingofficer.controllers.actions.IdentifierAction
import play.api.mvc.Results.*
import java.net.URI
import play.api.libs.ws.{BodyWritable, InMemoryBody}
import org.apache.pekko.util.ByteString
import uk.gov.hmrc.senioraccountingofficer.controllers.NotificationControllerISpec.*
import com.github.tomakehurst.wiremock.client.WireMock.*
import play.api.libs.ws.WSResponse

class NotificationControllerISpec extends ISpecBase {
  private val appConfig = app.injector.instanceOf[AppConfig]

  given HeaderCarrier = HeaderCarrier()

  override def additionalConfigs: Map[String, Any] = Map(
    "microservice.services.hip.host" -> wireMockHost,
    "microservice.services.hip.port" -> wireMockPort
  )

  given BodyWritable[String] = rawStringWriter("application/json")

  def makeRequest(body: String): WSResponse = {
    wsClient
      .url(s"$baseUrl/senior-accounting-officer/notification")
      .withHttpHeaders(
        HeaderNames.AUTHORIZATION -> MockAuthHelper.testBearerToken,
        "correlationId"           -> "d80fd83a-2c50-4967-b596-675f7f11e241"
      )
      .post(body)
      .futureValue
  }

  "POST /notification" when {
    "GetSubscription" when {
      // TODO: 200 and we can parse it

      "returns 200 with a response body that cannot be parsed" must {
        "return a 500 response reason DOWNSTREAM_SERVICE_MISALIGNMENT" in {
          MockAuthHelper.mockAuthOk()
          GetSubscriptionHelper.mock(MockAuthHelper.testSubscriptionId, 200, Some(unparsableResponse))

          val response = makeRequest(requestBody)

          response.status mustBe 500
          response.body[String] mustBe s"""{"reason":"DOWNSTREAM_SERVICE_MISALIGNMENT"}"""
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
          }
        }
      })
    }

    "RetrieveCustomer" when {
      // TODO: 200, we can parse it and customer found response (assert it is passed to DPS)
      // TODO: 200, we can parse it and customer not found response (assert it is not passed to DPS)

      "returns 200 with a response body that cannot be parsed" must {
        "return a 500 response reason DOWNSTREAM_SERVICE_MISALIGNMENT" in {
          MockAuthHelper.mockAuthOk()
          GetSubscriptionHelper.mock(MockAuthHelper.testSubscriptionId, 200, Some(getSubscriptionResponse))
          RetrieveCustomerHelper.mock(200, Some(unparsableResponse))

          val response = makeRequest(requestBody)

          response.status mustBe 500
          response.body[String] mustBe s"""{"reason":"DOWNSTREAM_SERVICE_MISALIGNMENT"}"""
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
          }
        }
      })
    }

    "SubmitNotification" when {
      // TODO: 201 and we can parse it

      "returns 201 with a response body that cannot be parsed" must {
        "return a 500 response reason DOWNSTREAM_SERVICE_MISALIGNMENT" in {
          MockAuthHelper.mockAuthOk()
          GetSubscriptionHelper.mock(MockAuthHelper.testSubscriptionId, 200, Some(getSubscriptionResponse))
          RetrieveCustomerHelper.mock(200, Some(retrieveCustomerResponseCustomerFound))
          SubmitNotificationHelper.mock(MockAuthHelper.testSubscriptionId, 201, Some(unparsableResponse))

          val response = makeRequest(requestBody)

          response.status mustBe 500
          response.body[String] mustBe s"""{"reason":"DOWNSTREAM_SERVICE_MISALIGNMENT"}"""
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
          }
        }
      })
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

  val getSubscriptionResponse = """{
                                  |  "etmpSafeId": "1234567890",
                                  |  "contacts": [
                                  |    {
                                  |      "name": "Tester Eve",
                                  |      "email": "eve.tester@test.com",
                                  |      "language": "en",
                                  |      "status": "valid"
                                  |    },
                                  |    {
                                  |      "name": "Tester Adams",
                                  |      "email": "admas.tester@test.com",
                                  |      "language": "cy",
                                  |      "status": "valid"
                                  |    }
                                  |  ],
                                  |  "nominatedCompany": {
                                  |    "crn": "04613156",
                                  |    "name": "Fake Company Ltd",
                                  |    "utr": "1000098671"
                                  |  }
                                  |}""".stripMargin

  val retrieveCustomerResponseCustomerFound = """{
                                                |  "customerId": "02839521",
                                                |  "existingCustomer": true,
                                                |  "status": "Success"
                                                |}""".stripMargin

  val retrieveCustomerResponseCustomerNotFound = """{
                                                   |  "errorDescription": "error",
                                                   |  "existingCustomer": false,
                                                   |  "status": "Failure"
                                                   |}""".stripMargin
}
