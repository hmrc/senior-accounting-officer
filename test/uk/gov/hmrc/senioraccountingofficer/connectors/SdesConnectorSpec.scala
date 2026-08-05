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

import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.Json

class SdesConnectorSpec extends AnyWordSpec with Matchers with GuiceOneAppPerSuite {

  override def fakeApplication(): Application =
    GuiceApplicationBuilder()
      .configure(
        "object-store.sdes-host"                       -> "https://configured-object-store.example/object",
        "secure-data-exchange-proxy.clientId"          -> "configured-client-id",
        "secure-data-exchange-proxy.informationType"   -> "DSAO",
        "secure-data-exchange-proxy.recipientOrSender" -> "Documentum"
      )
      .build()

  private val connector = app.injector.instanceOf[SdesConnector]

  "clientId" must {
    "read the SDES client ID from config" in {
      connector.clientId mustBe "configured-client-id"
    }
  }

  "buildFileReadyPayload" must {
    "build the SDES file-ready payload with the object-store location and hex MD5 checksum" in {
      val payload = connector.buildFileReadyPayload(
        fileName = "20260728_NOT0123456789_SAO_Notification_OFFICIAL_SENSITIVE.zip",
        owner = "senior-accounting-officer",
        objectStorePath = "/sdes/NOT0123456789/20260728_NOT0123456789_SAO_Notification_OFFICIAL_SENSITIVE.zip",
        checksum = "1B2M2Y8AsgTpgAmY7PhCfg==",
        contentLength = 100L
      )

      payload mustBe Json.obj(
        "informationType" -> "DSAO",
        "file"            -> Json.obj(
          "recipientOrSender" -> "Documentum",
          "name"              -> "20260728_NOT0123456789_SAO_Notification_OFFICIAL_SENSITIVE.zip",
          "location"          ->
            "https://configured-object-store.example/object/senior-accounting-officer/sdes/NOT0123456789/20260728_NOT0123456789_SAO_Notification_OFFICIAL_SENSITIVE.zip",
          "checksum" -> Json.obj(
            "algorithm" -> "md5",
            "value"     -> "d41d8cd98f00b204e9800998ecf8427e"
          ),
          "size"       -> 100L,
          "properties" -> Json.arr()
        ),
        "audit" -> Json.obj(
          "correlationID" -> "20260728_NOT0123456789_SAO_Notification_OFFICIAL_SENSITIVE.zip"
        )
      )
    }
  }
}
