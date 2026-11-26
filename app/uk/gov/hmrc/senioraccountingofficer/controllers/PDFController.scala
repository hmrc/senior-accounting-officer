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

package uk.gov.hmrc.senioraccountingofficer.controllers

import play.api.Logging
import play.api.mvc.{Action, AnyContent, ControllerComponents}
import uk.gov.hmrc.senioraccountingofficer.controllers.actions.{
  EnsureCorrelationIdAction,
  EnsureSaoSubscriptionIdAction,
  IdentifierAction
}
import uk.gov.hmrc.senioraccountingofficer.repositories.submission.SubmissionStateRepository
import uk.gov.hmrc.senioraccountingofficer.services.ObjectStoreService
import uk.gov.hmrc.senioraccountingofficer.services.SubmissionDocumentType.{Certificate, Notification}

import scala.concurrent.{ExecutionContext, Future}

import javax.inject.Inject

class PDFController @Inject() (
    cc: ControllerComponents,
    objectStore: ObjectStoreService,
    identify: IdentifierAction,
    ensureCorrelationId: EnsureCorrelationIdAction,
    repository: SubmissionStateRepository
)(using
    ExecutionContext
) extends BaseController(cc)
    with Logging {

  def pdfStatusCertificate(saoSubscriptionId: String, certificateReference: String): Action[AnyContent] =
    (identify andThen ensureCorrelationId andThen new EnsureSaoSubscriptionIdAction(saoSubscriptionId)).async {
      implicit req =>
        val result = objectStore.isCertificatePdfAvailable(saoSubscriptionId, certificateReference)
        result.map {
          case true  => Ok
          case false => NotFound
        }
    }
  def getPDFCertificate(saoSubscriptionId: String, certificateReference: String): Action[AnyContent] =
    (identify andThen ensureCorrelationId andThen new EnsureSaoSubscriptionIdAction(saoSubscriptionId)).async {
      implicit req =>
        objectStore
          .downloadSubmissionPdf(saoSubscriptionId, certificateReference, Certificate)
          .map {
            case Some(pdf) =>
              Ok.chunked(pdf.content)
                .as("application/pdf")

            case None =>
              NotFound
          }
    }

  def pdfStatusNotification(saoSubscriptionId: String, notificationReference: String): Action[AnyContent] =
    (identify andThen ensureCorrelationId andThen new EnsureSaoSubscriptionIdAction(saoSubscriptionId)).async {
      implicit req =>
        val result = objectStore.isNotificationPdfAvailable(saoSubscriptionId, notificationReference)
        result.map {
          case true  => Ok
          case false => NotFound
        }
    }

  def getPDFNotification(saoSubscriptionId: String, notificationReference: String): Action[AnyContent] =
    (identify andThen ensureCorrelationId andThen new EnsureSaoSubscriptionIdAction(saoSubscriptionId)).async {
      implicit req =>
        objectStore
          .downloadSubmissionPdf(saoSubscriptionId, notificationReference, Notification)
          .map {
            case Some(pdf) =>
              Ok.chunked(pdf.content)
                .as("application/pdf")

            case None =>
              NotFound
          }
    }

  def getDocumentumPackage(submissionId: String, fileName: String): Action[AnyContent] =
    (identify andThen ensureCorrelationId).async { implicit req =>
      repository.getSubscriptionIdFromSubmissionId(submissionId, req.saoSubscriptionId).flatMap {
        case false => Future.successful(NotFound)
        case true  =>
          objectStore
            .downloadDocumentumPackage(submissionId, fileName)
            .map {
              case Some(pdf) =>
                Ok.chunked(pdf)
                  .as("application/zip")

              case None =>
                NotFound
            }
      }
    }
}
