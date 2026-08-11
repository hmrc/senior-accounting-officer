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

import org.mockito.Mockito.*
import org.scalatest.BeforeAndAfterEach
import org.scalatest.matchers.must.Matchers
import org.scalatestplus.mockito.MockitoSugar.mock
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Application
import play.api.http.{MimeTypes, Status}
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.{JsObject, Json}
import play.api.test.FakeRequest
import play.api.test.Helpers.*
import uk.gov.hmrc.senioraccountingofficer.controllers.actions.{FakeIdentifierAction, IdentifierAction}
import uk.gov.hmrc.senioraccountingofficer.services.CertificateService
import uk.gov.hmrc.senioraccountingofficer.utils.TestDataGenerator.*

import scala.concurrent.Future

import java.util.UUID

import SdesCallbackControllerSpec.*
import org.scalatest.concurrent.ScalaFutures
import play.api.mvc.AnyContentAsJson
import play.api.mvc.Result
import play.api.mvc.AnyContentAsText
import org.scalatest.freespec.AnyFreeSpec

class SdesCallbackControllerSpec
    extends AnyFreeSpec
    with Matchers
    with GuiceOneAppPerSuite
    with BeforeAndAfterEach
    with ScalaFutures {

  private val mockCertificateService = mock[CertificateService]

  override def fakeApplication(): Application =
    GuiceApplicationBuilder()
      .overrides(
        bind[CertificateService].toInstance(mockCertificateService),
        bind[IdentifierAction].to[FakeIdentifierAction]
      )
      .build()

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockCertificateService)
  }

  private def routeResult(request: FakeRequest[AnyContentAsText]): Future[Result] =
    route(app, request) match {
      case Some(result) => result
      case None         => fail("Expected route to be defined")
    }

  "callback" - {
    "return 204" - {
      "for a file processed notification" in {
        val request =
          FakeRequest("POST", routes.SdesCallbackController.callback.url)
            .withTextBody(fileProcessedPayload)
            .withHeaders(
              "Content-Type" -> MimeTypes.JSON
            )

        val result = routeResult(request)

        status(result) mustBe Status.NO_CONTENT
      }

      "for a file received notification" in {
        val request =
          FakeRequest("POST", routes.SdesCallbackController.callback.url)
            .withTextBody(fileReceivedPayload)
            .withHeaders(
              "Content-Type" -> MimeTypes.JSON
            )

        val result = routeResult(request)

        status(result) mustBe Status.NO_CONTENT
      }

      "for a file processing failure notification" in {
        val request =
          FakeRequest("POST", routes.SdesCallbackController.callback.url)
            .withTextBody(fileProcessingFailurePayload)
            .withHeaders(
              "Content-Type" -> MimeTypes.JSON
            )

        val result = routeResult(request)

        status(result) mustBe Status.NO_CONTENT
      }
    }
  }
}

object SdesCallbackControllerSpec {
  val crn = generateCrn
  val utr = generateUtr

  val fileReceivedPayload =
    """{
      |  "notification": "FileReceived",
      |  "filename": "filename",
      |  "correlationID": "80bb1bfe-9497-4168-9ac8-a7548cf83474",
      |  "checksumAlgorithm": "md5",
      |  "checksum": "c0f56e9aaee2584fb553c315d6a64617",
      |  "availableUntil": "2026-08-11T13:36:10.279Z",
      |  "dateTime": "2026-08-11T13:35:10.279Z",
      |  "properties": []
      |}""".stripMargin

  val fileProcessedPayload =
    """{
      |  "notification": "FileProcessed",
      |  "filename": "filename",
      |  "correlationID": "80bb1bfe-9497-4168-9ac8-a7548cf83474",
      |  "checksumAlgorithm": "md5",
      |  "checksum": "c0f56e9aaee2584fb553c315d6a64617",
      |  "availableUntil": "2026-08-11T13:36:10.287Z",
      |  "dateTime": "2026-08-11T13:35:10.287Z",
      |  "properties": []
      |}
      |""".stripMargin

  val fileProcessingFailurePayload =
    """{
      |  "notification": "FileProcessingFailure",
      |  "filename": "filename",
      |  "correlationID": "80bb1bfe-9497-4168-9ac8-a7548cf83474",
      |  "checksumAlgorithm": "md5",
      |  "checksum": "c0f56e9aaee2584fb553c315d6a64617",
      |  "availableUntil": "2026-08-11T13:36:10.279Z",
      |  "dateTime": "2026-08-11T13:35:10.279Z",
      |  "properties": []
      |}""".stripMargin
}
