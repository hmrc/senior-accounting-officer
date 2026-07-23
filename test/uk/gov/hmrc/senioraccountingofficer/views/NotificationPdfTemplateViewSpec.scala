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
import uk.gov.hmrc.senioraccountingofficer.services.PdfService.Notification
import uk.gov.hmrc.senioraccountingofficer.views.html.NotificationPdfView
import uk.gov.hmrc.senioraccountingofficer.{AdditionalInformationGenerator, PdfTestData}
import views.NotificationPdfTemplateViewSpec.*

import scala.concurrent.ExecutionContext

class NotificationPdfTemplateViewSpec extends AnyWordSpec with Matchers with MockitoSugar with GuiceOneAppPerSuite {

  given ExecutionContext         = ExecutionContext.global
  given actorSystem: ActorSystem = ActorSystem()

  val notificationData: Notification =
    PdfTestData.testNotificationData(3, Option(AdditionalInformationGenerator.generate(totalBytes = 32767L, 1)))
  val notificationPdfTemplate: NotificationPdfView = app.injector.instanceOf[NotificationPdfView]
  val doc: Document                                = Jsoup.parse(notificationPdfTemplate(notificationData).body)

  "NotificationPdfView" must {
    "display the logo container section of the pdf view" in {
      doc.logoText.text mustBe logoText
      doc.logo.eachAttr("src").get(0) mustBe imgPath
      doc.logo.eachAttr("src").size mustBe 1

      doc.logo.eachAttr("alt").get(0) mustBe logoAltText
      doc.logo.eachAttr("alt").size mustBe 1
    }
    "check the 'bookmarks' section of the pdf view, when 'additional information' notification attribute exist" in {
      doc.bookmarks.select("bookmark").size mustBe 4
      bookmarkNames
        .zip(doc.bookmarks.select("bookmark").eachAttr("name"))
        .foreach((expectedName, actualName) => actualName mustBe expectedName)
      bookmarkHrefs
        .zip(doc.bookmarks.select("bookmark").eachAttr("href"))
        .foreach((expectedHref, actualHref) => actualHref mustBe expectedHref)
    }

    "check the 'bookmarks' section of the pdf view, when the 'additional Information' notification attribute does not exist" in {
      val notification  = PdfTestData.testNotificationData(3, None).copy(companies = Seq())
      val doc: Document = Jsoup.parse(notificationPdfTemplate(notification).body)
      val bookmark      = doc.bookmarks.select("bookmark")
      bookmark.size mustBe 3
      bookmark.eachAttr("name").size mustBe 3
      bookmark.eachAttr("href").size mustBe 3
      bookmark.eachAttr("name").get(0) mustBe "Nominated company details"
      bookmark.eachAttr("href").get(0) mustBe "#company-details"
      bookmark.eachAttr("name").get(1) mustBe "Contact details"
      bookmark.eachAttr("href").get(1) mustBe "#contact-details"
      bookmark.eachAttr("name").get(2) mustBe "List of companies"
      bookmark.eachAttr("href").get(2) mustBe "#companies-list"
    }
    "display 'notification submission record' section of the pdf" in {
      doc.heading.size() mustBe 1
      doc.heading.text() mustBe notificationHeader
      doc.paragraph1.text() mustBe paragraph1
    }
    "display the 'company details' section of the pdf view" in {
      doc.submissionDetailsSubheading.text() mustBe subheadings(0)
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
    }
    "display the 'contact details' section of the pdf view" in {
      doc.saoDetailSubheading.text() mustBe subheadings(1)

      saoDetailsTableHeaders
        .zip(doc.saoDetailTableHeaders.eachText())
        .foreach((expectedHeader, actualHeader) => actualHeader mustBe expectedHeader)

      val expectedSaoDetailsData = notificationData.saoHistory
      expectedSaoDetailsData
        .zip(doc.saoDetailTableData)
        .foreach((expectedRow, actualRow) => {
          val expectedStartDate = expectedRow.startDate.getOrElse("")
          val expectedEndDate   = expectedRow.endDate.getOrElse("")
          val colVals           = actualRow.select("td").eachText()
          colVals.get(0) mustBe expectedRow.name
          colVals.get(1) mustBe expectedStartDate
          if colVals.size == 3 then colVals.get(2) mustBe expectedEndDate
        })

    }
    "display the 'additional information' section of the pdf view when there is additional information" in {
      val expectedAddInfo = notificationData.additionalInformation.getOrElse("").split("\n")
      val actualAddInfo   = doc.addInfo.eachText()

      doc.addInfoSubheading.text mustBe subheadings(2)

      expectedAddInfo
        .zip(actualAddInfo)
        .foreach((expectedParagraph, actualParagraph) => actualParagraph mustBe expectedParagraph.stripTrailing())
    }
    "not display the 'additional information' section when additional information is not given" in {
      val notification    = PdfTestData.testNotificationData(3, None)
      val doc: Document   = Jsoup.parse(notificationPdfTemplate(notification).body)
      val expectedAddInfo = notification.additionalInformation.getOrElse("").split("\n")
      val actualAddInfo   = doc.addInfo.eachText()

      doc.addInfoSubheading.text mustBe ""

      expectedAddInfo
        .zip(actualAddInfo)
        .foreach((expectedParagraph, actualParagraph) => actualParagraph mustBe expectedParagraph)

    }
    "display the 'companies-list' section of the pdf view" in {
      doc.companiesSubheading.text mustBe subheadings(3)
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
    def logo: Elements      = doc.select(".logo")
    def logoText: Elements  = doc.select(".logo-text")
    def bookmarks: Elements = doc.select("bookmarks")

    def heading: Elements                     = doc.select("h1")
    def paragraph1: Elements                  = doc.select("h1 + p")
    def submissionDetailsSubheading: Elements = doc.select("h1 + p + h2")
    def companyDetailsTableHeaders: Elements  = doc.select("h1 + p + h2 + table").select("thead").select("th")
    def companyDetailsTableData: Elements     = doc.select("h1 + p + h2 + table").select("tbody > tr > td")

    def saoDetailSubheading: Elements   = doc.select("#contact-details")
    def saoDetailTableHeaders: Elements = doc.select("#contact-details + table > thead > tr > th")
    def saoDetailTableData: Elements    = doc.select("#contact-details + table > tbody > tr")

    def addInfoSubheading: Elements     = doc.select("#additional-information")
    def addInfoParagraph1: Elements     = doc.select("#additional-information+p")
    def addInfo: Elements               = doc.select("#additional-information + p ~ p")
    def companiesSubheading             = doc.select("#companies-list")
    def companiesTableHeaders: Elements = doc.select("tables-page > h2 + table > thead > tr > th")
    def companiesTableData: Elements    = doc.select("tables-page > h2 + table > tbody > tr")

  }

  val imgPath     = "gov-uk-logo.png"
  val logoAltText = "GOV.UK"
  val logoText    = "Senior Accounting Officer notification and certificate"

  val bookmarkNames: List[String] =
    List("Nominated company details", "Contact details", "Additional information", "List of companies")
  val bookmarkHrefs: List[String] =
    List("#company-details", "#contact-details", "#additional-information", "#companies-list")

  val notificationHeader = "Notification submission record"
  val paragraph1         =
    "This document is a record of the information submitted to HMRC through the Digital SAO service at the time of submission. It includes the SAO's name, the dates they held the role during the notification period, contact details recorded for service updates and compliance purposes, and the information uploaded in the submission template for the notification."
  val subheadings: Seq[String] =
    Seq("Submission details", "Senior Accounting Officer details", "Additional information", "List of companies")
  val companyDetailsHeaders: Seq[String] =
    Seq("Company name", "Financial year end date", "Submission date", "Reference number")

  val addInfoParagraph1                   = "Information about your notification"
  val saoDetailsTableHeaders: Seq[String] = Seq("Full name", "Role start date", "Role end date")

  val companiesTableHeaders: Seq[String] = Seq("Company name", "CRN", "UTR", "Type", "Status", "Financial year end")
}
