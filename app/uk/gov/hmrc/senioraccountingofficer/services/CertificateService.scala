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

import cats.data.EitherT
import play.api.http.Status.*
import play.api.libs.json.*
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}
import uk.gov.hmrc.senioraccountingofficer.connectors.{CertificateConnector, CrmmConnector, GetSubscriptionConnector}
import uk.gov.hmrc.senioraccountingofficer.models.crmm.{RetrieveCustomerRequest, RetrieveCustomerResponse}
import uk.gov.hmrc.senioraccountingofficer.models.documentum.DocumentumPackageContext
import uk.gov.hmrc.senioraccountingofficer.models.dps.{
  CertificateDpsRequest,
  CertificateDpsResponse,
  GetSubscriptionDpsResponse
}
import uk.gov.hmrc.senioraccountingofficer.services.CertificateService.*
import uk.gov.hmrc.senioraccountingofficer.services.CertificateService.DownstreamService.*
import uk.gov.hmrc.senioraccountingofficer.services.CertificateService.PostCertificateResponse.*
import uk.gov.hmrc.senioraccountingofficer.services.documentum.DocumentumPackageService

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

import javax.inject.Inject

class CertificateService @Inject() (
    getSubscriptionConnector: GetSubscriptionConnector,
    crmmConnector: CrmmConnector,
    certificateConnector: CertificateConnector,
    documentumPackageService: DocumentumPackageService,
    pdfService: PdfService
)(using ExecutionContext) {

  def postCertificate(subscriptionId: String, request: CertificateDpsRequest)(using
      HeaderCarrier
  ): Future[PostCertificateResponse] = {
    for {
      dpsSubscription <- getSubscriptionDps(subscriptionId)
      customerId <- retrieveCrmmCustomerId(dpsSubscription.nominatedCompany.crn, dpsSubscription.nominatedCompany.utr)
      dpsResult  <- postCertificateDps(subscriptionId, request)
      _          <- packageAndSubmitDocumentumFile(
        subscriptionId,
        dpsSubscription,
        dpsResult.certificateRef,
        request
      )
    } yield Success(certificateReference = dpsResult.certificateRef)
  }.merge

  private def getSubscriptionDps(
      subscriptionId: String
  )(using HeaderCarrier): EitherT[Future, PostCertificateResponse with Failure, GetSubscriptionDpsResponse] = {
    EitherT(
      getSubscriptionConnector
        .getSubscription(subscriptionId)
        .map {
          case HttpResponse(OK, body, _) =>
            Try(Json.parse(body).as[GetSubscriptionDpsResponse]).toEither.left
              .map { _ =>
                MalformedResponse(Subscription)
              }
          case HttpResponse(NO_CONTENT, _, _)            => Left(NotFoundFailure(Subscription))
          case HttpResponse(BAD_REQUEST, _, _)           => Left(Misalignment(Subscription))
          case HttpResponse(UNAUTHORIZED, _, _)          => Left(Misconfiguration(Subscription, UNAUTHORIZED))
          case HttpResponse(FORBIDDEN, _, _)             => Left(Misconfiguration(Subscription, FORBIDDEN))
          case HttpResponse(INTERNAL_SERVER_ERROR, _, _) => Left(DownstreamServiceError(Subscription))
          case HttpResponse(SERVICE_UNAVAILABLE, _, _)   => Left(DownstreamServiceUnavailable(Subscription))
          case HttpResponse(status, _, _)                => Left(UnknownFailure(Subscription, status))
        }
    )
  }

  private def retrieveCrmmCustomerId(
      crn: Option[String],
      utr: String
  )(using HeaderCarrier): EitherT[Future, PostCertificateResponse with Failure, Option[String]] = {
    val request = RetrieveCustomerRequest(crn, Some(utr))
    EitherT(
      crmmConnector
        .retrieveCustomer(request)
        .map {
          case HttpResponse(OK, body, _)                 => parseCrmmResponse(body)
          case HttpResponse(BAD_REQUEST, _, _)           => Left(Misalignment(CRMM))
          case HttpResponse(INTERNAL_SERVER_ERROR, _, _) => Left(DownstreamServiceError(CRMM))
          case HttpResponse(UNAUTHORIZED, _, _)          => Left(Misconfiguration(CRMM, UNAUTHORIZED))
          case HttpResponse(FORBIDDEN, _, _)             => Left(Misconfiguration(CRMM, FORBIDDEN))
          case HttpResponse(SERVICE_UNAVAILABLE, _, _)   => Left(DownstreamServiceUnavailable(CRMM))
          case HttpResponse(NOT_FOUND, _, _)             => Left(Misalignment(CRMM))
          case HttpResponse(status, _, _)                => Left(UnknownFailure(CRMM, status))
        }
    )
  }

  private def parseCrmmResponse(body: String): Either[PostCertificateResponse with Failure, Option[String]] = {
    Try(
      Json
        .parse(body)
        .as[RetrieveCustomerResponse]
    ).toEither match {
      case Left(_)         => Left(MalformedResponse(CRMM))
      case Right(customer) =>
        customer match {
          case RetrieveCustomerResponse(None, Some(_), false, "Failure") =>
            Right(None)
          case RetrieveCustomerResponse(Some(customerId), None, true, "Success") => Right(Some(customerId))
          case _                                                                 => Left(MalformedResponse(CRMM))
        }
    }
  }

  private def postCertificateDps(subscriptionId: String, request: CertificateDpsRequest)(using
      HeaderCarrier
  ): EitherT[Future, PostCertificateResponse with Failure, CertificateDpsResponse] = {
    EitherT(certificateConnector.postCertificate(subscriptionId, request).map {
      case HttpResponse(CREATED, body, _) =>
        Try(Json.parse(body).validate[CertificateDpsResponse].asEither).toEither.flatten.left
          .map(_ => MalformedResponse(DPS))
      case HttpResponse(BAD_REQUEST, _, _)           => Left(Misalignment(DPS))
      case HttpResponse(INTERNAL_SERVER_ERROR, _, _) => Left(DownstreamServiceError(DPS))
      case HttpResponse(UNAUTHORIZED, _, _)          => Left(Misconfiguration(DPS, UNAUTHORIZED))
      case HttpResponse(FORBIDDEN, _, _)             => Left(Misconfiguration(DPS, FORBIDDEN))
      case HttpResponse(NOT_FOUND, _, _)             => Left(Misalignment(DPS))
      case HttpResponse(SERVICE_UNAVAILABLE, _, _)   => Left(DownstreamServiceUnavailable(DPS))
      case HttpResponse(status, _, _)                => Left(UnknownFailure(DPS, status))
    })
  }

  private def packageAndSubmitDocumentumFile(
      subscriptionId: String,
      dpsSubscription: GetSubscriptionDpsResponse,
      certificateReference: String,
      request: CertificateDpsRequest
  )(using
      HeaderCarrier
  ) =
    EitherT.right[PostCertificateResponse with Failure](
      documentumPackageService.packageAndSubmit(
        DocumentumPackageContext.certificate(certificateReference, subscriptionId, request),
        pdfService.generateCertificatePdf(CertificateDpsRequest.toCertificate(request), dpsSubscription)
      )
    )
}

object CertificateService {
  enum DownstreamService {
    case Subscription, DPS, CRMM
  }
  sealed trait Failure
  enum PostCertificateResponse {
    case Success(certificateReference: String)                              extends PostCertificateResponse
    case MalformedResponse(downstreamService: DownstreamService)            extends PostCertificateResponse with Failure
    case DownstreamServiceUnavailable(downstreamService: DownstreamService) extends PostCertificateResponse with Failure
    case UnknownFailure(downstreamService: DownstreamService, status: Int)  extends PostCertificateResponse with Failure

    case NotFoundFailure(downstreamService: DownstreamService) extends PostCertificateResponse with Failure
    case Misalignment(downstreamService: DownstreamService)    extends PostCertificateResponse with Failure
    case Misconfiguration(downstreamService: DownstreamService, status: Int)
        extends PostCertificateResponse
        with Failure
    case DownstreamServiceError(downstreamService: DownstreamService) extends PostCertificateResponse with Failure
  }
}
