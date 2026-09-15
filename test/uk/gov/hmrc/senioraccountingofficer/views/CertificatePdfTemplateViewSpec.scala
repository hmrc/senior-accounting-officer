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

package uk.gov.hmrc.senioraccountingofficer.views

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
import uk.gov.hmrc.senioraccountingofficer.services.PdfService.Certificate
import uk.gov.hmrc.senioraccountingofficer.views.CertificatePdfTemplateViewSpec.*
import uk.gov.hmrc.senioraccountingofficer.views.html.CertificatePdfView
import uk.gov.hmrc.senioraccountingofficer.{AdditionalInformationGenerator, PdfTestData}

import scala.concurrent.ExecutionContext

class CertificatePdfTemplateViewSpec extends AnyWordSpec with Matchers with MockitoSugar with GuiceOneAppPerSuite {

  given ExecutionContext = ExecutionContext.global
  given ActorSystem      = ActorSystem()

  val certificateData: Certificate =
    PdfTestData.testCertificateData(
      5,
      Some("Firstname Lastname"),
      Option(AdditionalInformationGenerator.generate(totalBytes = 32767L, 1))
    )
  val certificatePdfTemplate: CertificatePdfView = app.injector.instanceOf[CertificatePdfView]
  val doc: Document                              = Jsoup.parse(certificatePdfTemplate(certificateData).body)

