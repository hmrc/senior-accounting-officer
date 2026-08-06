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

import com.github.tomakehurst.wiremock.client.WireMock.*
import support.ISpecBase
import uk.gov.hmrc.http.{HeaderCarrier, InternalServerException}
import uk.gov.hmrc.senioraccountingofficer.connectors.SdesConnectorIntegrationSpec.*

import java.util.UUID
import scala.util.Random

class SdesConnectorIntegrationSpec extends ISpecBase {

  private val connector = app.injector.instanceOf[SdesConnector]

  override def additionalConfigs: Map[String, Any] = Map(
    "microservice.services.secure-data-exchange-proxy.host" -> wireMockHost,
    "microservice.services.secure-data-exchange-proxy.port" -> wireMockPort
  )

  "SdesConnector" must {

    "return a Future.failed(InternalServerException) if header carrier does not contain 'correlationId'" in {
      given HeaderCarrier = HeaderCarrier()

      val e = intercept[InternalServerException] {
        connector
          .notifyFileReady(
            fileName = testFileName,
            owner = testOwner,
            objectStorePath = testFilePath,
            base64Checksum = testBase64CheckSum,
            contentLength = testFileSize
          )
          .futureValue
      }

      e.message mustBe "No correlationId found"

      verify(
        0,
        postRequestedFor(urlEqualTo("/notification/fileready"))
      )
    }

    Seq(204, 400, 401, 403, 500, 503).foreach { expectedStatus =>
      given HeaderCarrier = HeaderCarrier(extraHeaders = Seq("correlationId" -> correlationId))

      s"return a Future.successful(HttpResponse) for a $expectedStatus response from SDES Proxy" in {
        stubFor(
          post(urlEqualTo("/notification/fileready"))
            .willReturn(
              aResponse()
                .withStatus(expectedStatus)
                .withBody(testResponse)
            )
        )

        val result = connector
          .notifyFileReady(
            fileName = testFileName,
            owner = testOwner,
            objectStorePath = testFilePath,
            base64Checksum = testBase64CheckSum,
            contentLength = testFileSize
          )
          .futureValue

        result.status mustBe expectedStatus
        result.body mustBe testResponse

        verify(
          1,
          postRequestedFor(urlEqualTo("/notification/fileready"))
            .withRequestBody(equalToJson(expectedRequest))
        )
      }
    }

  }
}

object SdesConnectorIntegrationSpec {
  val correlationId: String = UUID.randomUUID().toString
  val testResponse          = ""
  val testOwner             = "testOwner"
  val testFileName          = "testFileName"
  val testFileSize: Int     = Random.nextInt(100)
  val testBase64CheckSum    = "ABC"
  val testHexCheckSum       = "0010"
  val testFilePath          = "test/url"

  val expectedRequest: String =
    s"""{
      |  "informationType" : "DSAO",
      |  "file" : {
      |    "recipientOrSender" : "Documentum",
      |    "name" : "$testFileName",
      |    "location" : "https://hsopriv.hmrc.gov.uk/object-store/object/$testOwner/$testFilePath",
      |    "checksum" : {
      |      "algorithm" : "md5",
      |      "value" : "$testHexCheckSum"
      |    },
      |    "size" : $testFileSize,
      |    "properties" : [ ]
      |  },
      |  "audit" : {
      |    "correlationID" : "$correlationId"
      |  }
      |}
      |""".stripMargin
}
