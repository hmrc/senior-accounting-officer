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

object ObjectStoreHelper {
  def mockPdfUpload(filename: String, status: Int, body: Option[String]): StubMapping = {
    stubFor(
      put(
        urlMatching(
          s"/object-store/object/senior-accounting-officer/senior-accounting-officer/[a-z0-9]+/$filename"
        )
      )
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

  def mockPdfRetrieval(filename: String, status: Int, body: Option[String]): StubMapping = {
    stubFor(
      get(
        urlMatching(
          s"/object-store/object/senior-accounting-officer/senior-accounting-officer/[a-z0-9]+/$filename"
        )
      )
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

  def mockZip(submissionId: String, status: Int, body: Option[String]): StubMapping = {
    stubFor(
      put(
        urlMatching(
          s"/object-store/object/senior-accounting-officer/sdes/$submissionId/[0-9]+_${submissionId}_SAO_Notification_OFFICIAL_SENSITIVE.zip"
        )
      )
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

  def verifyPdfUpload(filename: String, times: Int): Unit = {
    verify(
      times,
      putRequestedFor(
        urlMatching(s"/object-store/object/senior-accounting-officer/senior-accounting-officer/[a-z0-9]+/$filename")
      )
    )
  }

  def verifyZipUpload(submissionId: String, times: Int): Unit = {
    verify(
      times,
      putRequestedFor(
        // File(Directory(/sdes/NOT0008470194/),20260817_NOT0008470194_SAO_Notification_OFFICIAL_SENSITIVE.zip)
        urlMatching(
          s"/object-store/object/senior-accounting-officer/sdes/$submissionId/[0-9]+_${submissionId}_SAO_Notification_OFFICIAL_SENSITIVE.zip"
        )
      )
    )
  }

  def verifyPdfRetrieval(filename: String, times: Int): Unit = {
    verify(
      times,
      getRequestedFor(
        urlMatching(s"/object-store/object/senior-accounting-officer/senior-accounting-officer/[a-z0-9]+/$filename")
      )
    )
  }
}
