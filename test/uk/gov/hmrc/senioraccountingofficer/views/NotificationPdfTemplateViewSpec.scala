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

  given ExecutionContext = ExecutionContext.global

  given ActorSystem = ActorSystem()

  val notificationData: Notification =
    PdfTestData.testNotificationData(3, Option(AdditionalInformationGenerator.generate(totalBytes = 32767L, 1)))
  val notificationPdfTemplate: NotificationPdfView = app.injector.instanceOf[NotificationPdfView]
  val doc: Document                                = Jsoup.parse(notificationPdfTemplate(notificationData).body)

  "NotificationPdfView" must {
    "must generate a pdf with the correct title" in {
      doc.title mustBe pageTitle
    }

    "check meta tags contains content" in {
      metaTags.foreach((name, expectedContent) => {
        val metaTag = doc.selectFirst(s"""meta[name="$name"]""")
        metaTag.attr("content") mustBe expectedContent
      })
    }

    "display the logo container section of the pdf view" in {
      doc.logoText.text mustBe logoText
      doc.logo.eachAttr("src").get(0) mustBe imgPath
      doc.logo.eachAttr("src").size mustBe 1

      doc.logo.eachAttr("alt").get(0) mustBe logoAltText
      doc.logo.eachAttr("alt").size mustBe 1
    }
    "check the 'bookmarks' section of the pdf view, when 'additional information' notification attribute exist" in {
      doc.bookmarks.select("bookmark").size mustBe 5
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
      bookmark.size mustBe 5
      bookmark.eachAttr("name").size mustBe 5
      bookmark.eachAttr("href").size mustBe 5
      bookmark.eachAttr("name").get(0) mustBe "Submission"
      bookmark.eachAttr("href").get(0) mustBe "#submission"
      bookmark.eachAttr("name").get(1) mustBe "Registration"
      bookmark.eachAttr("href").get(1) mustBe "#registration"
      bookmark.eachAttr("name").get(2) mustBe "Senior Accounting Officer (SAO)"
      bookmark.eachAttr("href").get(2) mustBe "#senior-accounting-officer"
      bookmark.eachAttr("name").get(3) mustBe "Additional information about your notification"
      bookmark.eachAttr("href").get(3) mustBe "#additional-information"
      bookmark.eachAttr("name").get(4) mustBe "Companies in your notification"
      bookmark.eachAttr("href").get(4) mustBe "#companies-list"
    }
    "display 'notification submission record' section of the pdf" in {
      doc.heading.size() mustBe 1
      doc.heading.text() mustBe notificationHeader
    }
    "display the 'submission' section of the pdf view" in {
      doc.submissionDetailsSubheading.text() mustBe subheadings.head
    }
    "display the 'subscription' section of the pdf view" in {
      doc.companyDetailsSubheading.text() mustBe subheadings(1)
      subscriptionHeaders
        .zip(doc.companyDetailsTableHeaders.eachText())
        .foreach((expectedHeader, actualHeader) => actualHeader mustBe expectedHeader)
      doc.companyDetailsTableHeaders.size() mustBe 5

      val actualCompanyDetails = List(
        notificationData.nominatedCompany.name,
        notificationData.nominatedCompany.crn.getOrElse(""),
        notificationData.nominatedCompany.utr,
        s"${notificationData.subscriptionCreationDateTime} UK time",
        notificationData.subscriptionId
      )
      actualCompanyDetails
        .zip(doc.companyDetailsTableData.eachText())
        .foreach((expectedData, actualData) => actualData mustBe expectedData)
      actualCompanyDetails.size mustBe 5
    }
    "display the 'senior accounting officer' section of the pdf view" in {
      doc.saoHistorySubheading.text() mustBe subheadings(2)

      notificationData.saoHistory.mkString("\n") mustBe
        """SaoTenure(Fake Jackson Brown,Some(1 June 2024),None)
          |SaoTenure(Fake Ashley Ross,Some(1 January 2024),Some(31 May 2024))
          |SaoTenure(Fake John Smith,Some(1 January 2023),Some(31 May 2023))""".stripMargin
      doc.saoHistoryTable.toString mustBe
        """<table>
        | <tbody>
        |  <tr>
        |   <th class="bold">SAO at the end of the financial year</th>
        |   <td>Fake Jackson Brown</td>
        |  </tr>
        |  <tr>
        |   <th class="bold">Start date</th>
        |   <td>1 June 2024</td>
        |  </tr>
        |  <tr>
        |   <th class="bold">SAO before Fake Jackson Brown</th>
        |   <td>Fake Ashley Ross</td>
        |  </tr>
        |  <tr>
        |   <th class="bold">Start date</th>
        |   <td>1 January 2024</td>
        |  </tr>
        |  <tr>
        |   <th class="bold">End date</th>
        |   <td>31 May 2024</td>
        |  </tr>
        |  <tr>
        |   <th class="bold">SAO before Fake Ashley Ross</th>
        |   <td>Fake John Smith</td>
        |  </tr>
        |  <tr>
        |   <th class="bold">Start date</th>
        |   <td>1 January 2023</td>
        |  </tr>
        |  <tr>
        |   <th class="bold">End date</th>
        |   <td>31 May 2023</td>
        |  </tr>
        | </tbody>
        |</table>""".stripMargin
    }
    "display the 'additional information' section of the pdf view when there is additional information" in {
      val additionalInformation = "1. First item\r\n2. Second item\n\nA new paragraph with <unsafe> text"
      val notification          = notificationData.copy(additionalInformation = Some(additionalInformation))
      val additionalInfoDoc     = Jsoup.parse(notificationPdfTemplate(notification).body)
      val actualAddInfo         = additionalInfoDoc.addInfo.first()

      additionalInfoDoc.addInfoSubheading.text mustBe subheadings(3)
      additionalInfoDoc.addInfo.size() mustBe 1
      actualAddInfo.select("br").isEmpty mustBe true
      actualAddInfo.text() mustBe "1. First item 2. Second item A new paragraph with <unsafe> text"
      actualAddInfo.html() must include("&lt;unsafe&gt;")
    }
    "not display the 'additional information' section when additional information is not given" in {
      val notification    = PdfTestData.testNotificationData(3, None)
      val doc: Document   = Jsoup.parse(notificationPdfTemplate(notification).body)
      val expectedAddInfo = "Not provided"
      val actualAddInfo   = doc.addInfo.eachText()

      doc.addInfoSubheading.text mustBe "Additional information about your notification"

      actualAddInfo.mkString mustBe expectedAddInfo
    }
    "display the 'companies-list' section of the pdf view" in {
      doc.companiesSubheading.text mustBe subheadings(4)
      doc.companiesParagraph1.text mustBe companyListParagraph(
        notificationData.companies.size,
        notificationData.saoHistory.head.name
      )

      notificationData.companies.mkString("\n") mustBe
        """Row(Test Company 1,6000032741,00970313,PLC,Active,31 Jan 2025)
          |Row(Test Halcyon Merchants International 2,8000018620,00814904,PLC,Active,31 Mar 2025)
          |Row(Test Pinnacle Freight and Forwarding Solutions 3,1000049581,00906606,PLC,Administration,31 Mar 2025)""".stripMargin
      doc.companiesTable.toString mustBe
        """<table>
        | <thead>
        |  <tr>
        |   <th>Company name</th>
        |   <th>CRN</th>
        |   <th>UTR</th>
        |   <th>Type</th>
        |   <th>Status</th>
        |   <th>Financial year end</th>
        |  </tr>
        | </thead>
        | <tbody>
        |  <tr>
        |   <td class="bold">Test Company 1</td>
        |   <td>00970313</td>
        |   <td>6000032741</td>
        |   <td>PLC</td>
        |   <td>Active</td>
        |   <td>31 Jan 2025</td>
        |  </tr>
        |  <tr>
        |   <td class="bold">Test Halcyon Merchants International 2</td>
        |   <td>00814904</td>
        |   <td>8000018620</td>
        |   <td>PLC</td>
        |   <td>Active</td>
        |   <td>31 Mar 2025</td>
        |  </tr>
        |  <tr>
        |   <td class="bold">Test Pinnacle Freight and Forwarding Solutions 3</td>
        |   <td>00906606</td>
        |   <td>1000049581</td>
        |   <td>PLC</td>
        |   <td>Administration</td>
        |   <td>31 Mar 2025</td>
        |  </tr>
        | </tbody>
        |</table>""".stripMargin
    }

    "derive the companies-list summary from the notification" in {
      val notification = notificationData.copy(
        saoHistory = Seq(notificationData.saoHistory.head.copy(name = "Different SAO")),
        companies = notificationData.companies.take(1)
      )
      val variableDoc = Jsoup.parse(notificationPdfTemplate(notification).body)

      variableDoc.companiesParagraph1.text mustBe companyListParagraph(1, "Different SAO")
    }
  }
}

