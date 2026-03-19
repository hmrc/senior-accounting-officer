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

package uk.gov.hmrc.senioraccountingofficer.connectors

import play.api.http.MimeTypes
import play.api.libs.ws.writeableOf_String
import uk.gov.hmrc.http.*
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.senioraccountingofficer.config.AppConfig

import scala.concurrent.{ExecutionContext, Future}

import javax.inject.Inject

class ContactDetailsConnector @Inject() (appConfig: AppConfig, httpClient: HttpClientV2)(using ExecutionContext) {
  def getContactDetails(id: String)(using HeaderCarrier): Future[HttpResponse] = {
    given HttpReads[HttpResponse] = HttpReads.Implicits.readRaw
    httpClient
      .get(url"${appConfig.stubsBaseUrl}/contact-details/$id")
      .setHeader("Authorization" -> appConfig.hipAuthorisationCredentials)
      .execute[HttpResponse]
  }

  def putContactDetails(id: String, body: String)(using HeaderCarrier): Future[HttpResponse] = {
    given HttpReads[HttpResponse] = HttpReads.Implicits.readRaw
    httpClient
      .put(url"${appConfig.stubsBaseUrl}/contact-details/$id")
      .setHeader("Authorization" -> appConfig.hipAuthorisationCredentials)
      .setHeader("Content-Type" -> MimeTypes.JSON)
      .withBody(body)
      .execute[HttpResponse]
  }
}
