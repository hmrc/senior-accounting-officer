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

package uk.gov.hmrc.senioraccountingofficer.services

import play.api.Logging
import play.api.http.Status.{ACCEPTED, BAD_REQUEST}
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}
import uk.gov.hmrc.senioraccountingofficer.connectors.EmailConnector
import uk.gov.hmrc.senioraccountingofficer.models.*
import uk.gov.hmrc.senioraccountingofficer.models.dps.Contact

import scala.concurrent.ExecutionContext
import scala.util.control.NonFatal

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

class EmailService @Inject() (
    emailConnector: EmailConnector
)(using ExecutionContext)
    extends Logging {

  private val dateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMMM yyyy 'at' hh:mma", Locale.ENGLISH)

  private def sendEmail(email: Email)(using HeaderCarrier): Unit = {
    emailConnector
      .postEmail(email)
      .map {
        case HttpResponse(ACCEPTED, _, _)    => ()
        case HttpResponse(BAD_REQUEST, _, _) =>
          logger.warn("Error from HMRC email service: status=400")
        case HttpResponse(status, _, _) =>
          logger.warn(s"Unexpected response from HMRC email service: status=$status")
      }
      .recover { case NonFatal(e) =>
        logger.warn(s"Unable to send registration confirmation email: ${e.getClass.getSimpleName}")
      }
  }
  def sendNotificationEmail(
      contacts: List[Contact],
      companyName: String,
      referenceId: String
  )(using HeaderCarrier): Unit = {
    val datetime = LocalDateTime.now().format(dateFormatter)

    contacts.foreach(contact => {
      val emailParameters = NotificationEmailParameters(
        templateId = EmailTemplate.NotificationConfirmation,
        companyName = companyName,
        submittedDateTime = datetime,
        referenceId = referenceId
      )
      val emailModel = NotificationEmail(List(contact.email), emailParameters)
      sendEmail(emailModel)
    })
  }

  def sendCertificateEmail(
      emailTemplate: EmailTemplate,
      email: String,
      recipientName: String,
      companyName: String,
      referenceId: String,
      submitterName: String,
      saoName: Option[String]
  )(using HeaderCarrier): Unit = {
    val datetime        = LocalDateTime.now().format(dateFormatter)
    val emailParameters = CertificateEmailParameters(
      templateId = emailTemplate,
      companyName = companyName,
      submittedDateTime = datetime,
      referenceId = referenceId,
      submitterName = submitterName,
      saoName = saoName
    )
    val emailModel = CertificateEmail(List(email), emailParameters)
    sendEmail(emailModel)
  }

}
