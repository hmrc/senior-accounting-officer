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
import uk.gov.hmrc.senioraccountingofficer.connectors.CertificateConnector
import uk.gov.hmrc.senioraccountingofficer.models.dps.{CertificateDpsRequest, CertificateDpsResponse}
import uk.gov.hmrc.senioraccountingofficer.services.CertificateService.*
import uk.gov.hmrc.senioraccountingofficer.services.CertificateService.DownstreamService.DPS
import uk.gov.hmrc.senioraccountingofficer.services.CertificateService.PostCertificateResponse.*

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

import javax.inject.Inject

class CertificateService @Inject() (
    certificateConnector: CertificateConnector
)(using ExecutionContext) {

  def postCertificate(subscriptionId: String, request: CertificateDpsRequest)(using
      HeaderCarrier
  ): Future[PostCertificateResponse] = {
    for {
      dpsResult <- postCertificateDps(subscriptionId, request)
    } yield Success(certificateRef = dpsResult.certificateRef)
  }.merge

  private def postCertificateDps(subscriptionId: String, request: CertificateDpsRequest)(using
      HeaderCarrier
  ): EitherT[Future, PostCertificateResponse with Failure, CertificateDpsResponse] = {
    EitherT(certificateConnector.postCertificate(subscriptionId, Json.toJson(request).toString).map {
      case HttpResponse(CREATED, body, _) =>
        Try(Json.parse(body).validate[CertificateDpsResponse].asEither).toEither.flatten.left
          .map(_ => MalformedResponse(DPS))
      case HttpResponse(BAD_REQUEST, _, _)           => Left(BadRequestFailure(DPS))
      case HttpResponse(INTERNAL_SERVER_ERROR, _, _) => Left(InternalServerFailure(DPS))
      case HttpResponse(SERVICE_UNAVAILABLE, _, _)   => Left(ServiceUnavailableFailure(DPS))
      case HttpResponse(status, _, _)                => Left(UnknownFailure(DPS, status))
    })
  }
}

object CertificateService {
  enum DownstreamService {
    case DPS
  }
  sealed trait Failure
  enum PostCertificateResponse {
    case Success(certificateRef: String)                                   extends PostCertificateResponse
    case MalformedResponse(downstreamService: DownstreamService)           extends PostCertificateResponse with Failure
    case BadRequestFailure(downstreamService: DownstreamService)           extends PostCertificateResponse with Failure
    case InternalServerFailure(downstreamService: DownstreamService)       extends PostCertificateResponse with Failure
    case ServiceUnavailableFailure(downstreamService: DownstreamService)   extends PostCertificateResponse with Failure
    case UnknownFailure(downstreamService: DownstreamService, status: Int) extends PostCertificateResponse with Failure
  }
}
