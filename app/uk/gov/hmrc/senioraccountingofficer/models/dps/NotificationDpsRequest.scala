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

package uk.gov.hmrc.senioraccountingofficer.models.dps

import play.api.libs.json.{Format, Json, OFormat}
import uk.gov.hmrc.senioraccountingofficer.services.PdfService
import uk.gov.hmrc.senioraccountingofficer.services.PdfService.*

import java.time.LocalDate

final case class NotificationDpsRequest(
    companies: List[Company],
    saos: List[Sao],
    remarks: Option[String] = None,
    staffPID: Option[String] = None
)

final case class Company(
    crn: Option[String] = None,
    utr: String,
    name: String,
    accPeriodEnd: String,
    status: String,
    `type`: String
)

final case class Sao(
    name: String,
    fromDate: Option[String],
    email: Option[String] = None,
    toDate: Option[String]
)

object NotificationDpsRequest {
  given OFormat[NotificationDpsRequest] = Json.format[NotificationDpsRequest]

  def toNotification(notificationRef: String, request: NotificationDpsRequest, companyName: String): Notification = {
    val companies = request.companies.map(company => {

      Notification.Row(
        companyName = company.name,
        utr = company.utr,
        crn = company.crn.fold("")(identity),
        companyType = toCompanyType(company.`type`),
        status = toStatus(company.status),
        financialYearEndDate = company.accPeriodEnd
      )
    })
    val saos = request.saos.map(sao => SaoTenure(name = sao.name, startDate = sao.fromDate, endDate = sao.toDate))
    Notification(
      companyName = companyName,
      submissionDate = LocalDate.now().toString,
      submissionId = notificationRef,
      saoHistory = saos,
      companies = companies,
      additionalInformation = request.remarks
    )
  }
}

object Company {
  given Format[Company] = Json.format[Company]
}

object Sao {
  given Format[Sao] = Json.format[Sao]
}
