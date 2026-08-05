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
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.Materializer
import org.apache.pekko.stream.scaladsl.Source
import org.apache.pekko.util.ByteString
import org.mockito.ArgumentMatchers.{any, eq as meq, isNull}
import org.mockito.Mockito.{verify, when}
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.mockito.MockitoSugar
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}
import uk.gov.hmrc.objectstore.client.play.PlayObjectStoreClient
import uk.gov.hmrc.objectstore.client.{Object as ObjectStoreObject, *}
import uk.gov.hmrc.senioraccountingofficer.connectors.SdesConnector
import uk.gov.hmrc.senioraccountingofficer.models.documentum.{
  DocumentumCompany,
  DocumentumPackageContext,
  SubmissionType
}

import scala.concurrent.{ExecutionContext, Future}

import java.time.format.DateTimeFormatter
import java.time.{Instant, LocalDate, ZoneOffset}

class DocumentumPackageServiceSpec
    extends AnyWordSpec
    with Matchers
    with MockitoSugar
    with ScalaFutures
    with BeforeAndAfterAll {

  private given ActorSystem      = ActorSystem("DocumentumPackageServiceSpec")
  private given Materializer     = Materializer(summon[ActorSystem])
  private given ExecutionContext = summon[ActorSystem].dispatcher
  private given HeaderCarrier    = HeaderCarrier()

  private val objectStoreClient = mock[PlayObjectStoreClient]
  private val sdesConnector     = mock[SdesConnector]
  private val service           = new DocumentumPackageService(
    metadataXmlGenerator = new DocumentumMetadataXmlGenerator(),
    zipBuilder = new DocumentumZipBuilder(),
    objectStoreClient = objectStoreClient,
    sdesConnector = sdesConnector
  )

  override def afterAll(): Unit = {
    summon[ActorSystem].terminate()
    super.afterAll()
  }

  "packageAndSubmit" must {
    "upload the zip to object store with one week retention and notify SDES" in {
      when(
        objectStoreClient.putObject(
          path = meq(expectedPdfPath),
          content = meq(generatedPdfSource),
          retentionPeriod = meq(RetentionPeriod.OneWeek),
          contentType = meq(Some("application/pdf")),
          contentMd5 = isNull,
          owner = meq(owner)
        )(using any(), any())
      ).thenReturn(
        Future.successful(
          ObjectSummaryWithMd5(expectedPdfPath, 1, Md5Hash("hash"), Instant.parse("2026-07-28T12:00:00Z"))
        )
      )
      when(
        objectStoreClient.getObject[Source[ByteString, NotUsed]](
          path = meq(expectedPdfPath),
          owner = meq(owner)
        )(using any(), any())
      ).thenReturn(Future.successful(Some(stagedPdfObject)))
      when(
        objectStoreClient.putObject(
          path = meq(expectedZipPath),
          content = any[Source[ByteString, NotUsed]](),
          retentionPeriod = meq(RetentionPeriod.OneWeek),
          contentType = meq(Some("application/zip")),
          contentMd5 = isNull,
          owner = meq(owner)
        )(using any(), any())
      ).thenReturn(
        Future.successful(
          ObjectSummaryWithMd5(expectedZipPath, 100, Md5Hash("zip-hash"), Instant.parse("2026-07-28T12:00:00Z"))
        )
      )
      when(
        sdesConnector.notifyFileReady(
          meq(expectedFileName),
          meq(owner),
          meq(expectedZipPath.asUri),
          meq("zip-hash"),
          meq(100L)
        )(using any())
      )
        .thenReturn(Future.successful(HttpResponse(204)))

      val result = service.packageAndSubmit(context, generatedPdfSource).futureValue

      result.packageAvailable mustBe true
      result.fileName mustBe Some(expectedFileName)
      verify(sdesConnector).notifyFileReady(
        meq(expectedFileName),
        meq(owner),
        meq(expectedZipPath.asUri),
        meq("zip-hash"),
        meq(100L)
      )(using any())
    }

    "return unavailable when SDES rejects the notification" in {
      when(
        objectStoreClient.putObject(
          path = meq(expectedPdfPath),
          content = meq(generatedPdfSource),
          retentionPeriod = meq(RetentionPeriod.OneWeek),
          contentType = meq(Some("application/pdf")),
          contentMd5 = isNull,
          owner = meq(owner)
        )(using any(), any())
      ).thenReturn(
        Future.successful(
          ObjectSummaryWithMd5(expectedPdfPath, 1, Md5Hash("hash"), Instant.parse("2026-07-28T12:00:00Z"))
        )
      )
      when(
        objectStoreClient.getObject[Source[ByteString, NotUsed]](
          path = meq(expectedPdfPath),
          owner = meq(owner)
        )(using any(), any())
      ).thenReturn(Future.successful(Some(stagedPdfObject)))
      when(
        objectStoreClient.putObject(
          path = meq(expectedZipPath),
          content = any[Source[ByteString, NotUsed]](),
          retentionPeriod = meq(RetentionPeriod.OneWeek),
          contentType = meq(Some("application/zip")),
          contentMd5 = isNull,
          owner = meq(owner)
        )(using any(), any())
      ).thenReturn(
        Future.successful(
          ObjectSummaryWithMd5(expectedZipPath, 100, Md5Hash("zip-hash"), Instant.parse("2026-07-28T12:00:00Z"))
        )
      )
      when(
        sdesConnector.notifyFileReady(
          meq(expectedFileName),
          meq(owner),
          meq(expectedZipPath.asUri),
          meq("zip-hash"),
          meq(100L)
        )(using any())
      )
        .thenReturn(Future.successful(HttpResponse(500)))

      val result = service.packageAndSubmit(context, generatedPdfSource).futureValue

      result.packageAvailable mustBe false
      result.fileName mustBe None
    }
  }

  private val owner            = "senior-accounting-officer"
  private val expectedDate     = LocalDate.now(ZoneOffset.UTC).format(DateTimeFormatter.BASIC_ISO_DATE)
  private val expectedFileName = s"${expectedDate}_NOT0123456789_SAO_Notification_OFFICIAL_SENSITIVE.zip"
  private val expectedZipPath  = Path.Directory("/sdes/NOT0123456789/").file(expectedFileName)
  private val expectedPdfPath  = Path
    .Directory("/senior-accounting-officer/NOT0123456789/")
    .file("NOT0123456789_SAO_Notification.pdf")
  private val generatedPdfSource = Source.single(ByteString("generated-pdf"))
  private val stagedPdfObject    = ObjectStoreObject(
    expectedPdfPath,
    Source.single(ByteString("retrieved-pdf")),
    ObjectMetadata("application/pdf", 13, Md5Hash("hash"), Instant.parse("2026-07-28T12:00:00Z"), Map.empty)
  )
  private val context =
    DocumentumPackageContext(
      submissionId = "NOT0123456789",
      submissionType = SubmissionType.Notification,
      saoSubscriptionId = "XASAO1234567890",
      customerId = None,
      companies = List(DocumentumCompany(utr = "1234567890", name = "Test Ltd", crn = Some("AB123456")))
    )
}
