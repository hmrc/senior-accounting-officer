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

package support

import com.github.tomakehurst.wiremock.client.WireMock.*
import com.github.tomakehurst.wiremock.stubbing.StubMapping

object SubmitNotificationHelper {
  def mock(subscriptionId: String, status: Int, body: Option[String]): StubMapping = {
    stubFor(
      post(urlEqualTo(s"/dapm/subscriptions/$subscriptionId/notifications"))
        .willReturn(
          body match {
            case Some(body) =>
              aResponse()
                .withStatus(status)
                .withBody(body)
            case None =>
              aResponse()
                .withStatus(status)
          }
        )
    )
  }

  def verifyCalled(subscriptionId: String, body: Option[String], times: Int): Unit = {
    val postRequest = postRequestedFor(urlEqualTo(s"/dapm/subscriptions/$subscriptionId/notifications"))
    verify(
      times,
      body.fold(postRequest)(body => postRequest.withRequestBody(equalToJson(body)))
    )
  }
}
