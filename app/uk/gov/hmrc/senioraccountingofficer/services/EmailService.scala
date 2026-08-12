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
import uk.gov.hmrc.senioraccountingofficer.models
import uk.gov.hmrc.senioraccountingofficer.models.*
import uk.gov.hmrc.senioraccountingofficer.models.EmailTemplate.{CertificateConfirmationSAO, CertificateConfirmationSubmitter}
import uk.gov.hmrc.senioraccountingofficer.models.dps.{CertificateDpsRequest, CertificateDpsResponse, Contact, GetSubscriptionDpsResponse}

import scala.concurrent.{ExecutionContext, Future}
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


  def sendSAOCertificateEmail(dpsResponse: GetSubscriptionDpsResponse,
                              certificateRequest: CertificateDpsRequest,
                              certificateDpsResponse: CertificateDpsResponse)(using HeaderCarrier): Future[Unit] = {
    val dateTime = LocalDateTime.now().format(dateFormatter)
    val emailDetails = extractSAOEmailDetails(dpsResponse, certificateRequest, certificateDpsResponse)
      val request = Email(
        to = Seq(certificateRequest.saoEmail),
        templateId = CertificateConfirmationSAO,
        parameters = Map(
          "recipientName" -> certificateRequest.saoName,
          "companyName" -> emailDetails.companyName,
          "submittedDateTime" -> dateTime,
          "referenceId" -> emailDetails.referenceId
        )
      )
      emailConnector.postEmail(request).map {
        case HttpResponse(ACCEPTED, _, _) => ()
        case HttpResponse(BAD_REQUEST, _, _) =>
          logger.warn("Error from HMRC email service: status=400")
        case HttpResponse(status, _, _) =>
          logger.warn(s"Unexpected response from HMRC email service: status=$status")
      }

    }



  def sendSubmitterCertificateEmail(dpsResponse: GetSubscriptionDpsResponse,
                              certificateRequest: CertificateDpsRequest,
                              certificateDpsResponse: CertificateDpsResponse)(using HeaderCarrier): Future[Unit] = {
      val dateTime = LocalDateTime.now().format(dateFormatter)
      val emailDetails = extractSubmitterEmailDetails(dpsResponse, certificateRequest, certificateDpsResponse)
      val emailRequests = for contact <- emailDetails.contacts yield {
      val request = Email(
        to = Seq(contact.email),
        templateId = CertificateConfirmationSubmitter,
        parameters = Map(
          "recipientName" -> contact.name,
          "submitterName" -> certificateRequest.submitterName.getOrElse(""),
          "saoName" -> certificateRequest.saoName,
          "submittedDateTime" -> dateTime,
          "referenceId" ->  certificateDpsResponse.certificateRef
        )
      )
      emailConnector.postEmail(request).map {
        case HttpResponse(ACCEPTED, _, _) => ()
        case HttpResponse(BAD_REQUEST, _, _) =>
          logger.warn("Error from HMRC email service: status=400")
        case HttpResponse(status, _, _) =>
          logger.warn(s"Unexpected response from HMRC email service: status=$status")
      }

    }
      Future.sequence(emailRequests).map(_ => ())
}


  private def extractSubmitterEmailDetails(
                                      dpsResponse: GetSubscriptionDpsResponse,
                                      certificateRequest: CertificateDpsRequest,
                                      certificateDpsResponse: CertificateDpsResponse
                                    ): EmailDetails = {
    EmailDetails(
      contacts = dpsResponse.contacts,
      companyName = dpsResponse.nominatedCompany.name,
      referenceId = certificateDpsResponse.certificateRef
    )
  }

  private def extractSAOEmailDetails(
                                   dpsResponse: GetSubscriptionDpsResponse,
                                   certificateRequest: CertificateDpsRequest,
                                   certificateDpsResponse: CertificateDpsResponse
                                 ): EmailDetails = {
    EmailDetails(
      contacts = dpsResponse.contacts,
      companyName = dpsResponse.nominatedCompany.name,
      referenceId = certificateDpsResponse.certificateRef
    )
  }

  private final case class EmailDetails(
                                            contacts: List[Contact],
                                            companyName: String,
                                            referenceId: String
                                          )



}