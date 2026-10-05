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
import uk.gov.hmrc.objectstore.client.{Object as _, *}
import uk.gov.hmrc.senioraccountingofficer.connectors.SdesConnector
import uk.gov.hmrc.senioraccountingofficer.models.documentum.{
  DocumentumPackageContext,
  PreparedSdesSubmission,
  SubmissionType
}
import uk.gov.hmrc.senioraccountingofficer.services.documentum.DocumentumPackageService.owner
import uk.gov.hmrc.senioraccountingofficer.utils.SubscriptionIdHash

import scala.concurrent.{ExecutionContext, Future}

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

  def preparePackage(
      context: DocumentumPackageContext,
      pdfSource: Source[ByteString, ?],
      submissionDateTime: LocalDateTime = LocalDateTime.now(ZoneOffset.UTC)
  )(using HeaderCarrier): Future[PreparedSdesSubmission] = {
    val submissionDate       = submissionDateTime.toLocalDate
    val documentBaseFileName = documentBaseFileNameFor(context, submissionDate)
    val pdfFileName          = s"$documentBaseFileName.pdf"
    val metadataXmlName      =
      s"$documentBaseFileName-${submissionDate.format(DateTimeFormatter.BASIC_ISO_DATE)}-metadata.xml"
    val zipFileName      = s"$documentBaseFileName.zip"
    val zipPath          = zipObjectStorePath(context.submissionId, zipFileName)
    val reconciliationId =
      s"$documentBaseFileName-${submissionDateTime.format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))}"
    val metadataXml = metadataXmlGenerator.generate(context, documentBaseFileName, reconciliationId)

    val zipSource = zipBuilder.build(pdfSource, pdfFileName, metadataXml, metadataXmlName)

    for {
      zipSummary <- uploadZip(zipPath, zipSource)
    } yield PreparedSdesSubmission(
      submissionId = context.submissionId,
      fileName = zipFileName,
      owner = owner,
      objectStorePath = zipPath.asUri,
      checksum = zipSummary.contentMd5.value,
      contentLength = zipSummary.contentLength
    )
  }

  def notifySdes(
      preparedSubmission: PreparedSdesSubmission
  )(using HeaderCarrier): Future[Unit] = {
    sdesConnector
      .notifyFileReady(
        preparedSubmission.fileName,
        preparedSubmission.owner,
        preparedSubmission.objectStorePath,
        preparedSubmission.checksum,
        preparedSubmission.contentLength
      )
      .map { response =>
        if response.status < 200 || response.status >= 300 then
          throw new IllegalStateException(
            s"Unexpected SDES status ${response.status} for ${preparedSubmission.submissionId}"
          )
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

  private def zipObjectStorePath(submissionId: String, fileName: String): Path.File =
    Path.Directory(s"/sdes/$submissionId/").file(fileName)

}

object DocumentumPackageService {
  val owner = "senior-accounting-officer"

  def stagedPdfObjectStorePath(
      subscriptionId: String,
      submissionId: String,
      submissionType: SubmissionType
  ): Path.File =
    Path
      .Directory(s"/senior-accounting-officer/${SubscriptionIdHash.hex(subscriptionId)}/")
      .file(s"${submissionId}_SAO_${submissionType.documentumName}.pdf")

}