object NotificationPdfTemplateViewSpec {

  extension (doc: Document) {
    def logo: Elements      = doc.select(".logo")
    def logoText: Elements  = doc.select(".logo-text")
    def bookmarks: Elements = doc.select("bookmarks")

    def heading: Elements                     = doc.select("h1")
    def submissionDetailsSubheading: Elements = doc.select("#submission")

    def companyDetailsSubheading: Elements   = doc.select("#registration")
    def companyDetailsTableHeaders: Elements = doc.select("#registration + table tr > th")
    def companyDetailsTableData: Elements    = doc.select("#registration + table tr > td")

    def saoHistorySubheading: Elements = doc.select("#senior-accounting-officer")
    def saoHistoryTable: Elements      = doc.select("#senior-accounting-officer + table")

    def addInfoSubheading: Elements     = doc.select("#additional-information")
    def addInfo: Elements               = doc.select("#additional-information + p")
    def companiesSubheading             = doc.select("#companies-list")
    def companiesParagraph1: Elements   = doc.select("#companies-list + p")
    def companiesTable: Elements        = doc.select("#companies-list ~ table")
    def companiesTableHeaders: Elements = doc.select("#companies-list ~ table th")
    def companiesTableData: Elements    = doc.select("#companies-list ~ table tr")

  }