  "CertificatePdfView" must {

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
    "check the 'bookmarks' section of the pdf view, when 'additional information', 'qualified' and 'unqualified' certificate attributes exist" in {
      doc.bookmarks.select("bookmark").size mustBe 7
      bookmarkNames
        .zip(doc.bookmarks.select("bookmark").eachAttr("name"))
        .foreach((expectedName, actualName) => actualName mustBe expectedName)
      bookmarkHrefs
        .zip(doc.bookmarks.select("bookmark").eachAttr("href"))
        .foreach((expectedHref, actualHref) => actualHref mustBe expectedHref)
    }

    "check the 'bookmarks' section of the pdf view, when the 'additional information', 'qualified' and 'unqualified' certificate attributes does not exist" in {
      val certificate   = PdfTestData.testCertificateData(3, None, None).copy(companies = Seq())
      val doc: Document = Jsoup.parse(certificatePdfTemplate(certificate).body)

      doc.bookmarks.select("bookmark").size mustBe 7
      doc.bookmarks.select("bookmark").eachAttr("name").size mustBe 7
      doc.bookmarks.select("bookmark").eachAttr("href").size mustBe 7
      doc.bookmarks.select("bookmark").eachAttr("name").get(0) mustBe "Submission"
      doc.bookmarks.select("bookmark").eachAttr("href").get(0) mustBe "#submission"
      doc.bookmarks.select("bookmark").eachAttr("name").get(1) mustBe "Registration"
      doc.bookmarks.select("bookmark").eachAttr("href").get(1) mustBe "#registration"
      doc.bookmarks.select("bookmark").eachAttr("name").get(2) mustBe "Senior Accounting Officer(SAO)"
      doc.bookmarks.select("bookmark").eachAttr("href").get(2) mustBe "#senior-accounting-officer"
      doc.bookmarks.select("bookmark").eachAttr("name").get(3) mustBe "Declaration"
      doc.bookmarks.select("bookmark").eachAttr("href").get(3) mustBe "#declaration"
      doc.bookmarks.select("bookmark").eachAttr("name").get(4) mustBe "Additional information about your certificate"
      doc.bookmarks.select("bookmark").eachAttr("href").get(4) mustBe "#additional-information"
      doc.bookmarks.select("bookmark").eachAttr("name").get(5) mustBe "Companies with a qualified certificate"
      doc.bookmarks.select("bookmark").eachAttr("href").get(5) mustBe "#qualified-certificates"
      doc.bookmarks.select("bookmark").eachAttr("name").get(6) mustBe "Companies with an unqualified certificate"
      doc.bookmarks.select("bookmark").eachAttr("href").get(6) mustBe "#unqualified-certificates"
    }

    "display 'certificate submission record' section of the pdf" in {
      doc.heading.size() mustBe 1
      doc.heading.text() mustBe certificateHeader
    }
    "display the 'submission' section of the pdf view, when there is a an 'authorised submitter'" in {
      doc.submissionSubheading.text() mustBe subheadings(0)

      submissionTableHeaders
        .zip(doc.submissionTableHeaders.eachText)
        .foreach((expectedHeader, actualHeader) => actualHeader mustBe expectedHeader)

      val expectedSubmissionData = List(
        s"${certificateData.submissionDateTime} UK time",
        certificateData.submissionId
      )
      expectedSubmissionData
        .zip(doc.submissionTableData.eachText)
        .foreach((expectedCol, actualCol) => {
          actualCol mustBe expectedCol
        })

    }

    "display the 'submission' section of the pdf view, when there is not an 'authorised submitter'" in {
      val certificate   = certificateData.copy(submitterName = None)
      val doc: Document = Jsoup.parse(certificatePdfTemplate(certificate).body)

      doc.submissionSubheading.text() mustBe subheadings(0)

      submissionTableHeadersWithoutAuthSubmitter
        .zip(doc.submissionTableHeaders.eachText)
        .foreach((expectedHeader, actualHeader) => actualHeader mustBe expectedHeader)

      val expectedSubmissionData = List(
        s"${certificateData.submissionDateTime} UK time",
        certificateData.submissionId
      )

      doc.submissionTableData.eachText().size() mustBe expectedSubmissionData.size
      expectedSubmissionData
        .zip(doc.submissionTableData.eachText)
        .foreach((expectedCol, actualCol) => {
          actualCol mustBe expectedCol
        })

    }

    "display the 'subscription' section of the pdf view" in {
      doc.subscriptionSubheading.text() mustBe subheadings(1)
      subscriptionHeaders
        .zip(doc.subscriptionTableHeaders.eachText())
        .foreach((expectedHeader, actualHeader) => actualHeader mustBe expectedHeader)
      doc.subscriptionTableHeaders.size() mustBe 5

      val expectedSubscriptionData = List(
        certificateData.nominatedCompany.name,
        certificateData.nominatedCompany.crn.getOrElse(""),
        certificateData.nominatedCompany.utr,
        s"${certificateData.subscriptionCreationDateTime} UK time",
        certificateData.subscriptionId
      )
      expectedSubscriptionData
        .zip(doc.subscriptionTableData.eachText())
        .foreach((expectedData, actualData) => actualData mustBe expectedData)
      expectedSubscriptionData.size mustBe 5
    }

    "display the 'sao' section of the pdf view" in {
      doc.saoSubheading.text() mustBe subheadings(2)
      saoHeaders
        .zip(doc.saoTableHeaders.eachText())
        .foreach((expectedHeader, actualHeader) => actualHeader mustBe expectedHeader)
      doc.saoTableHeaders.size() mustBe 2

      val expectedSaoData = List(
        certificateData.saoName,
        certificateData.saoEmail
      )
      expectedSaoData
        .zip(doc.saoTableData.eachText())
        .foreach((expectedData, actualData) => actualData mustBe expectedData)
      expectedSaoData.size mustBe 2
    }

    "display the 'declaration' section of the pdf view when there is an assistant" in {
      doc.declarationSubheading.text() mustBe subheadings(3)
      declarationHeadersWithAssistant
        .zip(doc.declarationTableHeaders.eachText())
        .foreach((expectedHeader, actualHeader) => actualHeader mustBe expectedHeader)
      doc.declarationTableHeaders.size() mustBe 3

      val expectedDeclarationData = List(
        "A person authorised to submit on behalf of the SAO",
        certificateData.submitterName.fold("")(identity),
        certificateData.saoName
      )
      expectedDeclarationData
        .zip(doc.declarationTableData.eachText())
        .foreach((expectedData, actualData) => actualData mustBe expectedData)
      expectedDeclarationData.size mustBe 3
    }

    "display the 'additional information' section of the pdf view when there is additional information" in {
      val additionalInformation = "1. First item\r\n2. Second item\n\nA new paragraph with <unsafe> text"
      val certificate           = certificateData.copy(additionalInformation = Some(additionalInformation))
      val additionalInfoDoc     = Jsoup.parse(certificatePdfTemplate(certificate).body)
      val actualAddInfo         = additionalInfoDoc.addInfo.first()

      additionalInfoDoc.addInfoSubheading.text mustBe subheadings(4)
      additionalInfoDoc.addInfo.size() mustBe 1
      actualAddInfo.select("br").isEmpty mustBe true
      actualAddInfo.text() mustBe "1. First item 2. Second item A new paragraph with <unsafe> text"
      actualAddInfo.html() must include("&lt;unsafe&gt;")
    }
    "not display the 'additional information' section when additional information is not given" in {
      val certificate     = PdfTestData.testCertificateData(3, None, None)
      val doc: Document   = Jsoup.parse(certificatePdfTemplate(certificate).body)
      val expectedAddInfo = "Not provided"
      val actualAddInfo   = doc.addInfo.eachText()

      doc.addInfoSubheading.text mustBe subheadings(4)

      actualAddInfo.mkString mustBe expectedAddInfo
    }
    "display the 'qualified certificates' section on the pdf view" in {
      val expectedQualCertPar = qualifiedCompaniesDeclarationParagraph
        .replace("{saoName}", certificateData.saoName)
        .replace("{length}", certificateData.qualified.size.toString)

      doc.qualCertSubheading.text mustBe subheadings(5)
      doc.qualCertParagraph.text mustBe expectedQualCertPar
      doc.qualCertParagraph.size mustBe 1

      certificateData.qualified.mkString("\n") mustBe
        """Row(Test Company 1,6000032741,00970313,PLC,Active,31 Jan 2025,TaxRegimes(true,true,true,false,false,false,false,false,false,false),Some(Lorem ipsum dolor sit amet, consectetur adipisicing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua. Ut enim ad minim veniam, quis nostrud exercitation ullamco laboris nisi ut aliquip ex ea commodo consequat. Duis aute irure dolor in reprehenderit in voluptate velit esse cillum dolore eu fugiat nulla pariatur. Excepteur sint occaecat cupidatat non proident, sunt in culpa qui officia deserunt mollit anim id est laborum.
        |Lorem ipsum dolor sit amet, consectetur adipisicing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua. Ut enim ad ))
        |Row(Test Arkwright and Co 4,8000077228,00301748,PLC,Active,31 Mar 2025,TaxRegimes(false,true,false,false,false,false,false,false,false,false),Some(Lorem ipsum dolor sit amet, consectetur adipisicing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua. Ut enim ad minim veniam, quis nostrud exercitation ullamco laboris nisi ut aliquip ex ea commodo consequat. Duis aute irure dolor in reprehenderit in voluptate velit esse cillum dolore eu fugiat nulla pariatur. Excepteur sint occaecat cupidatat non proident, sunt in culpa qui officia deserunt mollit anim id est laborum.
        |Lorem ipsum dolor sit amet, consectetur adipisicing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua. Ut enim ad ))""".stripMargin

      doc.qualCertTable.toString mustBe
        """<qualified-page>
          | <h2 id="qualified-certificates">Companies with a qualified certificate</h2>
          | <p>In accordance with paragraph 2, Schedule 46 of the Finance Act 2009, I Test Jackson Brown, the Senior Accounting Officer, hereby certify that 2 companies did not have appropriate tax accounting arrangements.</p>
          | <table>
          |  <tbody>
          |   <tr>
          |    <th class="bold">Company name</th>
          |    <td>Test Company 1</td>
          |   </tr>
          |   <tr>
          |    <th class="bold">CRN</th>
          |    <td>00970313</td>
          |   </tr>
          |   <tr>
          |    <th class="bold">UTR</th>
          |    <td>6000032741</td>
          |   </tr>
          |   <tr>
          |    <th class="bold">Type</th>
          |    <td>PLC</td>
          |   </tr>
          |   <tr>
          |    <th class="bold">Status</th>
          |    <td>Active</td>
          |   </tr>
          |   <tr>
          |    <th class="bold">Financial year end</th>
          |    <td>31 Jan 2025</td>
          |   </tr>
          |   <tr>
          |    <th class="bold">Tax regimes</th>
          |    <td>Corporation Tax, VAT, PAYE</td>
          |   </tr>
          |   <tr>
          |    <th class="bold">Explain why the certificate is qualified</th>
          |    <td>Lorem ipsum dolor sit amet, consectetur adipisicing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua. Ut enim ad minim veniam, quis nostrud exercitation ullamco laboris nisi ut aliquip ex ea commodo consequat. Duis aute irure dolor in reprehenderit in voluptate velit esse cillum dolore eu fugiat nulla pariatur. Excepteur sint occaecat cupidatat non proident, sunt in culpa qui officia deserunt mollit anim id est laborum. Lorem ipsum dolor sit amet, consectetur adipisicing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua. Ut enim ad</td>
          |   </tr>
          |  </tbody>
          | </table>
          |</qualified-page>
          |<qualified-page>
          | <table>
          |  <tbody>
          |   <tr>
          |    <th class="bold">Company name</th>
          |    <td>Test Arkwright and Co 4</td>
          |   </tr>
          |   <tr>
          |    <th class="bold">CRN</th>
          |    <td>00301748</td>
          |   </tr>
          |   <tr>
          |    <th class="bold">UTR</th>
          |    <td>8000077228</td>
          |   </tr>
          |   <tr>
          |    <th class="bold">Type</th>
          |    <td>PLC</td>
          |   </tr>
          |   <tr>
          |    <th class="bold">Status</th>
          |    <td>Active</td>
          |   </tr>
          |   <tr>
          |    <th class="bold">Financial year end</th>
          |    <td>31 Mar 2025</td>
          |   </tr>
          |   <tr>
          |    <th class="bold">Tax regimes</th>
          |    <td>VAT</td>
          |   </tr>
          |   <tr>
          |    <th class="bold">Explain why the certificate is qualified</th>
          |    <td>Lorem ipsum dolor sit amet, consectetur adipisicing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua. Ut enim ad minim veniam, quis nostrud exercitation ullamco laboris nisi ut aliquip ex ea commodo consequat. Duis aute irure dolor in reprehenderit in voluptate velit esse cillum dolore eu fugiat nulla pariatur. Excepteur sint occaecat cupidatat non proident, sunt in culpa qui officia deserunt mollit anim id est laborum. Lorem ipsum dolor sit amet, consectetur adipisicing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua. Ut enim ad</td>
          |   </tr>
          |  </tbody>
          | </table>
          |</qualified-page>""".stripMargin
    }

    "display 'Not provided' the 'qualified certificates' section on the pdf view, when there is no qualified companies " in {
      val certificate   = certificateData.copy(companies = Seq())
      val doc: Document = Jsoup.parse(certificatePdfTemplate(certificate).body)

      doc.select("qualified-page").size() mustBe 1
      doc.select("qulified-page").isEmpty mustBe true
      doc.qualCertSubheading.text mustBe "Companies with a qualified certificate"
      doc.qualCertParagraph.text mustBe "Not provided"
      doc.qualCertTableHeaders.size() mustBe 0
      doc.qualCertTableData.size() mustBe 0
    }

    "render qualification statements as escaped plain text" in {
      val qualification = "First line\nSecond <unsafe> line"
      val qualified     = certificateData.qualified.head.copy(additionalInformation = Some(qualification))
      val certificate   = certificateData.copy(companies = Seq(qualified))
      val doc           = Jsoup.parse(certificatePdfTemplate(certificate).body)
      val statementCell = doc.select("qualified-page table td").last()

      statementCell.select("br").isEmpty mustBe true
      statementCell.text() mustBe "First line Second <unsafe> line"
      statementCell.html() must include("&lt;unsafe&gt;")
    }

    "display the 'unqualified certificates' section on the pdf view" in {
      val expectedUnqualCertPar = unqualifiedCompaniesDeclarationParagraph
        .replace("{saoName}", certificateData.saoName)
        .replace("{length}", certificateData.unqualified.size.toString)

      doc.unqualCertSubheading.text mustBe subheadings(6)
      doc.unqualCertParagraph.text mustBe expectedUnqualCertPar
      doc.unqualCertParagraph.size mustBe 1

      certificateData.unqualified.mkString("\n") mustBe
        """Row(Test Halcyon Merchants International 2,8000018620,00814904,PLC,Active,31 Mar 2025,TaxRegimes(false,false,false,false,false,false,false,false,false,false),Some(Lorem ipsum dolor sit amet, consectetur adipisicing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua. Ut enim ad minim veniam, quis nostrud exercitation ullamco laboris nisi ut aliquip ex ea commodo consequat. Duis aute irure dolor in reprehenderit in voluptate velit esse cillum dolore eu fugiat nulla pariatur. Excepteur sint occaecat cupidatat non proident, sunt in culpa qui officia deserunt mollit anim id est laborum.
        |Lorem ipsum dolor sit amet, consectetur adipisicing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua. Ut enim ad ))
        |Row(Test Pinnacle Freight and Forwarding Solutions 3,1000049581,00906606,PLC,Administration,31 Mar 2025,TaxRegimes(false,false,false,false,false,false,false,false,false,false),Some(Lorem ipsum dolor sit amet, consectetur adipisicing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua. Ut enim ad minim veniam, quis nostrud exercitation ullamco laboris nisi ut aliquip ex ea commodo consequat. Duis aute irure dolor in reprehenderit in voluptate velit esse cillum dolore eu fugiat nulla pariatur. Excepteur sint occaecat cupidatat non proident, sunt in culpa qui officia deserunt mollit anim id est laborum.
        |Lorem ipsum dolor sit amet, consectetur adipisicing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua. Ut enim ad ))
        |Row(Test Vortex Supply Co 5,6000042344,00998473,LTD,Active,31 Mar 2025,TaxRegimes(false,false,false,false,false,false,false,false,false,false),Some(Lorem ipsum dolor sit amet, consectetur adipisicing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua. Ut enim ad minim veniam, quis nostrud exercitation ullamco laboris nisi ut aliquip ex ea commodo consequat. Duis aute irure dolor in reprehenderit in voluptate velit esse cillum dolore eu fugiat nulla pariatur. Excepteur sint occaecat cupidatat non proident, sunt in culpa qui officia deserunt mollit anim id est laborum.
        |Lorem ipsum dolor sit amet, consectetur adipisicing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua. Ut enim ad ))""".stripMargin

      unqualCertTableHeaders
        .zip(doc.unqualCertTableHeaders.eachText)
        .foreach((expectedHeader, actualHeader) => actualHeader mustBe expectedHeader)

      val expectedUnqualData = certificateData.unqualified.toList
      expectedUnqualData
        .zip(doc.unqualCertTableData)
        .foreach((expectedUnqualRow, actualUnqualRow) => {
          val cols = actualUnqualRow.select("td").eachText()
          cols.get(0) mustBe expectedUnqualRow.companyName
          cols.get(1) mustBe expectedUnqualRow.crn
          cols.get(2) mustBe expectedUnqualRow.utr
          cols.get(3) mustBe expectedUnqualRow.companyType.toString
          cols.get(4) mustBe expectedUnqualRow.status.toString
          cols.get(5) mustBe expectedUnqualRow.financialYearEndDate
        })
      doc.unqualCertTableData.size() mustBe certificateData.unqualified.size
    }

    "display 'Not provided' under the 'unqualified certificates' section on the pdf view, when there are no unqualified companies" in {
      val certificate   = certificateData.copy(companies = Seq())
      val doc: Document = Jsoup.parse(certificatePdfTemplate(certificate).body)

      doc.unqualCertSubheading.text mustBe "Companies with an unqualified certificate"
      doc.unqualCertParagraph.text mustBe "Not provided"
      doc.unqualCertTableHeaders.size() mustBe 0
      doc.unqualCertTableData.size() mustBe 0
    }
  }
}

