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

import org.apache.pekko.NotUsed
import org.apache.pekko.stream.Materializer
import org.apache.pekko.stream.scaladsl.{Sink, Source}
import org.apache.pekko.util.ByteString
import play.api.http.Status.ACCEPTED
import play.api.libs.json.*
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse, UpstreamErrorResponse}
import uk.gov.hmrc.objectstore.client.play.Implicits.*
import uk.gov.hmrc.objectstore.client.play.PlayObjectStoreClient
import uk.gov.hmrc.objectstore.client.{Path, RetentionPeriod}
import uk.gov.hmrc.senioraccountingofficer.connectors.*
import uk.gov.hmrc.senioraccountingofficer.models.*
import uk.gov.hmrc.senioraccountingofficer.models.crmm.{RetrieveCustomerRequest, RetrieveCustomerResponse}
import uk.gov.hmrc.senioraccountingofficer.models.documentum.{DocumentumPackageContext, SubmissionType}
import uk.gov.hmrc.senioraccountingofficer.models.dps.*
import uk.gov.hmrc.senioraccountingofficer.models.submission.*
import uk.gov.hmrc.senioraccountingofficer.models.submission.SubmissionKind.*
import uk.gov.hmrc.senioraccountingofficer.models.submission.SubmissionStep.*
import uk.gov.hmrc.senioraccountingofficer.services.PdfService
import uk.gov.hmrc.senioraccountingofficer.services.documentum.DocumentumPackageService

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

import java.time.format.DateTimeFormatter
import java.time.{Clock, LocalDateTime, ZoneId}
import java.util.Locale
import javax.inject.Inject

