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

package views

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

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.Materializer
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.select.Elements
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.twirl.api.TwirlHelperImports.twirlJavaCollectionToScala
import uk.gov.hmrc.senioraccountingofficer.PdfTestData
import uk.gov.hmrc.senioraccountingofficer.services.PdfService.Notification
import uk.gov.hmrc.senioraccountingofficer.views.html.NotificationPdfView
import views.NotificationPdfTemplateViewSpec.*

import scala.concurrent.ExecutionContext

class NotificationPdfTemplateViewSpec extends AnyWordSpec with Matchers with MockitoSugar with GuiceOneAppPerSuite {

  given ExecutionContext         = ExecutionContext.global
  given actorSystem: ActorSystem = ActorSystem()

  val notificationData: Notification               = PdfTestData.testNotificationData(3, 1)
  val notificationPdfTemplate: NotificationPdfView = app.injector.instanceOf[NotificationPdfView]
  val doc: Document                                = Jsoup.parse(notificationPdfTemplate(notificationData).body)

  "NotificationPdfView" must {
    "check notification pdf structure" in {
      // image path to do

      doc.heading.size() mustBe 1
      doc.heading.text() mustBe notificationHeader

      doc.paragraph1.text() mustBe paragraph1
      doc.subheading1.text() mustBe subheadings(0)

      doc.saoDetailSubheading.text() mustBe subheadings(1)

      saoDetailsTableHeaders.zip(doc.saoDetailTableHeaders.eachText()).foreach((expectedHeader, actualHeader) => actualHeader mustBe expectedHeader)
      val expectedSaoDetailsData = notificationData.saoHistory

      println(doc.saoDetailTableData)
      println("")
      println(doc.saoDetailTableData.select("td"))
      expectedSaoDetailsData.zip(doc.saoDetailTableData).foreach((expectedRow, actualRow) => {

        val tds = actualRow.select("td").eachText().
        expectedRow.name mustBe tds(0)
        expectedRow.startDate mustBe tds(1)
        expectedRow.endDate mustBe tds(2)
      })



      companyDetailsHeaders
        .zip(doc.companyDetailsTableHeaders.eachText())
        .foreach((expectedHeader, actualHeader) => actualHeader mustBe expectedHeader)
      doc.companyDetailsTableHeaders.size() mustBe 4

      val actualCompanyDetails = List(
        notificationData.companyName,
        notificationData.financialYearEndDate,
        notificationData.submissionDate,
        notificationData.submissionId
      )
      actualCompanyDetails
        .zip(doc.companyDetailsTableData.eachText())
        .foreach((expectedHeader, actualHeader) => actualHeader mustBe expectedHeader)
      actualCompanyDetails.size mustBe 4

      companiesTableHeaders
        .zip(doc.companiesTableHeaders.eachText())
        .foreach((expectedHeader, actualHeader) => expectedHeader mustBe actualHeader)
      doc.companiesTableHeaders.size() mustBe 6

      notificationData.companies
        .zip(doc.companiesTableData)
        .foreach((expectedRow, actualRow) => {
          val flattened = List(
            expectedRow.companyName,
            expectedRow.crn,
            expectedRow.utr,
            expectedRow.companyType,
            expectedRow.status,
            expectedRow.financialYearEndDate
          )
          flattened
            .zip(actualRow.select("td").eachText())
            .foreach((expectedCol, actualCol) => actualCol mustBe expectedCol)
        })

      doc.companiesTableData.size() mustBe notificationData.companies.size

    }
  }
}

object NotificationPdfTemplateViewSpec {

  extension (doc: Document) {
    def heading: Elements                    = doc.select("h1")
    def paragraph1: Elements                 = doc.select("h1 + p")
    def subheading1: Elements                = doc.select("h1 + p + h2")
    def companyDetailsTableHeaders: Elements = doc.select("h1 + p + h2 + table").select("thead").select("th")
    def companyDetailsTableData: Elements    = doc.select("h1 + p + h2 + table").select("tbody > tr > td")

    def saoDetailSubheading: Elements = doc.select("#contact-details")
    def saoDetailTableHeaders: Elements = doc.select("#contact-details + table > thead > tr > th")
    def saoDetailTableData: Elements = doc.select("#contact-details + table > tbody > tr")

    def companiesTableHeaders: Elements = doc.select("tables-page > h2 + table > thead > tr > th")
    def companiesTableData: Elements    = doc.select("tables-page > h2 + table > tbody > tr")

  }

  val imgPath            = "src/gov-uk-logo.png/"
  val logoText           = "Senior Accounting Officer notification and certificate"
  val notificationHeader = "Notification submission record"
  val paragraph1         =
    "This document is a record of the information submitted to HMRC through the Digital SAO service at the time of submission. It includes the SAO's name, the dates they held the role during the notification period, contact details recorded for service updates and compliance purposes, and the information uploaded in the submission template for the notification."
  val subheadings: Seq[String] =
    Seq("Submission details", "Senior Accounting Officer details", "Additional information", "List of companies")
  val companyDetailsHeaders: Seq[String] =
    Seq("Company name", "Financial year end date", "Submission date", "Reference number")

  val saoDetailsTableHeaders = Seq("Full name","Role start date", "Role end date")

  val companiesTableHeaders: Seq[String] = Seq("Company name", "CRN", "UTR", "Type", "Status", "Financial year end")
}
