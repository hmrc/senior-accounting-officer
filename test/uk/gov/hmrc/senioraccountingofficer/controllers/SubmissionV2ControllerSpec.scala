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

import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.{any, eq as meq}
import org.mockito.Mockito.*
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.{BeforeAndAfterEach, OptionValues}
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Application
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.Json
import play.api.mvc.Results
import play.api.test.FakeRequest
import play.api.test.Helpers.*
import uk.gov.hmrc.senioraccountingofficer.controllers.actions.FakeIdentifierAction.testSaoSubscriptionId
import uk.gov.hmrc.senioraccountingofficer.controllers.actions.{FakeIdentifierAction, IdentifierAction}
import uk.gov.hmrc.senioraccountingofficer.models.submission.*
import uk.gov.hmrc.senioraccountingofficer.services.submission.{SubmissionService, SubmissionTestData}

import scala.concurrent.Future

class SubmissionV2ControllerSpec
    extends AnyWordSpec
    with Matchers
    with OptionValues
    with MockitoSugar
    with GuiceOneAppPerSuite
    with BeforeAndAfterEach {

  val mockSubmissionService: SubmissionService = mock[SubmissionService]

  override def fakeApplication(): Application = GuiceApplicationBuilder()
    .configure("work-items.enabled" -> false)
    .overrides(
      bind[SubmissionService].toInstance(mockSubmissionService),
      bind[IdentifierAction].to[FakeIdentifierAction]
    )
    .build()

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockSubmissionService)
  }

  for kind <- SubmissionKind.values do {
    val endpoint = s"/senior-accounting-officer/v2/${kind.toString.toLowerCase}"
    val payload  = kind match {
      case SubmissionKind.Notification => Json.toJson(SubmissionTestData.notification)
      case SubmissionKind.Certificate  => Json.toJson(SubmissionTestData.certificate)
    }

    s"POST $endpoint" must {
      "accept validated requests with the authenticated subscription" in {
        when(mockSubmissionService.submit(any()))
          .thenReturn(Future.successful(Results.Accepted(Json.obj("idempotencyKey" -> "test-key"))))
        val request = FakeRequest(POST, endpoint).withHeaders("correlationId" -> "trace").withJsonBody(payload)
        val result  = route(app, request).value
        status(result) mustBe 202
        contentAsJson(result) mustBe Json.obj("idempotencyKey" -> "test-key")
        val captor = ArgumentCaptor.forClass(classOf[SubmissionData])
        verify(mockSubmissionService).submit(captor.capture())
        captor.getValue.subscriptionId mustBe testSaoSubscriptionId
        captor.getValue.correlationId mustBe "trace"
        captor.getValue.kind mustBe kind
      }

      "generate a usable key when omitted" in {
        when(mockSubmissionService.submit(any())).thenReturn(Future.successful(Results.Accepted))
        val result = route(
          app,
          FakeRequest(POST, endpoint)
            .withHeaders("correlationId" -> "trace")
            .withJsonBody(payload.as[play.api.libs.json.JsObject] - "idempotencyKey")
        ).value
        status(result) mustBe 202
        val captor = ArgumentCaptor.forClass(classOf[SubmissionData])
        verify(mockSubmissionService).submit(captor.capture())
        java.util.UUID.fromString(captor.getValue.idempotencyKey) mustBe a[java.util.UUID]
      }

      "reject malformed requests before enqueuing" in {
        val result =
          route(app, FakeRequest(POST, endpoint).withHeaders("correlationId" -> "trace").withTextBody("not-json")).value
        status(result) mustBe 400
        verifyNoInteractions(mockSubmissionService)
      }

      "reject missing correlation IDs before enqueuing" in {
        status(route(app, FakeRequest(POST, endpoint).withJsonBody(payload)).value) mustBe 400
        verifyNoInteractions(mockSubmissionService)
      }

    }

    s"GET $endpoint/:idempotencyKey" must {
      "look up the submission using the authenticated subscription, submission kind and URL key" in {
        when(mockSubmissionService.status(any(), any(), any())).thenReturn(Future.successful(Results.NoContent))
        val result =
          route(app, FakeRequest(GET, s"$endpoint/url-key").withHeaders("correlationId" -> "different-trace")).value
        status(result) mustBe 204
        verify(mockSubmissionService).status(meq(testSaoSubscriptionId), meq(kind), meq("url-key"))
      }
    }
  }
}
