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

package uk.gov.hmrc.senioraccountingofficer.testOnly

import com.github.tomakehurst.wiremock.client.WireMock.*
import org.apache.pekko.util.ByteString
import play.api.http.{HeaderNames, MimeTypes}
import play.api.libs.ws.{BodyWritable, InMemoryBody}
import support.ISpecBase
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.senioraccountingofficer.testOnly.StubProxyIntegrationSpec.*

class StubProxyIntegrationSpec extends ISpecBase {

  implicit val hc: HeaderCarrier = HeaderCarrier()

  override def additionalConfigs: Map[String, Any] = Map(
    "microservice.services.hip.host" -> wireMockHost,
    "microservice.services.hip.port" -> wireMockPort,
    "application.router"             -> "testOnlyDoNotUseInAppConf.Routes"
  )

  "GET /test-only/stub-proxy/*path" must {
    "proxy a GET request to our stubs" in {
      val targetPath           = "/test/url/path"
      val authHeader           = "testToken"
      val correlationId        = "id"
      val transmittingSystem   = "Transmitting-System"
      val originatingSystem    = "Originating-System"
      val receiptDate          = "Receipt-Date"
      val targetResponseStatus = 201

      stubFor(
        get(urlEqualTo(targetPath))
          .willReturn(
            aResponse()
              .withStatus(targetResponseStatus)
          )
      )

      val result = wsClient
        .url(s"$baseUrl/test-only/stub-proxy$targetPath")
        .withHttpHeaders(
          HeaderNames.AUTHORIZATION -> authHeader,
          "correlationId"           -> correlationId,
          "X-Transmitting-System"   -> transmittingSystem,
          "X-Originating-System"    -> originatingSystem,
          "X-Receipt-Date"          -> receiptDate
        )
        .get()
        .futureValue

      result.status mustBe targetResponseStatus

      verify(
        1,
        getRequestedFor(urlEqualTo(targetPath))
          .withHeader(HeaderNames.AUTHORIZATION, equalTo(authHeader))
          .withHeader("correlationId", equalTo(correlationId))
          .withHeader("X-Transmitting-System", equalTo(transmittingSystem))
          .withHeader("X-Originating-System", equalTo(originatingSystem))
          .withHeader("X-Receipt-Date", equalTo(receiptDate))
      )
    }

    "not add Authorization or correlationId header if they are not specified" in {
      val targetPath           = "/test/url/path"
      val targetResponseStatus = 202

      stubFor(
        get(urlEqualTo(targetPath))
          .willReturn(
            aResponse()
              .withStatus(targetResponseStatus)
          )
      )

      val result = wsClient
        .url(s"$baseUrl/test-only/stub-proxy$targetPath")
        .get()
        .futureValue

      result.status mustBe targetResponseStatus

      verify(
        1,
        getRequestedFor(urlEqualTo(targetPath))
          .withoutHeader(HeaderNames.AUTHORIZATION)
          .withoutHeader("correlationId")
      )
    }

  }

