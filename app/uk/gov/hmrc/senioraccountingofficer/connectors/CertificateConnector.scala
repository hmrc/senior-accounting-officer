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

import uk.gov.hmrc.http.*
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse, StringContextOps}
import uk.gov.hmrc.senioraccountingofficer.config.AppConfig
import play.api.libs.ws.writeableOf_JsValue

import scala.concurrent.{ExecutionContext, Future}

import java.net.URL
import javax.inject.Inject
import uk.gov.hmrc.senioraccountingofficer.models.dps.CertificateDpsRequest
import play.api.libs.json.Json

class CertificateConnector @Inject() (appConfig: AppConfig, httpClientV2: HttpClientV2)(using ExecutionContext) {

  def postCertificate(id: String, request: CertificateDpsRequest)(using HeaderCarrier): Future[HttpResponse] = {
    given HttpReads[HttpResponse] = HttpReads.Implicits.readRaw
    val url: URL                  = url"${appConfig.hipHost}/dapm/subscriptions/$id/certificates"

    httpClientV2
      .post(url)
      .setHeader("Authorization" -> appConfig.hipAuthorisationCredentials)
      .withBody(Json.toJson(request))
      .execute[HttpResponse]
  }
}
