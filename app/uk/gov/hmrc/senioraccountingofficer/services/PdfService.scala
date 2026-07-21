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

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.scaladsl.{Source, StreamConverters}
import org.apache.pekko.util.ByteString
import play.api.Logger
import uk.gov.hmrc.senioraccountingofficer.services.PdfService.*
import uk.gov.hmrc.senioraccountingofficer.utils.OpenHtmlToPdfService
import uk.gov.hmrc.senioraccountingofficer.views.html.{CertificatePdfView, NotificationPdfView}

import scala.collection.mutable.ListBuffer
import scala.concurrent.{ExecutionContext, Future, blocking}

import java.io.*
import javax.inject.{Inject, Singleton}

@Singleton
class PdfService @Inject() (
    openHtmlToPdfService: OpenHtmlToPdfService,
    notificationPdfTemplate: NotificationPdfView,
    certificatePdfTemplate: CertificatePdfView
)(implicit val ec: ExecutionContext, actorSystem: ActorSystem) {

  def generateNotificationPdf(notification: Notification): Unit = {
    val html = notificationPdfTemplate(notification).toString
    openHtmlToPdfService.builderFor(html).asSource
  }

  def generateCertificatePdf(certificate: Certificate): Unit = {
    val html = certificatePdfTemplate(certificate).toString
    openHtmlToPdfService.builderFor(html).asSource
  }

}

object PdfService {

  final case class Certificate(
      saoName: String,
      saoEmail: String,
      submitterName: String,
      submissionDate: String,
      submissionId: String,
      companies: Seq[Certificate.Row],
      additionalInformation: Option[String] = None
  )

  object Certificate {
    final case class Row(
        companyName: String,
        utr: String,
        crn: String,
        companyType: "Plc" | "Ltd",
        status: "Active" | "Dormant" | "Administration" | "Liquidation",
        financialYearEndDate: String,
        qualifiedRegimes: TaxRegimes = TaxRegimes(),
        additionalInformation: Option[String] = None
    )
    object Row {
      extension (row: Row) {
        def qualifiedRegimesAsText: String = {
          val regimes = row.qualifiedRegimes
          val builder = ListBuffer[String]()

          if regimes.corporationTax then builder.append("Corporation Tax")
          if regimes.vat then builder.append("VAT")
          if regimes.paye then builder.append("PAYE")
          if regimes.insurancePremiumTax then builder.append("Insurance Premium Tax")
          if regimes.stampDutyLandTax then builder.append("Stamp Duty Land Tax")
          if regimes.stampDutyReserveTax then builder.append("Stamp Duty Reserve Tax")
          if regimes.petroleumRevenueTax then builder.append("Petroleum Revenue Tax")
          if regimes.customsDuties then builder.append("Customs Duties")
          if regimes.exciseDuties then builder.append("Excise Duties")
          if regimes.bankLevy then builder.append("Bank Levy")
          builder.mkString(", ")
        }

        def toNotificationRow(
            index: Int
        ): Notification.Row = Notification.Row(
          row.companyName.replace("$index", index.toString),
          row.utr,
          row.crn,
          row.companyType,
          row.status,
          row.financialYearEndDate
        )
      }
    }

    extension (cert: Certificate) {
      def qualified: Seq[Certificate.Row] =
        cert.companies.filter(_.qualifiedRegimes.isQualified)

      def unqualified: Seq[Certificate.Row] =
        cert.companies.filterNot(_.qualifiedRegimes.isQualified)
    }
  }

  final case class SaoTenure(name: String, startDate: Option[String] = None, endDate: Option[String] = None)

  final case class Notification(
      companyName: String,
      financialYearEndDate: String,
      submissionDate: String,
      submissionId: String,
      saoHistory: Seq[SaoTenure],
      companies: Seq[Notification.Row],
      additionalInformation: Option[String] = None
  )

  object Notification {
    final case class Row(
        companyName: String,
        utr: String,
        crn: String,
        companyType: "Plc" | "Ltd",
        status: "Active" | "Dormant" | "Administration" | "Liquidation",
        financialYearEndDate: String
    )

    extension (row: Row) {
      def toCertificateRow(
          index: Int,
          qualifiedRegimes: TaxRegimes = TaxRegimes(),
          additionalInformation: Option[String] = None
      ): Certificate.Row = Certificate.Row(
        row.companyName.replace("$index", index.toString),
        row.utr,
        row.crn,
        row.companyType,
        row.status,
        row.financialYearEndDate,
        qualifiedRegimes,
        additionalInformation
      )
    }
  }

  final case class TaxRegimes(
      corporationTax: Boolean = false,
      vat: Boolean = false,
      paye: Boolean = false,
      insurancePremiumTax: Boolean = false,
      stampDutyLandTax: Boolean = false,
      stampDutyReserveTax: Boolean = false,
      petroleumRevenueTax: Boolean = false,
      customsDuties: Boolean = false,
      exciseDuties: Boolean = false,
      bankLevy: Boolean = false
  )

  object TaxRegimes {
    extension (regimes: TaxRegimes) {
      def isQualified: Boolean = regimes.productIterator.exists { case b: Boolean =>
        b
      }
    }
  }

  val logger: Logger = Logger(PdfService.getClass)

  extension (builder: PdfRendererBuilder) {
    def asSource(using system: ActorSystem): Source[ByteString, ?] = StreamConverters
      .fromInputStream(() => {
        given blockingEc: ExecutionContext =
          system.dispatchers.lookup("pekko.stream.materializer.blocking-io-dispatcher")

        val pos = new PipedOutputStream

        // Set the output stream
        builder.toStream(pos)

        Future {
          blocking {
            // Run the conversion
            builder.run()
          }
        }.onComplete { _ =>
          pos.close()
        }
        new PipedInputStream(pos)
      })
  }

}
