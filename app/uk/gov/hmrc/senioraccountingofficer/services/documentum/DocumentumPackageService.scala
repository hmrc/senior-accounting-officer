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
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
import uk.gov.hmrc.senioraccountingofficer.connectors.SdesConnector
import uk.gov.hmrc.senioraccountingofficer.models.documentum.{DocumentumPackageContext, DocumentumPackageResult}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

import java.time.format.DateTimeFormatter
import java.time.{LocalDate, LocalDateTime, ZoneOffset}
import javax.inject.Inject

class DocumentumPackageService @Inject() (
    metadataXmlGenerator: DocumentumMetadataXmlGenerator,
    zipBuilder: DocumentumZipBuilder,
    objectStoreClient: PlayObjectStoreClient,
    sdesConnector: SdesConnector,
    servicesConfig: ServicesConfig
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

    (for {
      _          <- uploadStagedPdf(stagedPdfPath, pdfSource)
      stagedPdf  <- getStagedPdf(stagedPdfPath)
      zipSource  <- zipBuilder.build(stagedPdf.content, pdfFileName, metadataXml, metadataXmlName)
      zipSummary <- uploadZip(zipPath, zipSource)
      response   <- sdesConnector.notifyFileReady(
        zipFileName,
        owner,
        zipPath.asUri,
        zipSummary.contentMd5.value,
        zipSummary.contentLength
      )
    } yield {
      if response.status >= 200 && response.status < 300 then
        DocumentumPackageResult(packageAvailable = true, Some(zipFileName))
      else {
        logger.warn(s"[DocumentumPackage][SDES][UnexpectedStatus] status=${response.status}")
        DocumentumPackageResult(packageAvailable = false)
      }
    }).recover { case NonFatal(exception) =>
      logger.warn(s"[DocumentumPackage][Failed] submissionId=${context.submissionId}", exception)
      DocumentumPackageResult(packageAvailable = false)
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
      retentionPeriod = retentionPeriod,
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
      retentionPeriod = retentionPeriod,
      contentType = Some("application/zip"),
      owner = owner
    )

  private def documentBaseFileNameFor(context: DocumentumPackageContext, submissionDate: LocalDate): String =
    s"${submissionDate.format(DateTimeFormatter.BASIC_ISO_DATE)}_${context.submissionId}_SAO_${context.submissionType.documentumName}_OFFICIAL_SENSITIVE"

  private def stagedPdfObjectStorePath(context: DocumentumPackageContext): Path.File =
    Path
      .Directory(s"/senior-accounting-officer/${context.submissionId}/")
      .file(s"${context.submissionId}_SAO_${context.submissionType.documentumName}.pdf")

  private def zipObjectStorePath(submissionId: String, fileName: String): Path.File =
    Path.Directory(s"/sdes/$submissionId/").file(fileName)

  private def retentionPeriod: RetentionPeriod =
    servicesConfig.getString("object-store.default-retention-period") match {
      case "1-day"     => RetentionPeriod.OneDay
      case "1-week"    => RetentionPeriod.OneWeek
      case unsupported =>
        throw new IllegalArgumentException(
          s"Unsupported object-store.default-retention-period: $unsupported"
        )
    }

  private val owner = "senior-accounting-officer"
}
