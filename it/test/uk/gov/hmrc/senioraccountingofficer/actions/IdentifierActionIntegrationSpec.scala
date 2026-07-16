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

package uk.gov.hmrc.senioraccountingofficer.actions

/*
 * Copyright 2025 HM Revenue & Customs
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

import play.api.http.{HeaderNames, Status}
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.ws.readableAsString
import play.api.mvc.*
import support.MockAuthHelper.{testBearerToken, testId, testSubscriptionId}
import support.{ISpecBase, MockAuthHelper}
import uk.gov.hmrc.senioraccountingofficer.actions.IdentifierActionIntegrationSpec.*
import uk.gov.hmrc.senioraccountingofficer.controllers.actions.IdentifierAction

class IdentifierActionIntegrationSpec extends ISpecBase {

  override def applicationBuilder: GuiceApplicationBuilder =
    GuiceApplicationBuilder()
      .appRoutes { app =>
        val identifierAction = app.injector.instanceOf[IdentifierAction]

        { case ("GET", testUrl) =>
          identifierAction { request =>
            Results.Ok(testSuccessBody(request.userId, request.saoSubscriptionId))
          }
        }
      }

  def targetUrl = s"$baseUrl$testPath"

  "An endpoint with Auth Action" when {
    "Authorization Bearer Token is missing" must {
      "respond with a 401 to login" in {
        val response =
          wsClient
            .url(targetUrl)
            .get()
            .futureValue

        MockAuthHelper.verifyAuthWasCalled(times = 0)
        response.status mustBe Status.UNAUTHORIZED
        response.body[String] mustBe """{"reason":"UNAUTHENTICATED"}"""
      }
    }

    "Auth is successful and returned an HMRC-DSAO-ORG Enrolment" must {
      "pass the action successfully" in {
        MockAuthHelper.mockAuthOk()

        val response =
          wsClient
            .url(targetUrl)
            .withHttpHeaders(
              HeaderNames.AUTHORIZATION -> testBearerToken
            )
            .get()
            .futureValue

        MockAuthHelper.verifyAuthWasCalled()
        response.status mustBe Status.OK
        response.body[String] mustBe testSuccessBody(testId, testSubscriptionId)
      }
    }

    "Auth is successful but did not return an HMRC-DSAO-ORG Enrolment" must {
      "respond with a 403" in {
        MockAuthHelper.mockAuthNoEnrolments()

        val response =
          wsClient
            .url(targetUrl)
            .withHttpHeaders(
              HeaderNames.AUTHORIZATION -> testBearerToken
            )
            .get()
            .futureValue

        MockAuthHelper.verifyAuthWasCalled()
        response.status mustBe Status.FORBIDDEN
        response.body[String] mustBe """{"reason":"NO_ENROLMENT"}"""
      }
    }

    "Auth did not respond with the required retrievals" must {
      "respond with a 500" in {
        MockAuthHelper.mockAuthNoId()

        val response =
          wsClient
            .url(targetUrl)
            .withHttpHeaders(
              HeaderNames.AUTHORIZATION -> testBearerToken
            )
            .get()
            .futureValue

        MockAuthHelper.verifyAuthWasCalled()
        response.status mustBe Status.INTERNAL_SERVER_ERROR
      }
    }
  }

}

object IdentifierActionIntegrationSpec {
  val testPath                                                   = "/test-identifier-action"
  def testSuccessBody(userId: String, saoSubscriptionId: String) =
    s"Action Passed Successfully $userId, $saoSubscriptionId"
}