object CertificatePdfTemplateViewSpec {

  extension (doc: Document) {
    def logo: Elements      = doc.select(".logo")
    def logoText: Elements  = doc.select(".logo-text")
    def bookmarks: Elements = doc.select("bookmarks")

    def heading: Elements = doc.select("h1")

    def submissionSubheading: Elements   = doc.select("#submission")
    def submissionTableHeaders: Elements = doc.select("#submission + table th")
    def submissionTableData: Elements    = doc.select("#submission + table td")

    def subscriptionSubheading: Elements   = doc.select("#registration")
    def subscriptionTableHeaders: Elements = doc.select("#registration + table th")
    def subscriptionTableData: Elements    = doc.select("#registration + table td")

    def saoSubheading: Elements   = doc.select("#senior-accounting-officer")
    def saoTableHeaders: Elements = doc.select("#senior-accounting-officer + table th")
    def saoTableData: Elements    = doc.select("#senior-accounting-officer + table td")

    def declarationSubheading: Elements   = doc.select("#declaration")
    def declarationTableHeaders: Elements = doc.select("#declaration + table th")
    def declarationTableData: Elements    = doc.select("#declaration + table td")

    def addInfoSubheading: Elements = doc.select("#additional-information")
    def addInfo: Elements           = doc.select("#additional-information + p")

