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
import uk.gov.hmrc.senioraccountingofficer.services.PdfService.Certificate
import uk.gov.hmrc.senioraccountingofficer.views.CertificatePdfTemplateViewSpec.*
import uk.gov.hmrc.senioraccountingofficer.views.html.CertificatePdfView
import uk.gov.hmrc.senioraccountingofficer.{AdditionalInformationGenerator, PdfTestData}

import scala.concurrent.ExecutionContext

class CertificatePdfTemplateViewSpec extends AnyWordSpec with Matchers with MockitoSugar with GuiceOneAppPerSuite {

  given ExecutionContext         = ExecutionContext.global
  given actorSystem: ActorSystem = ActorSystem()

  val certificateData: Certificate =
    PdfTestData.testCertificateData(
      3,
      Some("Firstname Lastname"),
      Option(AdditionalInformationGenerator.generate(totalBytes = 32767L, 1))
    )
  val certificatePdfTemplate: CertificatePdfView = app.injector.instanceOf[CertificatePdfView]
  val doc: Document                              = Jsoup.parse(certificatePdfTemplate(certificateData).body)

  "CertificatePdfView" must {
    "display the logo container section of the pdf view" in {
      doc.logoText.text mustBe logoText
      doc.logo.eachAttr("src").get(0) mustBe imgPath
      doc.logo.eachAttr("src").size mustBe 1

      doc.logo.eachAttr("alt").get(0) mustBe logoAltText
      doc.logo.eachAttr("alt").size mustBe 1
    }
    "check the 'bookmarks' section of the pdf view, when 'additional information', 'qualified' and 'unqualified' certificate attributes exist" in {
      doc.bookmarks.select("bookmark").size mustBe 4
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

      doc.bookmarks.select("bookmark").size mustBe 1
      doc.bookmarks.select("bookmark").eachAttr("name").size mustBe 1
      doc.bookmarks.select("bookmark").eachAttr("href").size mustBe 1
      doc.bookmarks.select("bookmark").eachAttr("name").get(0) mustBe "Submission details"
      doc.bookmarks.select("bookmark").eachAttr("href").get(0) mustBe "#submission-details"
    }

    "display 'certificate submission record' section of the pdf" in {
      doc.heading.size() mustBe 1
      doc.heading.text() mustBe certificateHeader
      doc.paragraph1.text() mustBe paragraph1
    }
    "display the 'submission details' section of the pdf view, when there is a an 'authorised submitter'" in {
      doc.saoDetailSubheading.text() mustBe subheadings(0)

      saoDetailsTableHeaders
        .zip(doc.saoDetailTableHeaders.eachText)
        .foreach((expectedHeader, actualHeader) => actualHeader mustBe expectedHeader)

      val expectedSaoDetailsData = List(
        certificateData.saoName,
        certificateData.saoEmail,
        certificateData.submitterName,
        certificateData.submissionDate,
        certificateData.submissionId
      )
      expectedSaoDetailsData
        .zip(doc.saoDetailTableData.eachText)
        .foreach((expectedCol, actualCol) => {
          actualCol mustBe expectedCol
        })

    }

    "display the 'submission details' section of the pdf view, when there is not an 'authorised submitter'" in {
      val certificate   = PdfTestData.testCertificateData(3, None, certificateData.additionalInformation)
      val doc: Document = Jsoup.parse(certificatePdfTemplate(certificate).body)

      doc.saoDetailSubheading.text() mustBe subheadings(0)

      saoDetailsTableHeadersWithoutAuthSubmitter
        .zip(doc.saoDetailTableHeaders.eachText)
        .foreach((expectedHeader, actualHeader) => actualHeader mustBe expectedHeader)

      val expectedSaoDetailsData = List(
        certificate.saoName,
        certificate.saoEmail,
        certificate.submissionDate,
        certificate.submissionId
      )

      doc.saoDetailTableData.eachText().size() mustBe expectedSaoDetailsData.size
      expectedSaoDetailsData
        .zip(doc.saoDetailTableData.eachText)
        .foreach((expectedCol, actualCol) => {
          actualCol mustBe expectedCol
        })

    }

    "display the 'additional information' section of the pdf view when there is additional information" in {
      val expectedAddInfo = certificateData.additionalInformation.getOrElse("").split("\n")
      val actualAddInfo   = doc.addInfo.eachText()

      doc.addInfoSubheading.text mustBe subheadings(1)
      doc.addInfoParagraph1.text mustBe addInfoParagraph1

      expectedAddInfo
        .zip(actualAddInfo)
        .foreach((expectedParagraph, actualParagraph) => actualParagraph mustBe expectedParagraph.stripTrailing())
    }
    "not display the 'additional information' section when additional information is not given" in {
      val certificate     = PdfTestData.testCertificateData(3, None, None)
      val doc: Document   = Jsoup.parse(certificatePdfTemplate(certificate).body)
      val expectedAddInfo = certificate.additionalInformation.getOrElse("").split("\n")
      val actualAddInfo   = doc.addInfo.eachText()

      doc.addInfoSubheading.text mustBe ""
      doc.addInfoParagraph1.text mustBe ""

      expectedAddInfo
        .zip(actualAddInfo)
        .foreach((expectedParagraph, actualParagraph) => actualParagraph mustBe expectedParagraph)

    }
    "display the 'qualified certificates' section on the pdf view, with an 'authorised submitter' paragraph" in {
      val expectedQualCertPar = qualCompaniesPara_A
        .replace("{submitterName}", certificateData.submitterName.get)
        .replace("{saoName}", certificateData.saoName)
        .replace("{length}", certificateData.qualified.size.toString)

      doc.qualCertSubheading.text mustBe subheadings(2)
      doc.qualCertParagraph.text mustBe expectedQualCertPar
      doc.qualCertParagraph.size mustBe 1

      qualCertTableHeaders
        .zip(doc.qualCertTableHeaders.eachText)
        .foreach((expectedHeader, actualHeader) => actualHeader mustBe expectedHeader)

      val expectedQualData = certificateData.qualified.toList
      expectedQualData
        .zip(doc.qualCertTableData)
        .foreach((expectedQualRow, actualQualRow) => {
          val cols = actualQualRow.select("td").eachText()
          cols.get(0) mustBe expectedQualRow.companyName
          cols.get(1) mustBe expectedQualRow.utr
          cols.get(2) mustBe expectedQualRow.qualifiedRegimesAsText
          cols.get(3) mustBe expectedQualRow.additionalInformation.getOrElse("").replace("\n", " ").stripTrailing()
        })
      doc.qualCertTableData.size() mustBe certificateData.qualified.size

    }

    "display the 'qualified certificates' section on the pdf view, with an 'SAO submitter' paragraph" in {
      val certificate   = PdfTestData.testCertificateData(3, None, certificateData.additionalInformation)
      val doc: Document = Jsoup.parse(certificatePdfTemplate(certificate).body)

      val expectedQualCertPar = qualCompaniesPara_B
        .replace("{saoName}", certificateData.saoName)
        .replace("{length}", certificateData.qualified.size.toString)

      doc.qualCertSubheading.text mustBe subheadings(2)
      doc.qualCertParagraph.text mustBe expectedQualCertPar
      doc.qualCertParagraph.size mustBe 1

      qualCertTableHeaders
        .zip(doc.qualCertTableHeaders.eachText)
        .foreach((expectedHeader, actualHeader) => actualHeader mustBe expectedHeader)

      val expectedQualData = certificateData.qualified.toList
      expectedQualData
        .zip(doc.qualCertTableData)
        .foreach((expectedQualRow, actualQualRow) => {
          val cols = actualQualRow.select("td").eachText()
          cols.get(0) mustBe expectedQualRow.companyName
          cols.get(1) mustBe expectedQualRow.utr
          cols.get(2) mustBe expectedQualRow.qualifiedRegimesAsText
          cols.get(3) mustBe expectedQualRow.additionalInformation.getOrElse("").replace("\n", " ").stripTrailing()
        })
      doc.qualCertTableData.size() mustBe certificateData.qualified.size

    }
    "not display the 'qualified certificates' section on the pdf view, when there is no qualified companies " in {
      val certificate   = certificateData.copy(companies = Seq())
      val doc: Document = Jsoup.parse(certificatePdfTemplate(certificate).body)

      doc.qualCertSubheading.text mustBe ""
      doc.qualCertParagraph.text mustBe ""
      doc.qualCertTableHeaders.size() mustBe 0
      doc.qualCertTableData.size() mustBe 0
    }
    "display the 'unqualified certificates' section on the pdf view, with an 'authorised submitter' paragraph" in {
      val expectedUnqualCertPar = unqualCompaniesPara_A
        .replace("{submitterName}", certificateData.submitterName.get)
        .replace("{saoName}", certificateData.saoName)
        .replace("{length}", certificateData.unqualified.size.toString)

      doc.unqualCertSubheading.text mustBe subheadings(3)
      doc.unqualCertParagraph.text mustBe expectedUnqualCertPar
      doc.unqualCertParagraph.size mustBe 1

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
          cols.get(3) mustBe expectedUnqualRow.companyType
          cols.get(4) mustBe expectedUnqualRow.status
          cols.get(5) mustBe expectedUnqualRow.financialYearEndDate
        })
      doc.unqualCertTableData.size() mustBe certificateData.unqualified.size
    }

    "display the 'unqualified certificates' section on the pdf view, with an 'SAO submitter' paragraph " in {
      val certificate           = PdfTestData.testCertificateData(3, None, certificateData.additionalInformation)
      val doc: Document         = Jsoup.parse(certificatePdfTemplate(certificate).body)
      val expectedUnqualCertPar = unqualCompaniesPara_B
        .replace("{saoName}", certificateData.saoName)
        .replace("{length}", certificateData.unqualified.size.toString)

      doc.unqualCertSubheading.text mustBe subheadings(3)
      doc.unqualCertParagraph.text mustBe expectedUnqualCertPar
      doc.unqualCertParagraph.size mustBe 1

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
          cols.get(3) mustBe expectedUnqualRow.companyType
          cols.get(4) mustBe expectedUnqualRow.status
          cols.get(5) mustBe expectedUnqualRow.financialYearEndDate
        })
      doc.unqualCertTableData.size() mustBe certificateData.unqualified.size
    }

    "not display the 'unqualified certificates' section on the pdf view, when there are no unqualified companies" in {
      val certificate   = certificateData.copy(companies = Seq())
      val doc: Document = Jsoup.parse(certificatePdfTemplate(certificate).body)

      doc.unqualCertSubheading.text mustBe ""
      doc.unqualCertParagraph.text mustBe ""
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

    def heading: Elements                     = doc.select("h1")
    def paragraph1: Elements                  = doc.select("h1 + p")
    def submissionDetailsSubheading: Elements = doc.select("h1 + p + h2")
    def companyDetailsTableHeaders: Elements  = doc.select("h1 + p + h2 + table").select("thead").select("th")
    def companyDetailsTableData: Elements     = doc.select("h1 + p + h2 + table").select("tbody > tr > td")

    def saoDetailSubheading: Elements   = doc.select("#submission-details")
    def saoDetailTableHeaders: Elements = doc.select("#submission-details + table > thead > tr > th")
    def saoDetailTableData: Elements    = doc.select("#submission-details + table > tbody > tr > td")

    def addInfoSubheading: Elements = doc.select("#additional-information")
    def addInfoParagraph1: Elements = doc.select("#additional-information+h4")
    def addInfo: Elements           = doc.select("#additional-information + p ~ p")
    def companiesSubheading         = doc.select("#companies-list")

    def qualCertSubheading: Elements   = doc.select("#qualified-certificates")
    def qualCertParagraph: Elements    = doc.select("#qualified-certificates ~ p")
    def qualCertTableHeaders: Elements = doc.select("#qualified-certificates ~ p ~ table > thead > tr > td")
    def qualCertTableData: Elements    = doc.select("#qualified-certificates ~ p ~ table > tbody > tr")

    def unqualCertSubheading: Elements   = doc.select("#unqualified-certificates")
    def unqualCertParagraph: Elements    = doc.select("#unqualified-certificates ~ p")
    def unqualCertTableHeaders: Elements = doc.select("#unqualified-certificates ~ p ~ table > thead > tr > td")
    def unqualCertTableData: Elements    = doc.select("#unqualified-certificates ~ p ~ table > tbody > tr")
  }

  val imgPath     = "gov-uk-logo.png"
  val logoAltText = "GOV.UK"
  val logoText    = "Senior Accounting Officer notification and certificate"

  val bookmarkNames: List[String] =
    List("Submission details", "Additional information", "Qualified certificates", "Unqualified certificates")
  val bookmarkHrefs: List[String] =
    List("#submission-details", "#additional-information", "#qualified-certificates", "#unqualified-certificates")

  val certificateHeader = "Certificate submission record"
  val paragraph1        =
    "This document records the information submitted to HMRC through the Digital SAO service at the time of submission, including the SAO's name as it appears on the certificate, the declaration made by the SAO or their authorised representative, the certificate type (unqualified or qualified), and the group's tax accounting arrangements."
  val subheadings: Seq[String] =
    Seq(
      "Submission details",
      "Additional information and explanation",
      "Companies with qualified certificates",
      "Companies with unqualified certificates"
    )
  val saoDetailsTableHeaders: Seq[String] =
    Seq("Senior Accounting Officer", "Email address", "Authorised submitter", "Submission date", "Reference number")
  val saoDetailsTableHeadersWithoutAuthSubmitter: Seq[String] =
    Seq("Senior Accounting Officer", "Email address", "Submission date", "Reference number")
  val addInfoParagraph1 = "Information about your certificate"

  val qualCompaniesPara_A =
    "In accordance with paragraph 2 of Schedule 46 to the Finance Act 2009, I {submitterName}, on the behalf of, {saoName} the Senior Accounting Officer, hereby certify that throughout the company's financial year ended 31 December 2024, {length} companies did not have appropriate tax accounting arrangements."
  val qualCompaniesPara_B =
    "In accordance with paragraph 2 of Schedule 46 to the Finance Act 2009, I {saoName} the Senior Accounting Officer, hereby certify that throughout the company's financial year ended 31 December 2024, {length} companies did not have appropriate tax accounting arrangements."
  val qualCertTableHeaders: Seq[String] = Seq("Company name", "UTR", "Tax regimes", "Additional information")

  val unqualCompaniesPara_A =
    "In accordance with Paragraph 2 Schedule 46 Finance Act 2009, I {submitterName}, on the behalf of, {saoName} the Senior Accounting Officer hereby certify, in respect of the financial year ended 31 December 2024 that {length} companies had appropriate tax accounting arrangements throughout the year."
  val unqualCompaniesPara_B =
    "In accordance with Paragraph 2 Schedule 46 Finance Act 2009, I {saoName} the Senior Accounting Officer hereby certify, in respect of the financial year ended 31 December 2024 that {length} companies had appropriate tax accounting arrangements throughout the year."
  val unqualCertTableHeaders: Seq[String] =
    Seq("Company name", "CRN", "UTR", "Type", "Status", "Financial year end")
}
