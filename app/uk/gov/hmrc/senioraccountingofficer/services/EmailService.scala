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

import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

import uk.gov.hmrc.senioraccountingofficer.services.EmailService.EmailRejected

class EmailService @Inject() (
    emailConnector: EmailConnector
)(using ExecutionContext)
    extends Logging {

  private val dateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMMM yyyy 'at' hh:mma", Locale.ENGLISH)

  private def sendEmail(email: Email, emailType: String)(using HeaderCarrier): Future[Unit] = {
    val correlationId = summon[HeaderCarrier].extraHeaders
      .collectFirst { case (name, value) if name.equalsIgnoreCase("correlationId") => value }
      .fold("not-provided")(identity)

    emailConnector
      .postEmail(email)
      .flatMap {
        case HttpResponse(ACCEPTED, _, _) => Future.unit
        case HttpResponse(status, _, _)   => Future.failed(EmailRejected(status, emailType, correlationId))
      }
  }

  private def sendEmailBestEffort(email: Email, emailType: String)(using HeaderCarrier): Future[Unit] = {
    val correlationId = summon[HeaderCarrier].extraHeaders
      .collectFirst { case (name, value) if name.equalsIgnoreCase("correlationId") => value }
      .fold("not-provided")(identity)

    sendEmail(email, emailType).recover {
      case EmailRejected(BAD_REQUEST, _, _) =>
        logger.warn(s"Error from HMRC email service: status=400 [CorrelationId=$correlationId]")
      case EmailRejected(status, _, _) =>
        logger.warn(s"Unexpected response from HMRC email service: status=$status [CorrelationId=$correlationId]")
      case NonFatal(e) =>
        logger.warn(
          s"Unable to send ${emailType} confirmation email: ${e.getClass.getSimpleName} [CorrelationId=$correlationId]"
        )
    }
  }

  private def notificationEmails(
      contacts: List[Contact],
      companyName: String,
      referenceId: String
  ): List[NotificationEmail] = {
    val datetime = LocalDateTime.now().format(dateFormatter)

    contacts.map(contact =>
      NotificationEmail(
        List(contact.email),
        templateId = EmailTemplate.NotificationConfirmation,
        parameters = NotificationEmailParameters(
          recipientName = contact.name,
          companyName = companyName,
          submittedDateTime = datetime,
          referenceId = referenceId
        )
      )
    )
  }

  def sendNotificationEmail(
      contacts: List[Contact],
      companyName: String,
      referenceId: String
  )(using HeaderCarrier): Future[Unit] = {
    Future
      .traverse(notificationEmails(contacts, companyName, referenceId))(email => sendEmail(email, "notification"))
      .map(_ => ())
  }

  def sendNotificationEmailBestEffort(
      contacts: List[Contact],
      companyName: String,
      referenceId: String
  )(using HeaderCarrier): Future[Unit] = {
    Future
      .traverse(notificationEmails(contacts, companyName, referenceId))(email =>
        sendEmailBestEffort(email, "notification")
      )
      .map(_ => ())
  }

  def sendSubmitterCertificateEmail(
      email: String,
      recipientName: String,
      companyName: String,
      referenceId: String,
      submitterName: String,
      saoName: String
  )(using HeaderCarrier): Future[Unit] = {
    val datetime        = LocalDateTime.now().format(dateFormatter)
    val emailParameters = SubmitterCertificateEmailParameters(
      recipientName = recipientName,
      companyName = companyName,
      submittedDateTime = datetime,
      referenceId = referenceId,
      submitterName = Some(submitterName),
      saoName = saoName
    )
    val emailModel = SubmitterCertificateEmail(List(email), parameters = emailParameters)
    sendEmailBestEffort(emailModel, "certificate")
  }

  def sendSaoCertificateEmail(
      email: String,
      recipientName: String,
      companyName: String,
      referenceId: String,
      saoName: String
  )(using HeaderCarrier): Future[Unit] = {
    val datetime        = LocalDateTime.now().format(dateFormatter)
    val emailParameters = SaoCertificateEmailParameters(
      recipientName = recipientName,
      companyName = companyName,
      submittedDateTime = datetime,
      referenceId = referenceId,
      saoName = saoName
    )
    val emailModel = SaoCertificateEmail(List(email), parameters = emailParameters)
    sendEmailBestEffort(emailModel, "certificate")
  }

}

object EmailService {
  final case class EmailRejected(status: Int, emailType: String, correlationId: String)
      extends RuntimeException(s"Email service returned $status for $emailType [CorrelationId=$correlationId]") {
    val retriable: Boolean = status >= 500
  }
}
