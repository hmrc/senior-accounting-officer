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

package services

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder
import org.apache.pdfbox.rendering.PDFRenderer
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.scaladsl.Source
import org.apache.pekko.util.ByteString
import org.mockito.ArgumentMatchers
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{times, verify, when}
import org.mockito.internal.matchers.Any
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.twirl.api.{Html, HtmlFormat}
import uk.gov.hmrc.senioraccountingofficer.PdfTestData
import uk.gov.hmrc.senioraccountingofficer.services.PdfService
import uk.gov.hmrc.senioraccountingofficer.services.PdfService.{Notification, asSource}
import uk.gov.hmrc.senioraccountingofficer.utils.OpenHtmlToPdfService
import uk.gov.hmrc.senioraccountingofficer.views.html.{CertificatePdfView, NotificationPdfView}

import scala.concurrent.ExecutionContext

class PdfServiceSpec extends AnyWordSpec with Matchers with MockitoSugar with GuiceOneAppPerSuite {

  given ExecutionContext         = ExecutionContext.global
  given actorSystem: ActorSystem = ActorSystem()

  val mockOpenHtmlToPdfService: OpenHtmlToPdfService   = mock[OpenHtmlToPdfService]
  val mockNotificationPdfTemplate: NotificationPdfView = mock[NotificationPdfView]
  val mockCertificatePdfTemplate: CertificatePdfView   = mock[CertificatePdfView]
  val abcv: PdfRendererBuilder = mock[PdfRendererBuilder]

  val service: PdfService = PdfService(mockOpenHtmlToPdfService, mockNotificationPdfTemplate, mockCertificatePdfTemplate)




  "PdfService" must {
    "return Source object after NotificationPdf generation" in {
      val notification = PdfTestData.testNotificationData(3, None)
//      val a: Source[ByteString, ?] = Source[ByteString, ?]
      when(mockNotificationPdfTemplate.apply(any())).thenReturn(Html("<p>a</p>"))
      when(mockNotificationPdfTemplate.toString).thenReturn("a")
      when(mockOpenHtmlToPdfService.builderFor(any())).thenReturn(any[PdfRendererBuilder])
      when(abcv.asSource).thenReturn(any[Source[ByteString, ?]])
      
      val res = service.generateNotificationPdf(notification)

      verify(mockOpenHtmlToPdfService, times(1)).builderFor(any())
      res mustBe Source[ByteString, ?]

    }
    }

}
