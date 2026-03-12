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

import org.apache.pekko.stream.Materializer
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.http.HeaderNames.CONTENT_TYPE
import play.api.http.Status
import play.api.test.FakeRequest
import play.api.test.Helpers.*
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}
import uk.gov.hmrc.senioraccountingofficer.connectors.SubscriptionsConnector

import scala.concurrent.{ExecutionContext, Future}

class SubscriptionsControllerSpec extends AnyWordSpec with Matchers with GuiceOneAppPerSuite {

  given ExecutionContext = ExecutionContext.global
  given Materializer     = app.materializer

  private val validPayload =
    """{
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

  "SubscriptionsController" should {
    "return the downstream status when the stub responds without a body" in {
      val controller = new SubscriptionsController(
        stubControllerComponents(),
        new StubSubscriptionsConnector(Future.successful(HttpResponse(status = Status.NO_CONTENT)))
      )

      val result = controller.putSubscription(
        FakeRequest("PUT", "/subscriptions")
          .withHeaders(CONTENT_TYPE -> "application/json")
          .withTextBody(validPayload)
      )

      status(result) shouldBe Status.NO_CONTENT
    }

    "return the downstream JSON body for validation errors" in {
      val downstreamBody = """[{"path":"safeId","reason":"INVALID_FORMAT"}]"""
      val controller     = new SubscriptionsController(
        stubControllerComponents(),
        new StubSubscriptionsConnector(
          Future.successful(HttpResponse(status = Status.BAD_REQUEST, body = downstreamBody))
        )
      )

      val result = controller.putSubscription(
        FakeRequest("PUT", "/subscriptions")
          .withHeaders(CONTENT_TYPE -> "application/json")
          .withTextBody(validPayload)
      )

      status(result) shouldBe Status.BAD_REQUEST
      contentAsString(result) shouldBe downstreamBody
    }

  }

  private class StubSubscriptionsConnector(
      result: Future[HttpResponse]
  ) extends SubscriptionsConnector {
    override def putSubscription(body: String)(using HeaderCarrier): Future[HttpResponse] =
      result
  }
}
