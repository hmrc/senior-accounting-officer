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

import play.api.libs.json.Json
import play.api.libs.ws.writeableOf_JsValue
import uk.gov.hmrc.http.*
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.senioraccountingofficer.config.AppConfig
import uk.gov.hmrc.senioraccountingofficer.models.crmm.RetrieveCustomerRequest

import scala.concurrent.{ExecutionContext, Future}

import javax.inject.Inject

class CrmmConnector @Inject() (
    httpClientV2: HttpClientV2,
    appConfig: AppConfig
)(using ExecutionContext) {
  def retrieveCustomer(
      request: RetrieveCustomerRequest
  )(using hc: HeaderCarrier): Future[HttpResponse] = {
    given HttpReads[HttpResponse] = HttpReads.Implicits.readRaw
    val url = url"${appConfig.hipHost}/compliance/civil-investigation-and-avoidance/api/customer/v1/retrievecustomer"

    if appConfig.crmmEnabled
    then
      httpClientV2
        .post(url)
        .setHeader("Authorization" -> appConfig.hipAuthorisationCredentials)
        .withBody(Json.toJson(request))
        .execute[HttpResponse]
    else
      Future.successful(
        HttpResponse(
          200,
          Json.obj().toString()
        )
      )
  }
}
