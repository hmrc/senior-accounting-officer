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

import org.apache.pekko.NotUsed
import org.apache.pekko.stream.scaladsl.Source
import org.apache.pekko.util.ByteString
import org.mockito.ArgumentMatchers.{any, eq as meq}
import org.mockito.Mockito.*
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.concurrent.ScalaFutures.convertScalaFuture
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.mockito.MockitoSugar.mock
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Application
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.objectstore.client.play.PlayObjectStoreClient
import uk.gov.hmrc.objectstore.client.{Object as ObjectStoreObject, *}
import uk.gov.hmrc.senioraccountingofficer.services.ObjectStoreServiceSpec.*
import uk.gov.hmrc.senioraccountingofficer.utils.SubscriptionIdHash

import scala.concurrent.Future

import java.time.Instant

class ObjectStoreServiceSpec
    extends AnyFreeSpec
    with Matchers
    with MockitoSugar
    with ScalaFutures
    with GuiceOneAppPerSuite
    with BeforeAndAfterEach {

  given HeaderCarrier = HeaderCarrier()

  val mockObjectStoreClient: PlayObjectStoreClient = mock[PlayObjectStoreClient]

  override lazy val app: Application = GuiceApplicationBuilder()
    .overrides(
      bind[PlayObjectStoreClient].toInstance(mockObjectStoreClient)
    )
    .build()

  def SUT: ObjectStoreService = app.injector.instanceOf[ObjectStoreService]

  override def beforeEach(): Unit = {
    reset(mockObjectStoreClient)
  }

  "isNotificationPdfAvailable" - {
    "when connection to object store fails must return false" in {
      when(
        mockObjectStoreClient.listObjects(
          path = any(),
          owner = any()
        )(using
          any()
        )
      )
        .thenReturn(Future.failed(RuntimeException("some exception")))

      val result = SUT.isNotificationPdfAvailable(saoSubscriptionId, notificationReference)

      result.futureValue mustBe false
    }

    "when no objects are found in object store must return false" in {
      when(
        mockObjectStoreClient.listObjects(
          path = any(),
          owner = any()
        )(using
          any()
        )
      )
        .thenReturn(Future.successful(ObjectListing(Nil)))

      val result = SUT.isNotificationPdfAvailable(saoSubscriptionId, notificationReference)

      result.futureValue mustBe false
    }

    "when objects are found in object store without a pdf must return false" in {
      when(
        mockObjectStoreClient.listObjects(
          path = any(),
          owner = any()
        )(using
          any()
        )
      )
        .thenReturn(
          Future.successful(
            ObjectListing(
              List(
                ObjectSummary(
                  Path
                    .File(
                      Path.Directory(s"senior-accounting-officer/$notificationReference"),
                      s"${notificationReference}_SAO_Notification.txt"
                    ),
                  0,
                  Instant.now()
                )
              )
            )
          )
        )

      val result = SUT.isNotificationPdfAvailable(saoSubscriptionId, notificationReference)

      result.futureValue mustBe false
    }

    "when objects are found in object store with a pdf must return true" in {
      when(
        mockObjectStoreClient.listObjects(
          path = any(),
          owner = any()
        )(using
          any()
        )
      )
        .thenReturn(
          Future.successful(
            ObjectListing(
              List(
                ObjectSummary(
                  Path
                    .File(
                      Path.Directory(s"senior-accounting-officer/$notificationReference"),
                      s"${notificationReference}_SAO_Notification.pdf"
                    ),
                  0,
                  Instant.now()
                )
              )
            )
          )
        )

      val result = SUT.isNotificationPdfAvailable(saoSubscriptionId, notificationReference)

      result.futureValue mustBe true
    }
  }

  "isCertificatePdfAvailable" - {
    "when connection to object store fails must return false" in {
      when(
        mockObjectStoreClient.listObjects(
          path = any(),
          owner = any()
        )(using
          any()
        )
      )
        .thenReturn(Future.failed(RuntimeException("some exception")))

      val result = SUT.isCertificatePdfAvailable(saoSubscriptionId, certificateReference)

      result.futureValue mustBe false
    }

    "when no objects are found in object store must return false" in {
      when(
        mockObjectStoreClient.listObjects(
          path = any(),
          owner = any()
        )(using
          any()
        )
      )
        .thenReturn(Future.successful(ObjectListing(Nil)))

      val result = SUT.isCertificatePdfAvailable(saoSubscriptionId, certificateReference)

      result.futureValue mustBe false
    }

    "when objects are found in object store without a pdf must return false" in {
      when(
        mockObjectStoreClient.listObjects(
          path = any(),
          owner = any()
        )(using
          any()
        )
      )
        .thenReturn(
          Future.successful(
            ObjectListing(
              List(
                ObjectSummary(
                  Path
                    .File(
                      Path.Directory(s"senior-accounting-officer/$certificateReference"),
                      s"${certificateReference}_SAO_Certificate.txt"
                    ),
                  0,
                  Instant.now()
                )
              )
            )
          )
        )

      val result = SUT.isCertificatePdfAvailable(saoSubscriptionId, certificateReference)

      result.futureValue mustBe false
    }

    "when objects are found in object store with a pdf must return true" in {
      when(
        mockObjectStoreClient.listObjects(
          path = any(),
          owner = any()
        )(using
          any()
        )
      )
        .thenReturn(
          Future.successful(
            ObjectListing(
              List(
                ObjectSummary(
                  Path
                    .File(
                      Path.Directory(s"senior-accounting-officer/$certificateReference"),
                      s"${certificateReference}_SAO_Certificate.pdf"
                    ),
                  0,
                  Instant.now()
                )
              )
            )
          )
        )

      val result = SUT.isCertificatePdfAvailable(saoSubscriptionId, certificateReference)

      result.futureValue mustBe true
    }
  }

  "downloadDocumentumPackage" - {
    "when connection to object store fails must return Future.succssful(None)" in {
      when(
        mockObjectStoreClient.getObject[Source[ByteString, NotUsed]](
          path = any(),
          owner = any()
        )(using
          any(),
          any()
        )
      ).thenReturn(Future.failed(RuntimeException("some exception")))

      val result = SUT.downloadDocumentumPackage(notificationReference, documentumPackageFileName)

      result.futureValue mustBe None
    }

    "must download the zip from the SDES object-store path" in {
      val expectedPath = Path
        .Directory(s"/senior-accounting-officer/sdes/$notificationReference/")
        .file(documentumPackageFileName)
      val storedObject = ObjectStoreObject(
        expectedPath,
        Source.single(ByteString("zip-content")),
        ObjectMetadata("application/zip", 11, Md5Hash("hash"), Instant.now(), Map.empty)
      )

      when(
        mockObjectStoreClient.getObject[Source[ByteString, NotUsed]](
          path = any(),
          owner = any()
        )(using
          any(),
          any()
        )
      ).thenReturn(Future.successful(Some(storedObject)))

      val result = SUT.downloadDocumentumPackage(notificationReference, documentumPackageFileName)

      result.futureValue mustBe Some(storedObject.content)
      verify(mockObjectStoreClient).getObject[Source[ByteString, NotUsed]](
        path = meq(expectedPath),
        owner = meq(ObjectStoreService.objectStoreOwner)
      )(using any(), any())
    }
  }

  "downloadSubmissionPdf" - {
    "must download the notification pdf from the path the backend stores it at" in {
      val expectedPath = Path
        .Directory(s"/senior-accounting-officer/$hashedSaoSubscriptionId/")
        .file(s"${notificationReference}_SAO_Notification.pdf")
      val storedObject = ObjectStoreObject(
        expectedPath,
        Source.single(ByteString("pdf-content")),
        ObjectMetadata("application/pdf", 11, Md5Hash("hash"), Instant.now(), Map.empty)
      )

      when(
        mockObjectStoreClient.getObject[Source[ByteString, NotUsed]](
          path = any(),
          owner = any()
        )(using
          any(),
          any()
        )
      ).thenReturn(Future.successful(Some(storedObject)))

      val result =
        SUT.downloadSubmissionPdf(saoSubscriptionId, notificationReference, SubmissionDocumentType.Notification)

      result.futureValue mustBe Some(storedObject)
      verify(mockObjectStoreClient).getObject[Source[ByteString, NotUsed]](
        path = meq(expectedPath),
        owner = meq(ObjectStoreService.objectStoreOwner)
      )(using any(), any())
    }

    "must download the certificate pdf from the path the backend stores it at" in {
      val expectedPath = Path
        .Directory(s"/senior-accounting-officer/$hashedSaoSubscriptionId/")
        .file(s"${certificateReference}_SAO_Certificate.pdf")
      val storedObject = ObjectStoreObject(
        expectedPath,
        Source.single(ByteString("pdf-content")),
        ObjectMetadata("application/pdf", 11, Md5Hash("hash"), Instant.now(), Map.empty)
      )

      when(
        mockObjectStoreClient.getObject[Source[ByteString, NotUsed]](
          path = any(),
          owner = any()
        )(using
          any(),
          any()
        )
      ).thenReturn(Future.successful(Some(storedObject)))

      val result =
        SUT.downloadSubmissionPdf(saoSubscriptionId, certificateReference, SubmissionDocumentType.Certificate)

      result.futureValue mustBe Some(storedObject)
      verify(mockObjectStoreClient).getObject[Source[ByteString, NotUsed]](
        path = meq(expectedPath),
        owner = meq(ObjectStoreService.objectStoreOwner)
      )(using any(), any())
    }

    "when the pdf is not in object store must return None" in {
      when(
        mockObjectStoreClient.getObject[Source[ByteString, NotUsed]](
          path = any(),
          owner = any()
        )(using
          any(),
          any()
        )
      ).thenReturn(Future.successful(None))

      val result =
        SUT.downloadSubmissionPdf(saoSubscriptionId, notificationReference, SubmissionDocumentType.Notification)

      result.futureValue mustBe None
    }

    "when connection to object store fails must fail rather than report the pdf as missing" in {
      val expectedException = RuntimeException("some exception")

      when(
        mockObjectStoreClient.getObject[Source[ByteString, NotUsed]](
          path = any(),
          owner = any()
        )(using
          any(),
          any()
        )
      ).thenReturn(Future.failed(expectedException))

      val result =
        SUT.downloadSubmissionPdf(saoSubscriptionId, notificationReference, SubmissionDocumentType.Notification)

      result.failed.futureValue mustBe expectedException
    }

    malformedReferences.foreach { reference =>
      s"must return None without calling object store for the reference '$reference'" in {
        val result = SUT.downloadSubmissionPdf(saoSubscriptionId, reference, SubmissionDocumentType.Notification)

        result.futureValue mustBe None
        verifyNoInteractions(mockObjectStoreClient)
      }
    }
  }

  "isNotificationPdfAvailable" - {
    malformedReferences.foreach { reference =>
      s"must return false without calling object store for the reference '$reference'" in {
        val result = SUT.isNotificationPdfAvailable(saoSubscriptionId, reference)

        result.futureValue mustBe false
        verifyNoInteractions(mockObjectStoreClient)
      }
    }
  }
}

object ObjectStoreServiceSpec {
  val saoSubscriptionId                = "SAOSUB123456789"
  val hashedSaoSubscriptionId: String  = SubscriptionIdHash.hex(saoSubscriptionId)
  val malformedReferences: Seq[String] = Seq(
    "../../other-service/secret",
    "NOT 0123456789",
    ""
  )
  val notificationReference             = "NOT0123456789"
  val certificateReference              = "CERT123456789"
  val documentumPackageFileName: String =
    s"20260731_${notificationReference}_SAO_Notification_OFFICIAL_SENSITIVE.zip"
}
