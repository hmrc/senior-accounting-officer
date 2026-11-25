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
import play.api.Logging
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.objectstore.client.play.Implicits.*
import uk.gov.hmrc.objectstore.client.play.PlayObjectStoreClient
import uk.gov.hmrc.objectstore.client.{Object as ObjectStoreObject, ObjectSummary, Path}
import uk.gov.hmrc.senioraccountingofficer.services.ObjectStoreService.{objectStoreOwner, referenceFormat}
import uk.gov.hmrc.senioraccountingofficer.utils.SubscriptionIdHash

import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

import javax.inject.Inject

class ObjectStoreService @Inject() (objectStoreClient: PlayObjectStoreClient)(using ec: ExecutionContext)
    extends Logging {
  def isNotificationPdfAvailable(saoSubscriptionId: String, notificationReference: String)(using
      hc: HeaderCarrier
  ): Future[Boolean] =
    isPdfAvailable(saoSubscriptionId, notificationReference, SubmissionDocumentType.Notification)

  def isCertificatePdfAvailable(saoSubscriptionId: String, certificateReference: String)(using
      hc: HeaderCarrier
  ): Future[Boolean] =
    isPdfAvailable(saoSubscriptionId, certificateReference, SubmissionDocumentType.Certificate)

  def downloadSubmissionPdf(saoSubscriptionId: String, reference: String, documentType: SubmissionDocumentType)(using
      hc: HeaderCarrier
  ): Future[Option[ObjectStoreObject[Source[ByteString, NotUsed]]]] =
    if !isWellFormedReference(reference) then {
      logger.warn("[OBJECT_STORE][GetObject] rejected a reference that is not well formed")
      Future.successful(None)
    } else {
      objectStoreClient
        .getObject[Source[ByteString, NotUsed]](
          path = submissionDirectory(saoSubscriptionId).file(documentType.pdfFileName(reference)),
          owner = objectStoreOwner
        )
        .recoverWith { case NonFatal(e) =>
          logger.warn("[OBJECT_STORE][GetObject]", e)
          Future.failed(e)
        }
    }

  private def isPdfAvailable(saoSubscriptionId: String, reference: String, documentType: SubmissionDocumentType)(using
      hc: HeaderCarrier
  ): Future[Boolean] = {
    if !isWellFormedReference(reference) then {
      logger.warn("[OBJECT_STORE][ListObjects] rejected a reference that is not well formed")
      Future.successful(false)
    } else {
      objectStoreClient
        .listObjects(
          path = submissionDirectory(saoSubscriptionId),
          owner = objectStoreOwner
        )
        .map {
          _.objectSummaries.exists { case ObjectSummary(Path.File(_, fileName), _, _) =>
            fileName == documentType.pdfFileName(reference)
          }
        }
        .recoverWith { case NonFatal(e) =>
          logger.warn("[OBJECT_STORE][ListObjects]", e)
          Future.successful(false)
        }
    }
  }

  def downloadDocumentumPackage(submissionId: String, fileName: String)(using
      hc: HeaderCarrier
  ): Future[Option[Source[ByteString, NotUsed]]] =
    objectStoreClient
      .getObject[Source[ByteString, NotUsed]](
        path = Path.Directory(s"/sdes/$submissionId/").file(fileName),
        owner = objectStoreOwner
      )
      .map(_.map(_.content))
      .recoverWith { case NonFatal(e) =>
        logger.warn("[OBJECT_STORE][getObject]", e)
        Future.successful(None)
      }

  private def isWellFormedReference(reference: String): Boolean =
    referenceFormat.matches(reference)

  private def submissionDirectory(saoSubscriptionId: String): Path.Directory =
    Path.Directory(s"/$objectStoreOwner/${SubscriptionIdHash.hex(saoSubscriptionId)}/")
}

object ObjectStoreService {
  val objectStoreOwner = "senior-accounting-officer"

  private val referenceFormat = "^[A-Za-z0-9]{1,64}$".r
}

enum SubmissionDocumentType(val documentName: String) {
  case Notification extends SubmissionDocumentType("Notification")
  case Certificate  extends SubmissionDocumentType("Certificate")

  def pdfFileName(reference: String): String = s"${reference}_SAO_$documentName.pdf"
}
