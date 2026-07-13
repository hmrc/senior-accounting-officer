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

package uk.gov.hmrc.senioraccountingofficer.controllers.testOnly

import org.apache.pekko.util.ByteString
import play.api.http.MimeTypes
import play.api.libs.ws.{BodyWritable, InMemoryBody}
import play.api.mvc.*
import uk.gov.hmrc.http.*
import uk.gov.hmrc.http.HttpReads.Implicits.*
import uk.gov.hmrc.http.client.{HttpClientV2, RequestBuilder}
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController
import uk.gov.hmrc.senioraccountingofficer.config.AppConfig

import scala.concurrent.ExecutionContext

import java.net.{URL, URLDecoder}
import java.nio.charset.StandardCharsets
import javax.inject.Inject

class HipProxyController @Inject() (
    cc: ControllerComponents,
    httpClient: HttpClientV2,
    appConfig: AppConfig
)(using
    ExecutionContext
) extends BackendController(cc) {

  def proxy(path: String): Action[?] = Action(parse.raw).async { implicit request =>
    val headers =
      request.headers.headers.filter((header, _) => headersToKeep.exists(_.toLowerCase == header.toLowerCase))

    val bodyString: Option[String] = request.body.asBytes().map(_.utf8String)

    given BodyWritable[String] = BodyWritable(
      str => InMemoryBody(ByteString(str)),
      request.headers.headers
        .collectFirst { case (CONTENT_TYPE, contentType) =>
          contentType
        }
        .fold(MimeTypes.BINARY)(identity)
    )

    val http: URL => RequestBuilder = request.method match {
      case "GET"  => httpClient.get(_).setHeader(headers*)
      case "POST" =>
        httpClient
          .post(_: URL)
          .withBody[String](bodyString.fold("")(identity))
          .setHeader(headers*)
      case "PUT" =>
        httpClient
          .put(_)
          .withBody[String](bodyString.fold("")(identity))
          .setHeader(headers*)
    }

    val stubHost: String = appConfig.hipHost
    val targetUrl        = s"$stubHost/${URLDecoder.decode(path, StandardCharsets.UTF_8)}"
    http(url"$targetUrl")
      .execute[HttpResponse]
      .map(response =>
        Status(response.status)(response.body).as(response.header(CONTENT_TYPE).fold(MimeTypes.BINARY)(identity))
      )
  }

  private def headersToKeep = Seq(
    CONTENT_TYPE,
    AUTHORIZATION,
    "correlationId",
    "X-Transmitting-System",
    "X-Originating-System",
    "X-Receipt-Date"
  )

}