  "POST /test-only/stub-proxy/*path" must {
    "proxy a POST request to our stubs" in {
      val targetPath           = "/test/url/path"
      val requestBodyRaw       = "{}"
      val correlationId        = "id"
      val authHeader           = "testToken"
      val requestContentType   = "application/json"
      val transmittingSystem = "Transmitting-System"
      val originatingSystem = "Originating-System"
      val receiptDate = "Receipt-Date"
      val targetResponseStatus = 201
      val targetResponseBody   = "test response"

      stubFor(
        post(urlEqualTo(targetPath))
          .willReturn(
            aResponse()
              .withStatus(targetResponseStatus)
              .withBody(targetResponseBody)
          )
      )

      given BodyWritable[String] = rawStringWriter(requestContentType)

      val result = wsClient
        .url(s"$baseUrl/test-only/stub-proxy$targetPath")
        .withHttpHeaders(        
          HeaderNames.AUTHORIZATION -> authHeader,
          "correlationId"           -> correlationId,
          "X-Transmitting-System"   -> transmittingSystem,
          "X-Originating-System"    -> originatingSystem,
          "X-Receipt-Date"          -> receiptDate
        )
        .post(requestBodyRaw)
        .futureValue

      result.status mustBe targetResponseStatus

      verify(
        1,
        postRequestedFor(urlEqualTo(targetPath))
          .withHeader(HeaderNames.AUTHORIZATION, equalTo(authHeader))
          .withHeader(HeaderNames.CONTENT_TYPE, equalTo(requestContentType))
          .withHeader("correlationId", equalTo(correlationId))
          .withHeader("X-Transmitting-System", equalTo(transmittingSystem))
          .withHeader("X-Originating-System", equalTo(originatingSystem))
          .withHeader("X-Receipt-Date", equalTo(receiptDate))
          .withRequestBody(equalTo(requestBodyRaw))
      )
    }

    "not add Authorization or correlationId header if they are not specified" in {
      val targetPath           = "/test/url/path"
      val requestBodyRaw       = "{}"
      val requestContentType   = "application/json"
      val targetResponseStatus = 202
      val targetResponseBody   = "test response"

      stubFor(
        post(urlEqualTo(targetPath))
          .willReturn(
            aResponse()
              .withStatus(targetResponseStatus)
              .withBody(targetResponseBody)
          )
      )

      given BodyWritable[String] = rawStringWriter(requestContentType)

      val result = wsClient
        .url(s"$baseUrl/test-only/stub-proxy$targetPath")
        .post(requestBodyRaw)
        .futureValue

      result.status mustBe targetResponseStatus

      verify(
        1,
        postRequestedFor(urlEqualTo(targetPath))
          .withHeader(HeaderNames.CONTENT_TYPE, equalTo(requestContentType))
          .withoutHeader(HeaderNames.AUTHORIZATION)
          .withoutHeader("correlationId")
      )
    }

    "permit invalid content for the given Content-Type to be sent" in {
      val targetPath           = "/test/url/path"
      val requestBodyRaw       = "{"
      val requestContentType   = "application/json"
      val targetResponseStatus = 203
      val targetResponseBody   = "test response"

      stubFor(
        post(urlEqualTo(targetPath))
          .willReturn(
            aResponse()
              .withStatus(targetResponseStatus)
              .withBody(targetResponseBody)
          )
      )

      given BodyWritable[String] = rawStringWriter(requestContentType)

      val result = wsClient
        .url(s"$baseUrl/test-only/stub-proxy$targetPath")
        .withHttpHeaders(HeaderNames.CONTENT_TYPE -> requestContentType)
        .post(requestBodyRaw)
        .futureValue

      result.status mustBe targetResponseStatus

      verify(
        1,
        postRequestedFor(urlEqualTo(targetPath))
          .withHeader(HeaderNames.CONTENT_TYPE, equalTo(requestContentType))
      )
    }

    "allow invalid content under the specified Content-Type header to be sent" in {
      val targetPath           = "/test/url/path"
      val requestBodyRaw       = "{}"
      val requestContentType   = "application/xml"
      val targetResponseStatus = 203
      val targetResponseBody   = "test response"

      stubFor(
        post(urlEqualTo(targetPath))
          .willReturn(
            aResponse()
              .withStatus(targetResponseStatus)
              .withBody(targetResponseBody)
          )
      )

      given BodyWritable[String] = rawStringWriter(requestContentType)

      val result = wsClient
        .url(s"$baseUrl/test-only/stub-proxy$targetPath")
        .post(requestBodyRaw)
        .futureValue

      result.status mustBe targetResponseStatus

      verify(
        1,
        postRequestedFor(urlEqualTo(targetPath))
          .withHeader(HeaderNames.CONTENT_TYPE, equalTo(requestContentType))
      )
    }

    "Content-Type of application/octet-stream will be used if unspecified" in {
      val targetPath           = "/test/url/path"
      val requestBodyRaw       = "{}"
      val targetResponseStatus = 203
      val targetResponseBody   = "test response"

      stubFor(
        post(urlEqualTo(targetPath))
          .willReturn(
            aResponse()
              .withStatus(targetResponseStatus)
              .withBody(targetResponseBody)
          )
      )

      given BodyWritable[String] = rawStringWriter("")

      val result = wsClient
        .url(s"$baseUrl/test-only/stub-proxy$targetPath")
        .post(requestBodyRaw)
        .futureValue

      result.status mustBe targetResponseStatus

      verify(
        1,
        postRequestedFor(urlEqualTo(targetPath))
          .withHeader(HeaderNames.CONTENT_TYPE, equalTo(MimeTypes.BINARY))
      )
    }

  }

