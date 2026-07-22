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

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.Materializer
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import uk.gov.hmrc.senioraccountingofficer.PdfTestData
import uk.gov.hmrc.senioraccountingofficer.services.PdfService
import uk.gov.hmrc.senioraccountingofficer.services.PdfService.Notification
import uk.gov.hmrc.senioraccountingofficer.utils.OpenHtmlToPdfService
import uk.gov.hmrc.senioraccountingofficer.views.html.{CertificatePdfView, NotificationPdfView}

import scala.concurrent.ExecutionContext

class PdfServiceSpec extends AnyWordSpec with Matchers with MockitoSugar with GuiceOneAppPerSuite {

  given ExecutionContext         = ExecutionContext.global
  given actorSystem: ActorSystem = ActorSystem()

  val openHtmlToPdfService: OpenHtmlToPdfService   = OpenHtmlToPdfService()
  val notificationPdfTemplate: NotificationPdfView = app.injector.instanceOf[NotificationPdfView]
  val certificatePdfTemplate: CertificatePdfView   = CertificatePdfView()

  val service: PdfService = PdfService(openHtmlToPdfService, notificationPdfTemplate, certificatePdfTemplate)

//  val notification: Notification = PdfTestData.testNotificationData(2, 1)
//  val doc: Document              = Jsoup.parse(notificationPdfTemplate(notification).body)

//  "PdfService" must {
//    "check notification pdf structure" in {
//      doc.select("h1").size() mustBe 1
//      doc.select("h1").text() mustBe notificationHeader
//
//    }
  "return pdf content for notification" in {

//    service.generateNotificationPdf(notification)
//      println(res)
  }

}
