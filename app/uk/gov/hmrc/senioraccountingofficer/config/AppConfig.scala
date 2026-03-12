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

package uk.gov.hmrc.senioraccountingofficer.config

import play.api.Configuration
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import java.util.Base64
import javax.inject.Inject

class AppConfig @Inject() (servicesConfig: ServicesConfig, config: Configuration) {

  val appName: String = config.get[String]("appName")

  val stubsBaseUrl: String = servicesConfig.baseUrl("senior-accounting-officer-stubs")

  private val hipClientId: String     = config.get[String]("hip.clientId")
  private val hipClientSecret: String = config.get[String]("hip.clientSecret")

  val hipAuthorisationCredentials: String = {
    val encoded = Base64.getEncoder.encodeToString(s"$hipClientId:$hipClientSecret".getBytes("UTF-8"))
    s"Basic $encoded"
  }
  val appName: String                 = config.get[String]("appName")
  val stubsBaseUrl: String            = servicesConfig.baseUrl("senior-accounting-officer-stubs")
  val stubsAuthorizationToken: String =
    config.get[String]("microservice.services.senior-accounting-officer-stubs.authorizationToken")
  val appName: String      = config.get[String]("appName")
  val stubsBaseUrl: String = servicesConfig.baseUrl("senior-accounting-officer-stubs")
  val appName: String = config.get[String]("appName")

  // TODO: name subject to change, probably
  val stubsUrl: String = config.get[String]("stubs.host")

  // TODO: name subject to change
  val stubsAuth: String = config.get[String]("stubs.auth")
}