  val imgPath     = "gov-uk-logo.png"
  val logoAltText = "GOV.UK"
  val logoText    = "Senior Accounting Officer notification and certificate"

  val bookmarkNames: List[String] =
    List(
      "Submission",
      "Registration",
      "Senior Accounting Officer (SAO)",
      "Additional information about your notification",
      "Companies in your notification"
    )
  val bookmarkHrefs: List[String] =
    List("#submission", "#registration", "#senior-accounting-officer", "#additional-information", "#companies-list")

  val notificationHeader              = "Notification submission record"
  val pageTitle                       = "Senior Accounting Officer Notification submission record"
  val metaTags: Seq[(String, String)] = Seq(
    ("author", "HMRC forms service"),
    ("subject", "SAO notification submission record"),
    ("Creator", "HMRC forms service")
  )

  val subheadings: Seq[String] =
    Seq(
      "Submission",
      "Registration",
      "Senior Accounting Officer (SAO)",
      "Additional information about your notification",
      "Companies in your notification"
    )

  def companyListParagraph(companyCount: Int, saoName: String): String =
    s"This list is from your submission template. It shows $companyCount companies $saoName was responsible for in the financial year."

  val subscriptionHeaders: Seq[String] =
    Seq("Company name", "CRN", "UTR", "Date of registration", "Registration reference number")

  val saoDetailsTableHeaders: Seq[String] = Seq("Full name", "Role start date", "Role end date")

  val companiesTableHeaders: Seq[String] = Seq("Company name", "CRN", "UTR", "Type", "Status", "Financial year end")
}
