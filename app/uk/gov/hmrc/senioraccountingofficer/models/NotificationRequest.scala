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

package uk.gov.hmrc.senioraccountingofficer.models

import play.api.libs.json.{Format, Json}
import uk.gov.hmrc.senioraccountingofficer.models.dps.NotificationDpsRequest
import uk.gov.hmrc.senioraccountingofficer.models.dps.{Company as DpsCompany, Sao as DpsSao}

final case class NotificationRequest(
    subscriptionId: String,
    companies: List[Company],
    saos: List[Sao],
    remarks: Option[String]
)

extension (notificationRequest: NotificationRequest) {

  def toNotificationDpsRequest: NotificationDpsRequest = {
    NotificationDpsRequest(
      companies = notificationRequest.companies.map(_.toDpsCompany),
      saos = notificationRequest.saos.map(_.toDpsSao),
      remarks = notificationRequest.remarks,
      staffPID = None
    )

  }
}

final case class Company(
    crn: Option[String] = None,
    utr: String,
    name: String,
    accPeriodEnd: String,
    status: String,
    `type`: String
)

extension (company: Company) {
  def toDpsCompany: DpsCompany = {
    DpsCompany(
      crn = company.crn,
      utr = company.utr,
      name = company.name,
      accPeriodEnd = company.accPeriodEnd,
      status = company.status,
      `type` = company.`type`
    )
  }
}

final case class Sao(
    name: String,
    fromDate: Option[String],
    email: Option[String],
    toDate: Option[String]
)

extension (sao: Sao) {
  def toDpsSao: DpsSao = {
    DpsSao(name = sao.name, fromDate = sao.fromDate, email = sao.email, toDate = sao.toDate)
  }
}

object NotificationRequest {
  given Format[NotificationRequest] = Json.format[NotificationRequest]
}

object Company {
  given Format[Company] = Json.format[Company]
}

object Sao {
  given Format[Sao] = Json.format[Sao]
}
