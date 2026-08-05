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
import uk.gov.hmrc.http.HttpReads.Implicits.*
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import scala.concurrent.{ExecutionContext, Future}

import java.net.{URI, URL}
import java.util.Base64
import javax.inject.Inject

class SdesConnector @Inject() (
    httpClientV2: HttpClientV2,
    servicesConfig: ServicesConfig
)(using ExecutionContext) {

  def notifyFileReady(
      fileName: String,
      owner: String,
      objectStorePath: String,
      checksum: String,
      contentLength: Long
  )(using HeaderCarrier): Future[HttpResponse] = {
    val url: URL = URI
      .create(s"${servicesConfig.baseUrl("secure-data-exchange-proxy")}$fileReadyNotificationPath")
      .toURL

    httpClientV2
      .post(url)
      .setHeader("X-Client-ID" -> clientId)
      .withBody(
        buildFileReadyPayload(fileName, owner, objectStorePath, checksum, contentLength)
      )
      .execute[HttpResponse]
  }

  private[connectors] def buildFileReadyPayload(
      fileName: String,
      owner: String,
      objectStorePath: String,
      checksum: String,
      contentLength: Long
  ) =
    Json.obj(
      "informationType" -> servicesConfig.getString("secure-data-exchange-proxy.informationType"),
      "file"            -> Json.obj(
        "recipientOrSender" -> servicesConfig.getString("secure-data-exchange-proxy.recipientOrSender"),
        "name"              -> fileName,
        "location" -> s"${servicesConfig.getString("object-store.sdes-host")}/${owner.stripPrefix("/")}/${objectStorePath.stripPrefix("/")}",
        "checksum" -> Json.obj(
          "algorithm" -> "md5",
          "value"     -> base64Md5ToHex(checksum)
        ),
        "size"       -> contentLength,
        "properties" -> Json.arr()
      ),
      "audit" -> Json.obj(
        "correlationID" -> fileName
      )
    )

  private[connectors] def base64Md5ToHex(checksum: String): String =
    Base64.getDecoder.decode(checksum).map(byte => f"${byte & 0xff}%02x").mkString

  private[connectors] def clientId: String =
    servicesConfig.getString("secure-data-exchange-proxy.clientId")

  private val fileReadyNotificationPath = "/notification/fileready"
}