    def qualCertSubheading: Elements   = doc.select("#qualified-certificates")
    def qualCertParagraph: Elements    = doc.select("#qualified-certificates ~ p")
    def qualCertTable: Elements        = doc.select("qualified-page")
    def qualCertTableHeaders: Elements = doc.select("qualified-page ~ table th")
    def qualCertTableData: Elements    = doc.select("qualified-page ~ table td")

    def unqualCertSubheading: Elements   = doc.select("#unqualified-certificates")
    def unqualCertParagraph: Elements    = doc.select("#unqualified-certificates ~ p")
    def unqualCertTableHeaders: Elements = doc.select("#unqualified-certificates ~ p ~ table > thead > tr > td")
    def unqualCertTableData: Elements    = doc.select("#unqualified-certificates ~ p ~ table > tbody > tr")
  }

  val imgPath     = "gov-uk-logo.png"
  val logoAltText = "GOV.UK"
  val logoText    = "Senior Accounting Officer notification and certificate"

  val bookmarkNames: List[String] =
    List(
      "Submission",
      "Registration",
      "Senior Accounting Officer(SAO)",
      "Declaration",
      "Additional information about your certificate",
      "Companies with a qualified certificate",
      "Companies with an unqualified certificate"
    )
  val bookmarkHrefs: List[String] =
    List(
      "#submission",
      "#registration",
      "#senior-accounting-officer",
      "#declaration",
      "#additional-information",
      "#qualified-certificates",
      "#unqualified-certificates"
    )

