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

import org.apache.pekko.stream.scaladsl.Source
import org.apache.pekko.util.ByteString
import org.mockito.ArgumentMatchers.{any, eq as meq}
import org.mockito.Mockito.{verify, when}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Millis, Seconds, Span}
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.mockito.MockitoSugar
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}
import uk.gov.hmrc.senioraccountingofficer.connectors.CertificateConnector
import uk.gov.hmrc.senioraccountingofficer.models.documentum.{DocumentumPackageContext, DocumentumPackageResult}
import uk.gov.hmrc.senioraccountingofficer.models.dps.CertificateDpsRequest
import uk.gov.hmrc.senioraccountingofficer.services.documentum.DocumentumPackageService
import uk.gov.hmrc.senioraccountingofficer.services.CertificateService.DownstreamService.DPS

import scala.concurrent.{ExecutionContext, Future}

import CertificateService.PostCertificateResponse.*
import CertificateServiceSpec.*

class CertificateServiceSpec extends AnyWordSpec with Matchers with MockitoSugar with ScalaFutures {

  override given patienceConfig: PatienceConfig =
    PatienceConfig(timeout = Span(5, Seconds), interval = Span(25, Millis))

  given ExecutionContext = ExecutionContext.global
  given HeaderCarrier    = HeaderCarrier()

  val mockConnector: CertificateConnector                    = mock[CertificateConnector]
  val mockDocumentumPackageService: DocumentumPackageService = mock[DocumentumPackageService]
  val mockPdfService: PdfService                             = mock[PdfService]
  val service = new CertificateService(mockConnector, mockDocumentumPackageService, mockPdfService)

  "postCertificate" must {
    "return Success if everything was orchestrated successfully" in {
      val mockResponse = HttpResponse(201, validDpsResponseBody)
      when(mockConnector.postCertificate(any(), any())(using any())).thenReturn(Future.successful(mockResponse))
      when(mockPdfService.generateCertificatePdf(any())).thenReturn(objectStoreFileContent)
      when(mockDocumentumPackageService.packageAndSubmit(any(), any())(using any()))
        .thenReturn(Future.successful(DocumentumPackageResult(packageAvailable = true, Some(objectStoreFilename))))

      val result = service.postCertificate(requestId, testRequest).futureValue

      result mustBe Success(certificateRef)
      verify(mockDocumentumPackageService)
        .packageAndSubmit(
          meq(DocumentumPackageContext.certificate(certificateRef, requestId, testRequest)),
          meq(objectStoreFileContent)
        )(using any())
    }

    "return MalformedResponse(DPS) for a malformed 201 response from DPS" in {
      val malformedResponseBody = "{"
      val mockResponse          = HttpResponse(201, malformedResponseBody)
      when(mockConnector.postCertificate(any(), any())(using any())).thenReturn(Future.successful(mockResponse))

      val result = service.postCertificate(requestId, testRequest).futureValue

      result mustBe MalformedResponse(DPS)
    }

    "return MalformedResponse(DPS) for an invalid 201 response from DPS" in {
      val invalidResponseBody = "{}"
      val mockResponse        = HttpResponse(201, invalidResponseBody)
      when(mockConnector.postCertificate(any(), any())(using any())).thenReturn(Future.successful(mockResponse))

      val result = service.postCertificate(requestId, testRequest).futureValue

      result mustBe MalformedResponse(DPS)
    }

    "return BadRequestFailure(DPS) for a 400 response from DPS" in {
      val mockResponse = HttpResponse(400)
      when(mockConnector.postCertificate(any(), any())(using any())).thenReturn(Future.successful(mockResponse))

      val result = service.postCertificate(requestId, testRequest).futureValue

      result mustBe BadRequestFailure(DPS)
    }

    "return InternalServerFailure(DPS) for a 500 response from DPS" in {
      val mockResponse = HttpResponse(500)
      when(mockConnector.postCertificate(any(), any())(using any())).thenReturn(Future.successful(mockResponse))

      val result = service.postCertificate(requestId, testRequest).futureValue

      result mustBe InternalServerFailure(DPS)
    }

    "return ServiceUnavailableFailure(DPS) for a 503 response from DPS" in {
      val mockResponse = HttpResponse(503)
      when(mockConnector.postCertificate(any(), any())(using any())).thenReturn(Future.successful(mockResponse))

      val result = service.postCertificate(requestId, testRequest).futureValue

      result mustBe ServiceUnavailableFailure(DPS)
    }

    "return UnknownFailure(DPS, status) for an unexpected status response from DPS" in {
      val unexpectedStatus = 600
      val mockResponse     = HttpResponse(unexpectedStatus)
      when(mockConnector.postCertificate(any(), any())(using any())).thenReturn(Future.successful(mockResponse))

      val result = service.postCertificate(requestId, testRequest).futureValue

      result mustBe UnknownFailure(DPS, unexpectedStatus)
    }
  }

}

object CertificateServiceSpec {
  val requestId                          = "123"
  val testRequest: CertificateDpsRequest =
    CertificateDpsRequest(
      submitterName = Some("Firstname Lastname"),
      saoName = "Firstname Lastname",
      saoEmail = "firstname.lastname@example.com",
      companies = List.empty
    )
  val certificateRef               = "CRT0001234567"
  val validDpsResponseBody: String = s"""{"certificateRef":"$certificateRef"}"""
  val objectStoreFilename: String  = s"20260728_${certificateRef}_SAO_Certificate_OFFICIAL_SENSITIVE.ZIP"
  val objectStoreFileContent       = Source.single(ByteString("dummy file content"))
}
