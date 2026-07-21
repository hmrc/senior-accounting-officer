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

import org.mockito.ArgumentMatchers.{any, eq as meq, isNull}
import org.mockito.Mockito.when
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.mockito.MockitoSugar
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}
import uk.gov.hmrc.objectstore.client.Md5Hash
import uk.gov.hmrc.objectstore.client.ObjectSummaryWithMd5
import uk.gov.hmrc.objectstore.client.Path
import uk.gov.hmrc.objectstore.client.play.PlayObjectStoreClient
import uk.gov.hmrc.senioraccountingofficer.connectors.CertificateConnector
import uk.gov.hmrc.senioraccountingofficer.models.dps.CertificateDpsRequest
import uk.gov.hmrc.senioraccountingofficer.services.CertificateService.DownstreamService.DPS

import scala.concurrent.{ExecutionContext, Future}

import java.time.Instant

import CertificateService.PostCertificateResponse.*
import CertificateServiceSpec.*

class CertificateServiceSpec extends AnyWordSpec with Matchers with MockitoSugar with ScalaFutures {

  given ExecutionContext = ExecutionContext.global
  given HeaderCarrier    = HeaderCarrier()

  val mockConnector: CertificateConnector          = mock[CertificateConnector]
  val mockObjectStoreClient: PlayObjectStoreClient = mock[PlayObjectStoreClient]
  val service                                      = new CertificateService(mockConnector, mockObjectStoreClient)

  "postCertificate" must {
    "return Success if everything was orchestrated successfully" in {
      val mockResponse = HttpResponse(201, validDpsResponseBody)
      when(mockConnector.postCertificate(any(), any())(using any())).thenReturn(Future.successful(mockResponse))

      when(
        mockObjectStoreClient.putObject(
          path = meq(
            Path
              .Directory(objectStorePath)
              .file(objectStoreFilename)
          ),
          content = meq(objectStoreFileContent),
          retentionPeriod = isNull,
          contentType = isNull,
          contentMd5 = isNull,
          owner = meq(objectStoreOwner)
        )(using any(), any())
      )
        .thenReturn(
          Future.successful(ObjectSummaryWithMd5(Path.File(objectStoreFilename), 0, Md5Hash("hash"), Instant.now))
        )

      val result = service.postCertificate(requestId, testRequest).futureValue

      result mustBe Success(certificateRef)
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
      submitterName = "Firstname Lastname",
      saoName = "Firstname Lastname",
      saoEmail = "firstname.lastname@example.com",
      companies = List.empty
    )
  val certificateRef               = "CRT0001234567"
  val validDpsResponseBody: String = s"""{"certificateRef":"$certificateRef"}"""
  val objectStorePath: String      = s"/senior-accounting-officer/${certificateRef}/"
  val objectStoreFilename: String  = s"${certificateRef}_SAO_Certificate.pdf"
  val objectStoreOwner             = "senior-accounting-officer"
  val objectStoreFileContent       = "dummy file content"
}