  val certificateHeader               = "Certificate submission record"
  val pageTitle                       = "Senior Accounting Officer Certificate submission record"
  val metaTags: Seq[(String, String)] = Seq(
    ("author", "HMRC forms service"),
    ("subject", "SAO notification submission record"),
    ("Creator", "HMRC forms service")
  )
  val subheadings: Seq[String] =
    Seq(
      "Submission",
      "Registration",
      "Senior Accounting Officer(SAO)",
      "Declaration",
      "Additional information about your certificate",
      "Companies with a qualified certificate",
      "Companies with an unqualified certificate"
    )
  val submissionTableHeaders: Seq[String] =
    Seq("Date of submission", "Submission reference number")
  val submissionTableHeadersWithoutAuthSubmitter: Seq[String] =
    Seq("Date of submission", "Submission reference number")

  val subscriptionHeaders: Seq[String] =
    Seq("Company name", "CRN", "UTR", "Date of registration", "Registration reference number")

  val saoHeaders: Seq[String] =
    Seq("SAO name", "SAO email address")

  val declarationHeadersNoAssistant: Seq[String] =
    Seq("Who is submitting the certificate?", "SAO name on the declaration")

  val declarationHeadersWithAssistant: Seq[String] =
    Seq("Who is submitting the certificate?", "Authorised name on the declaration", "SAO name on the declaration")

  val qualifiedCompaniesDeclarationParagraph =
    "In accordance with paragraph 2, Schedule 46 of the Finance Act 2009, I {saoName}, the Senior Accounting Officer, hereby certify that {length} companies did not have appropriate tax accounting arrangements."

  val qualCertTableHeaders: Seq[String] = Seq("Company name", "UTR", "Tax regimes", "Additional information")

  val unqualifiedCompaniesDeclarationParagraph =
    "In accordance with Paragraph 2, Schedule 46 of the Finance Act 2009, I {saoName}, the Senior Accounting Officer hereby certify that {length} companies had appropriate tax accounting arrangements throughout the year."
  val unqualCertTableHeaders: Seq[String] =
    Seq("Company name", "CRN", "UTR", "Type", "Status", "Financial year end")
}
