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

import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.*
import org.scalatest.matchers.must.Matchers.mustBe
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.mockito.MockitoSugar.mock
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Application
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.test.Helpers.*
import play.api.test.{FakeRequest, Helpers}
import uk.gov.hmrc.http.HttpResponse
import uk.gov.hmrc.senioraccountingofficer.connectors.StubConnector

import scala.concurrent.Future

class ObligationControllerSpec extends AnyWordSpec with Matchers with GuiceOneAppPerSuite {

  val mockStubConnector: StubConnector = mock[StubConnector]

  override def fakeApplication(): Application = new GuiceApplicationBuilder()
    .overrides(bind[StubConnector].to(mockStubConnector))
    .build()

  "ObligationController" should {
    "return what the connector returns" in {
      val expectedStatus = 203
      val expectedBody   = "testBody"

      when(mockStubConnector.getObligation(any())(using any())) thenReturn Future.successful(
        HttpResponse(expectedStatus, expectedBody)
      )

      val url = routes.ObligationController.getObligation("123").url

      running(app) {
        val request = FakeRequest(GET, url)

        val result = route(app, request).get // TODO: no get?

        status(result) mustBe expectedStatus
        contentAsString(result) mustBe expectedBody
      }
    }
  }
}
