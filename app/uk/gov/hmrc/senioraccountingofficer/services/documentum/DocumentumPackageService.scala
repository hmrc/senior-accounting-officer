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

package uk.gov.hmrc.senioraccountingofficer.services.documentum

import org.apache.pekko.NotUsed
import org.apache.pekko.stream.Materializer
import org.apache.pekko.stream.scaladsl.Source
import org.apache.pekko.util.ByteString
import play.api.Logging
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.objectstore.client.play.Implicits.*
import uk.gov.hmrc.objectstore.client.play.PlayObjectStoreClient
import uk.gov.hmrc.objectstore.client.{Object as ObjectStoreObject, *}
import uk.gov.hmrc.senioraccountingofficer.connectors.SdesConnector
import uk.gov.hmrc.senioraccountingofficer.models.documentum.{DocumentumPackageContext, DocumentumPackageResult}
import uk.gov.hmrc.senioraccountingofficer.utils.SubscriptionIdHash

import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

import java.time.format.DateTimeFormatter
import java.time.{LocalDate, LocalDateTime, ZoneOffset}
import javax.inject.Inject

class DocumentumPackageService @Inject() (
    metadataXmlGenerator: DocumentumMetadataXmlGenerator,
    zipBuilder: DocumentumZipBuilder,
    objectStoreClient: PlayObjectStoreClient,
    sdesConnector: SdesConnector
)(using ExecutionContext, Materializer)
    extends Logging {

  def packageAndSubmit(
      context: DocumentumPackageContext,
      pdfSource: Source[ByteString, ?]
  )(using HeaderCarrier): Future[DocumentumPackageResult] = {
    val submissionDateTime   = LocalDateTime.now(ZoneOffset.UTC)
    val submissionDate       = submissionDateTime.toLocalDate
    val documentBaseFileName = documentBaseFileNameFor(context, submissionDate)
    val pdfFileName          = s"$documentBaseFileName.pdf"
    val metadataXmlName      =
      s"$documentBaseFileName-${submissionDate.format(DateTimeFormatter.BASIC_ISO_DATE)}-metadata.xml"
    val zipFileName      = s"$documentBaseFileName.zip"
    val stagedPdfPath    = stagedPdfObjectStorePath(context)
    val zipPath          = zipObjectStorePath(context.submissionId, zipFileName)
    val reconciliationId =
      s"$documentBaseFileName-${submissionDateTime.format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))}"
    val metadataXml = metadataXmlGenerator.generate(context, documentBaseFileName, reconciliationId)

    uploadStagedPdf(stagedPdfPath, pdfSource)
      .map { _ =>
        val _ = submitPackageToSdes(
          stagedPdfPath,
          pdfFileName,
          metadataXml,
          metadataXmlName,
          zipPath,
          zipFileName,
          context.submissionId
        )

        DocumentumPackageResult(packageAvailable = true, Some(zipFileName))
      }
      .recover { case NonFatal(exception) =>
        logger.warn(
          s"[DocumentumPackage][Failed][CorrelationId=$getCorrelationId] submissionId=${context.submissionId}",
          exception
        )
        DocumentumPackageResult(packageAvailable = false)
      }
  }

  private def submitPackageToSdes(
      stagedPdfPath: Path.File,
      pdfFileName: String,
      metadataXml: String,
      metadataXmlName: String,
      zipPath: Path.File,
      zipFileName: String,
      submissionId: String
  )(using HeaderCarrier): Future[Unit] = {
    val stagedPdfSource = Source.lazyFutureSource(() => getStagedPdf(stagedPdfPath).map(_.content))
    val zipSource       = zipBuilder.build(stagedPdfSource, pdfFileName, metadataXml, metadataXmlName)

    (for {
      zipSummary <- uploadZip(zipPath, zipSource)
      response   <- sdesConnector.notifyFileReady(
        zipFileName,
        owner,
        zipPath.asUri,
        zipSummary.contentMd5.value,
        zipSummary.contentLength
      )
    } yield {
      if response.status < 200 || response.status >= 300 then
        logger.warn(
          s"[DocumentumPackage][SDES][UnexpectedStatus][CorrelationId=$getCorrelationId][Status=${response.status}]"
        )
    }).recover { case NonFatal(exception) =>
      logger.warn(s"[DocumentumPackage][Failed][CorrelationId=$getCorrelationId] submissionId=$submissionId", exception)
      ()
    }
  }

  def download(submissionId: String, fileName: String)(using
      HeaderCarrier
  ): Future[Option[Source[ByteString, NotUsed]]] =
    objectStoreClient
      .getObject[Source[ByteString, NotUsed]](
        path = zipObjectStorePath(submissionId, fileName),
        owner = owner
      )
      .map(_.map(_.content))

  private def uploadStagedPdf(path: Path.File, pdfSource: Source[ByteString, ?])(using
      HeaderCarrier
  ): Future[ObjectSummaryWithMd5] =
    objectStoreClient.putObject(
      path = path,
      content = pdfSource,
      retentionPeriod = RetentionPeriod.OneWeek,
      contentType = Some("application/pdf"),
      owner = owner
    )

  private def getStagedPdf(path: Path.File)(using
      HeaderCarrier
  ): Future[ObjectStoreObject[Source[ByteString, NotUsed]]] =
    objectStoreClient
      .getObject[Source[ByteString, NotUsed]](
        path = path,
        owner = owner
      )
      .map(_.getOrElse(throw new IllegalStateException(s"PDF not found in object store: ${path.asUri}")))

  private def uploadZip(path: Path.File, zipSource: Source[ByteString, NotUsed])(using
      HeaderCarrier
  ): Future[ObjectSummaryWithMd5] =
    objectStoreClient.putObject(
      path = path,
      content = zipSource,
      retentionPeriod = RetentionPeriod.OneWeek,
      contentType = Some("application/zip"),
      owner = owner
    )

  private def documentBaseFileNameFor(context: DocumentumPackageContext, submissionDate: LocalDate): String =
    s"${submissionDate.format(DateTimeFormatter.BASIC_ISO_DATE)}_${context.submissionId}_SAO_${context.submissionType.documentumName}_OFFICIAL_SENSITIVE"

  private def stagedPdfObjectStorePath(context: DocumentumPackageContext): Path.File =
    Path
      .Directory(s"/senior-accounting-officer/${SubscriptionIdHash.hex(context.saoSubscriptionId)}/")
      .file(s"${context.submissionId}_SAO_${context.submissionType.documentumName}.pdf")

  private def zipObjectStorePath(submissionId: String, fileName: String): Path.File =
    Path.Directory(s"/sdes/$submissionId/").file(fileName)

  private val owner = "senior-accounting-officer"

  private def getCorrelationId(using hc: HeaderCarrier): String = hc.extraHeaders
    .collectFirst { case ("correlationId", id) => id }
    .getOrElse("Not Set")
}