  "PUT /test-only/stub-proxy/*path" must {
    "proxy a PUT request to our stubs" in {
      val targetPath           = "/test/url/path"
      val requestBodyRaw       = "{}"
      val correlationId        = "id"
      val authHeader           = "testToken"
      val requestContentType   = "application/json"
      val transmittingSystem = "Transmitting-System"
      val originatingSystem = "Originating-System"
      val receiptDate = "Receipt-Date"
      val targetResponseStatus = 201
      val targetResponseBody   = "test response"

      stubFor(
        put(urlEqualTo(targetPath))
          .willReturn(
            aResponse()
              .withStatus(targetResponseStatus)
              .withBody(targetResponseBody)
          )
      )

      given BodyWritable[String] = rawStringWriter(requestContentType)

      val result = wsClient
        .url(s"$baseUrl/test-only/stub-proxy$targetPath")
        .withHttpHeaders(
          HeaderNames.AUTHORIZATION -> authHeader,
          "correlationId"           -> correlationId,
          "X-Transmitting-System"   -> transmittingSystem,
          "X-Originating-System"    -> originatingSystem,
          "X-Receipt-Date"          -> receiptDate
        )
        .put(requestBodyRaw)
        .futureValue

      result.status mustBe targetResponseStatus

      verify(
        1,
        putRequestedFor(urlEqualTo(targetPath))
          .withHeader(HeaderNames.AUTHORIZATION, equalTo(authHeader))
          .withHeader(HeaderNames.CONTENT_TYPE, equalTo(requestContentType))
          .withHeader("correlationId", equalTo(correlationId))
          .withHeader("X-Transmitting-System", equalTo(transmittingSystem))
          .withHeader("X-Originating-System", equalTo(originatingSystem))
          .withHeader("X-Receipt-Date", equalTo(receiptDate))
          .withRequestBody(equalTo(requestBodyRaw))
      )
    }

    "not add Authorization or correlationId header if they are not specified" in {
      val targetPath           = "/test/url/path"
      val requestBodyRaw       = "{}"
      val requestContentType   = "application/json"
      val targetResponseStatus = 202
      val targetResponseBody   = "test response"

      stubFor(
        put(urlEqualTo(targetPath))
          .willReturn(
            aResponse()
              .withStatus(targetResponseStatus)
              .withBody(targetResponseBody)
          )
      )

      given BodyWritable[String] = rawStringWriter(requestContentType)

      val result = wsClient
        .url(s"$baseUrl/test-only/stub-proxy$targetPath")
        .put(requestBodyRaw)
        .futureValue

      result.status mustBe targetResponseStatus

      verify(
        1,
        putRequestedFor(urlEqualTo(targetPath))
          .withHeader(HeaderNames.CONTENT_TYPE, equalTo(requestContentType))
          .withoutHeader(HeaderNames.AUTHORIZATION)
          .withoutHeader("correlationId")
      )
    }

    "permit invalid content for the given Content-Type to be sent" in {
      val targetPath           = "/test/url/path"
      val requestBodyRaw       = "{"
      val requestContentType   = "application/json"
      val targetResponseStatus = 203
      val targetResponseBody   = "test response"

      stubFor(
        put(urlEqualTo(targetPath))
          .willReturn(
            aResponse()
              .withStatus(targetResponseStatus)
              .withBody(targetResponseBody)
          )
      )

      given BodyWritable[String] = rawStringWriter(requestContentType)

      val result = wsClient
        .url(s"$baseUrl/test-only/stub-proxy$targetPath")
        .withHttpHeaders(HeaderNames.CONTENT_TYPE -> requestContentType)
        .put(requestBodyRaw)
        .futureValue

      result.status mustBe targetResponseStatus

      verify(
        1,
        putRequestedFor(urlEqualTo(targetPath))
          .withHeader(HeaderNames.CONTENT_TYPE, equalTo(requestContentType))
      )
    }

    "allow invalid content under the specified Content-Type header to be sent" in {
      val targetPath           = "/test/url/path"
      val requestBodyRaw       = "{}"
      val requestContentType   = "application/xml"
      val targetResponseStatus = 203
      val targetResponseBody   = "test response"

      stubFor(
        put(urlEqualTo(targetPath))
          .willReturn(
            aResponse()
              .withStatus(targetResponseStatus)
              .withBody(targetResponseBody)
          )
      )

      given BodyWritable[String] = rawStringWriter(requestContentType)

      val result = wsClient
        .url(s"$baseUrl/test-only/stub-proxy$targetPath")
        .put(requestBodyRaw)
        .futureValue

      result.status mustBe targetResponseStatus

      verify(
        1,
        putRequestedFor(urlEqualTo(targetPath))
          .withHeader(HeaderNames.CONTENT_TYPE, equalTo(requestContentType))
      )
    }

    "Content-Type of application/octet-stream will be used if unspecified" in {
      val targetPath           = "/test/url/path"
      val requestBodyRaw       = "{}"
      val targetResponseStatus = 203
      val targetResponseBody   = "test response"

      stubFor(
        put(urlEqualTo(targetPath))
          .willReturn(
            aResponse()
              .withStatus(targetResponseStatus)
              .withBody(targetResponseBody)
          )
      )

      given BodyWritable[String] = rawStringWriter("")

      val result = wsClient
        .url(s"$baseUrl/test-only/stub-proxy$targetPath")
        .put(requestBodyRaw)
        .futureValue

      result.status mustBe targetResponseStatus

      verify(
        1,
        putRequestedFor(urlEqualTo(targetPath))
          .withHeader(HeaderNames.CONTENT_TYPE, equalTo(MimeTypes.BINARY))
      )
    }

  }

}

object StubProxyIntegrationSpec {

  def rawStringWriter(contentType: String): BodyWritable[String] =
    BodyWritable(
      str => InMemoryBody(ByteString(str)),
      contentType
    )

}
