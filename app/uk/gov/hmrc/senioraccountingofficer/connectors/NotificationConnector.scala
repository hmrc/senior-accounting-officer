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
import uk.gov.hmrc.http.HttpReads.Implicits.*
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse, StringContextOps}
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.senioraccountingofficer.config.AppConfig
import uk.gov.hmrc.senioraccountingofficer.models.NotificationRequest
import play.api.libs.ws.JsonBodyWritables.writeableOf_JsValue

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import java.net.URL

@Singleton
class NotificationConnector @Inject() (
                                        httpClientV2: HttpClientV2,
                                        appConfig: AppConfig
                                      )(implicit ec: ExecutionContext) {

  def postNotification(id: String, request: NotificationRequest)(implicit hc: HeaderCarrier): Future[HttpResponse] = {
    val url: URL = url"http://${appConfig.notificationBaseUrl}/notification/$id"

    httpClientV2
      .post(url)
      .withBody(Json.toJson(request))
      .execute[HttpResponse]
  }
}
