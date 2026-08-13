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
import uk.gov.hmrc.senioraccountingofficer.models.requests.{CompanyStatus, CompanyType, NotificationRequest}
import uk.gov.hmrc.senioraccountingofficer.services.PdfService
import uk.gov.hmrc.senioraccountingofficer.services.PdfService.*

import java.time.LocalDate

final case class NotificationDpsRequest(
    companies: List[Company],
    customerId: Option[String],
    saos: List[Sao],
    remarks: Option[String] = None,
    staffPID: Option[String] = None
)

final case class Company(
    crn: Option[String] = None,
    utr: String,
    name: String,
    accPeriodEnd: String,
    status: CompanyStatus,
    `type`: CompanyType
)

final case class Sao(
    name: String,
    fromDate: Option[String],
    toDate: Option[String]
)

object NotificationDpsRequest {
  given OFormat[NotificationDpsRequest] = Json.format[NotificationDpsRequest]

  def toPdfNotification(notificationRef: String, request: NotificationRequest, companyName: String): Notification = {
    val companies = request.companies.value.map(company => {

      Notification.Row(
        companyName = company.name.value,
        utr = company.utr.value,
        crn = company.crn.fold("Not Provided")(_.value),
        companyType = company.`type`,
        status = company.status,
        financialYearEndDate = company.accPeriodEnd.format(dateFormatter)
      )
    })
    val saos = request.saos.value.map(sao =>
      SaoTenure(
        name = sao.name.value,
        startDate = sao.fromDate.map(_.format(dateFormatter)),
        endDate = sao.toDate.map(_.format(dateFormatter))
      )
    )
    Notification(
      companyName = companyName,
      submissionDate = LocalDate.now().format(dateFormatter),
      submissionId = notificationRef,
      saoHistory = saos,
      companies = companies,
      additionalInformation = request.remarks.map(_.value)
    )
  }
}

object Company {
  given Format[Company] = Json.format[Company]
}

object Sao {
  given Format[Sao] = Json.format[Sao]
}