class SubmissionOperations @Inject() (
    subscriptions: GetSubscriptionConnector,
    customers: CrmmConnector,
    notifications: NotificationConnector,
    certificates: CertificateConnector,
    emails: EmailConnector,
    sdes: SdesConnector,
    pdf: PdfService,
    objectStore: PlayObjectStoreClient,
    packages: DocumentumPackageService,
    clock: Clock
)(using ExecutionContext, Materializer) {

  private val owner = DocumentumPackageService.owner

  def execute(command: SubmissionCommand)(using HeaderCarrier): Future[SubmissionCommand] = {
    val data      = command.data
    val operation = Try {
      command.step match {
        case RetrieveSubscription =>
          subscriptions.getSubscription(data.subscriptionId).map { response =>
            command.copy(data = data.copy(subscription = Some(decode[GetSubscriptionDpsResponse](response, 200))))
          }
        case RetrieveCustomer =>
          val company = required(data.subscription, "subscription").nominatedCompany
          customers.retrieveCustomer(RetrieveCustomerRequest(company.crn, Some(company.utr))).map { response =>
            val customer = decode[RetrieveCustomerResponse](response, 200)
            command.copy(data = data.copy(customerNumber = customer.customerId))
          }
        case SubmitDps  => submitDps(command)
        case InitialPdf =>
          uploadPdf(data).map(_ => command.copy(data = data.copy(pdfAttempted = true, pdfStored = true)))
        case SendEmail =>
          emails.postEmail(emailModel(command)).map { response =>
            if response.status != 202 then throw SubmissionFailures.response(response.status)
            command
          }
        case EnsurePdf =>
          storedPdf(data).map { source =>
            source.runWith(Sink.cancelled)
            command
          }
        case PackageDocumentum => packageDocument(command)
        case NotifySdes        =>
          val prepared = required(data.prepared, "prepared")
          sdes
            .notifyFileReady(
              prepared.fileName,
              prepared.owner,
              prepared.objectStorePath,
              prepared.checksum,
              prepared.contentLength
            )
            .map { response =>
              if response.status != ACCEPTED then throw SubmissionFailures.response(response.status)
              command
            }
      }
    }
    Future.fromTry(operation).flatten.map(_.copy(operationFinished = true)).recoverWith {
      case e: UpstreamErrorResponse =>
        Future.failed(SubmissionFailures.response(e.statusCode, command.step == SubmitDps))
    }
  }

  private def required[A](value: Option[A], field: String): A =
    value.getOrElse(throw new IllegalStateException(s"Missing required submission field: $field"))

  private def submitDps(command: SubmissionCommand)(using HeaderCarrier): Future[SubmissionCommand] = {
    val data = command.data
    data.kind match {
      case Notification =>
        notifications
          .postNotification(
            data.subscriptionId,
            required(data.notification, "notification").toNotificationDpsRequest(data.customerNumber)
          )
          .map(r => withReference(command, decode[NotificationDpsResponse](r, 201, true).notificationRef))
      case Certificate =>
        certificates
          .postCertificate(
            data.subscriptionId,
            required(data.certificate, "certificate").toCertificateDpsRequest(data.customerNumber)
          )
          .map(r => withReference(command, decode[CertificateDpsResponse](r, 201, true).certificateRef))
    }
  }

  private def packageDocument(command: SubmissionCommand)(using HeaderCarrier): Future[SubmissionCommand] = {
    val data        = command.data
    val reference   = required(data.reference, "reference")
    val company     = required(data.subscription, "subscription").nominatedCompany
    val submittedAt = required(data.submittedAt, "submittedAt")
      .atZone(ZoneId.of("Europe/London"))
      .withZoneSameInstant(ZoneId.of("UTC"))
      .toLocalDateTime
    val context = data.kind match {
      case Notification =>
        DocumentumPackageContext.notification(
          reference,
          data.customerNumber,
          data.subscriptionId,
          company,
          required(data.notification, "notification")
        )
      case Certificate =>
        DocumentumPackageContext.certificate(
          reference,
          data.subscriptionId,
          company,
          data.customerNumber
        )
    }
    storedPdf(data).flatMap { source =>
      packages
        .preparePackage(
          context,
          source,
          submittedAt
        )
        .map(prepared => command.copy(data = data.copy(prepared = Some(prepared))))
    }
  }

  def successors(command: SubmissionCommand): Seq[SubmissionCommand] =
    if command.failure.isDefined then Seq.empty
    else
      command.step match {
        case RetrieveSubscription => Seq(command.next(RetrieveCustomer))
        case RetrieveCustomer     => Seq(command.next(SubmitDps))
        case SubmitDps            => Seq(command.next(InitialPdf))
        case InitialPdf           =>
          command.next(EnsurePdf) +: recipients(command.data).map(email => command.next(SendEmail, Some(email)))
        case EnsurePdf              => Seq(command.next(PackageDocumentum))
        case PackageDocumentum      => Seq(command.next(NotifySdes))
        case SendEmail | NotifySdes => Seq.empty
      }

  private def decode[A: Reads](response: HttpResponse, expected: Int, dpsPost: Boolean = false): A = {
    if response.status != expected then throw SubmissionFailures.response(response.status, dpsPost)
    Try(Json.parse(response.body).as[A]).getOrElse(throw SubmissionFailures.malformed(dpsPost))
  }

  private def withReference(command: SubmissionCommand, reference: String): SubmissionCommand = command.copy(data =
    command.data.copy(
      reference = Some(reference),
      submittedAt = Some(LocalDateTime.now(clock.withZone(ZoneId.of("Europe/London"))))
    )
  )

  private def pdfPath(data: SubmissionData): Path.File = DocumentumPackageService.stagedPdfObjectStorePath(
    data.subscriptionId,
    required(data.reference, "reference"),
    if data.kind == Notification then SubmissionType.Notification else SubmissionType.Certificate
  )

  private def uploadPdf(data: SubmissionData)(using HeaderCarrier): Future[Unit] = {
    val source = data.kind match {
      case Notification =>
        pdf.generateNotificationPdf(
          NotificationDpsRequest.toPdfNotification(
            data.subscriptionId,
            required(data.subscription, "subscription"),
            required(data.reference, "reference"),
            required(data.submittedAt, "submittedAt"),
            required(data.notification, "notification")
          )
        )
      case Certificate =>
        pdf.generateCertificatePdf(
          required(data.certificate, "certificate").toPdfCertificate(
            data.subscriptionId,
            required(data.subscription, "subscription"),
            required(data.reference, "reference"),
            required(data.submittedAt, "submittedAt")
          )
        )
    }
    objectStore
      .putObject(
        path = pdfPath(data),
        content = source,
        retentionPeriod = RetentionPeriod.OneWeek,
        contentType = Some("application/pdf"),
        owner = owner
      )
      .map(_ => ())
  }

  private def storedPdf(data: SubmissionData)(using HeaderCarrier): Future[Source[ByteString, NotUsed]] =
    objectStore.getObject[Source[ByteString, NotUsed]](path = pdfPath(data), owner = owner).flatMap {
      case Some(value) => Future.successful(value.content)
      case None        =>
        for {
          _      <- uploadPdf(data)
          stored <- objectStore.getObject[Source[ByteString, NotUsed]](path = pdfPath(data), owner = owner)
        } yield stored.getOrElse(throw new IllegalStateException("Uploaded PDF is missing")).content
    }

  private def recipients(data: SubmissionData): Seq[SubmissionEmail] = data.kind match {
    case Notification =>
      required(data.subscription, "subscription").contacts
        .map(c => SubmissionEmail(c.name, c.email, "notification"))
        .distinctBy(_.address)
    case Certificate =>
      val request = required(data.certificate, "certificate").toCertificateDpsRequest(data.customerNumber)
      val people  = ((request.saoName, request.saoEmail) :: required(data.subscription, "subscription").contacts.map(
        contact => (contact.name, contact.email)
      ))
        .distinctBy(_._2)
      people.map { (name, email) =>
        val template =
          if request.submitterName.isDefined then "submitter"
          else if email == request.saoEmail then "sao"
          else "contact"
        SubmissionEmail(name, email, template)
      }
  }

  private def emailModel(command: SubmissionCommand): Email = {
    val data      = command.data
    val recipient = required(command.email, "email")
    val company   = required(data.subscription, "subscription").nominatedCompany.name
    val reference = required(data.reference, "reference")
    val timestamp = required(data.submittedAt, "submittedAt").format(
      DateTimeFormatter.ofPattern("d MMMM yyyy 'at' hh:mma", Locale.ENGLISH)
    )
    recipient.template match {
      case "notification" =>
        NotificationEmail(
          List(recipient.address),
          EmailTemplate.NotificationConfirmation,
          NotificationEmailParameters(recipient.recipientName, company, timestamp, reference)
        )
      case "submitter" =>
        val request = required(data.certificate, "certificate").toCertificateDpsRequest(data.customerNumber)
        SubmitterCertificateEmail(
          List(recipient.address),
          parameters = SubmitterCertificateEmailParameters(
            recipient.recipientName,
            company,
            request.submitterName,
            request.saoName,
            timestamp,
            reference
          )
        )
      case template =>
        val request = required(data.certificate, "certificate").toCertificateDpsRequest(data.customerNumber)
        SaoCertificateEmail(
          List(recipient.address),
          if template == "sao" then EmailTemplate.CertificateConfirmationSAO
          else EmailTemplate.CertificateConfirmationSAOToContacts,
          SaoCertificateEmailParameters(recipient.recipientName, company, request.saoName, timestamp, reference)
        )
    }
  }
}
