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
import uk.gov.hmrc.senioraccountingofficer.services.PdfService.{Notification, SaoTenure}

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

  def toNotification(request: NotificationDpsRequest): Notification = {
    val companies = request.companies.map(company => {

      val `type`: "Plc" | "Ltd" = company.`type` match {
        case "Plc" => "Plc"
        case "Ltd" => "Ltd"
        case _     => throw Exception(s"could not parse company type into 'Plc' or 'Ltd', found ${company.`type`}")
      }
      val status: "Active" | "Dormant" | "Administration" | "Liquidation" = company.status match {
        case "Active"         => "Active"
        case "Dormant"        => "Dormant"
        case "Administration" => "Administration"
        case "Liquidation"    => "Liquidation"
        case _                =>
          throw Exception(
            s"could not parse company status into 'Active', 'Dormant', 'Administration', or 'Liquidation, found ${company.status}"
          )
      }

      Notification.Row(
        companyName = company.name,
        utr = company.utr,
        crn = company.crn.getOrElse(""),
        companyType = `type`,
        status = status,
        financialYearEndDate = company.accPeriodEnd
      )
    })
    val saos = request.saos.map(sao => SaoTenure(name = sao.name, startDate = sao.fromDate, endDate = sao.toDate))
    Notification(
      companyName = "test company",
      financialYearEndDate = "test date",
      submissionDate = "sub date",
      submissionId = "request.staffPID",
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
