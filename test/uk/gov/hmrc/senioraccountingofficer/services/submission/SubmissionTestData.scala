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

package uk.gov.hmrc.senioraccountingofficer.services.submission

import uk.gov.hmrc.senioraccountingofficer.models.dps.{Contact, GetSubscriptionDpsResponse, NominatedCompany}
import uk.gov.hmrc.senioraccountingofficer.models.requests.*
import uk.gov.hmrc.senioraccountingofficer.models.submission.*
import uk.gov.hmrc.senioraccountingofficer.utils.TestDataGenerator.generateUtr

import java.time.{LocalDate, LocalDateTime}

object SubmissionTestData {

  val utr                               = generateUtr
  val date: LocalDate                   = LocalDate.parse("2025-12-31")
  val time: LocalDateTime               = LocalDateTime.parse("2026-10-05T10:00:00")
  val notification: NotificationRequest = NotificationRequest(
    NotificationCompanies(
      List(NotificationCompany(None, Utr(utr), CompanyName("Example Ltd"), date, CompanyStatus.Active, CompanyType.LTD))
    ),
    Saos(List(Sao(PersonName("Jane Smith"), None, None))),
    None,
    Some("test-key")
  )

  val certificate: CertificateRequest = CertificateRequest(
    None,
    PersonName("Jane Smith"),
    PersonName("Jane Smith"),
    Email("jane@example.com"),
    None,
    CertificateCompanies(
      List(
        CertificateCompany(
          crn = None,
          utr = Utr(utr),
          name = CompanyName("Example Ltd"),
          accPeriodEnd = date,
          status = CompanyStatus.Active,
          `type` = CompanyType.LTD,
          isCorporationTaxQualified = false,
          isVatQualified = false,
          isPayeQualified = false,
          isInsurancePremiumTaxQualified = false,
          isStampDutyLandTaxQualified = false,
          isStampDutyReserveTaxQualified = false,
          isPetroleumRevenueTaxQualified = false,
          isCustomsDutiesQualified = false,
          isExciseDutiesQualified = false,
          isBankLevyQualified = false,
          qualificationStatement = None
        )
      )
    ),
    None,
    Some("test-key")
  )

  val subscription: GetSubscriptionDpsResponse = GetSubscriptionDpsResponse(
    "safe-id",
    NominatedCompany(Some("12345678"), "Example Ltd", utr),
    List(
      Contact("Contact One", "one@example.com", "en", "active"),
      Contact("Contact Two", "two@example.com", "en", "active")
    ),
    time,
    time
  )

  def data(kind: SubmissionKind = SubmissionKind.Notification): SubmissionData = {
    val initial = kind match {
      case SubmissionKind.Notification => SubmissionData.notification("subscription", "correlation", notification)
      case SubmissionKind.Certificate  => SubmissionData.certificate("subscription", "correlation", certificate)
    }
    initial.copy(
      subscription = Some(subscription),
      customerNumber = Some("customer-number"),
      reference = Some("REF123"),
      submittedAt = Some(time)
    )
  }

  def command(step: SubmissionStep, kind: SubmissionKind = SubmissionKind.Notification): SubmissionCommand =
    SubmissionCommand.start(data(kind)).next(step)
}
