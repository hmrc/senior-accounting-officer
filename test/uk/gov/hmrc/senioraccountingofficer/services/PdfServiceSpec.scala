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

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.scaladsl.Source
import org.apache.pekko.util.ByteString
import org.mockito.ArgumentMatchers
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{times, verify, when}
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.twirl.api.Html
import uk.gov.hmrc.senioraccountingofficer.PdfTestData
import uk.gov.hmrc.senioraccountingofficer.models.dps.{GetSubscriptionDpsResponse, NominatedCompany}
import uk.gov.hmrc.senioraccountingofficer.services.PdfServiceSpec.*
import uk.gov.hmrc.senioraccountingofficer.utils.OpenHtmlToPdfService
import uk.gov.hmrc.senioraccountingofficer.utils.TestDataGenerator.generateUtr
import uk.gov.hmrc.senioraccountingofficer.views.html.{CertificatePdfView, NotificationPdfView}

import scala.concurrent.ExecutionContext

class PdfServiceSpec extends AnyWordSpec with Matchers with MockitoSugar with GuiceOneAppPerSuite {

  given ExecutionContext = ExecutionContext.global
  given ActorSystem      = ActorSystem()

  val mockOpenHtmlToPdfService: OpenHtmlToPdfService   = mock[OpenHtmlToPdfService]
  val mockNotificationPdfTemplate: NotificationPdfView = mock[NotificationPdfView]
  val mockCertificatePdfTemplate: CertificatePdfView   = mock[CertificatePdfView]
  val mockPdfRendererBuilder: PdfRendererBuilder       = mock[PdfRendererBuilder]

  val service: PdfService =
    PdfService(mockOpenHtmlToPdfService, mockNotificationPdfTemplate, mockCertificatePdfTemplate)

  "PdfService" must {
    "return Source object after Notification Pdf generation" in {
      val notification = PdfTestData.testNotificationData(3, None)
      val txt          = "notification"
      val html         = s"<p>$txt</p>"
      when(mockNotificationPdfTemplate.apply(any())).thenReturn(Html(html))
      when(mockNotificationPdfTemplate.toString).thenReturn(txt)
      when(mockOpenHtmlToPdfService.builderFor(txt)).thenReturn(mockPdfRendererBuilder)

      val res = service.generateNotificationPdf(notification)
      verify(mockOpenHtmlToPdfService, times(1)).builderFor(html)
      res mustBe a[Source[ByteString, ?]]
    }

    "return Source object after Certificate Pdf generation" in {
      val certificate = PdfTestData.testCertificateData(3, None, None)
      val txt         = "certificate"
      val html        = s"<p>$txt</p>"
      when(mockCertificatePdfTemplate.apply(any())).thenReturn(Html(html))
      when(mockCertificatePdfTemplate.toString).thenReturn(txt)
      when(mockOpenHtmlToPdfService.builderFor(txt)).thenReturn(mockPdfRendererBuilder)

      val res = service.generateCertificatePdf(certificate, dummySubscription)
      verify(mockOpenHtmlToPdfService, times(1)).builderFor(html)
      res mustBe a[Source[ByteString, ?]]
    }

  }

}

object PdfServiceSpec {
  val dummySubscription: GetSubscriptionDpsResponse = GetSubscriptionDpsResponse(
    "etmpSafeId",
    NominatedCompany(None, "example name", generateUtr),
    Nil
  )
}
