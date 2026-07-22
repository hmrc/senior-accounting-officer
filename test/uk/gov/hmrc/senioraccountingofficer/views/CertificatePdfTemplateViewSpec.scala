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
import uk.gov.hmrc.senioraccountingofficer.views.CertificatePdfTemplateViewSpec
import uk.gov.hmrc.senioraccountingofficer.{AdditionalInformationGenerator, PdfTestData}
import uk.gov.hmrc.senioraccountingofficer.views.html.CertificatePdfView
import uk.gov.hmrc.senioraccountingofficer.views.CertificatePdfTemplateViewSpec.*

import scala.concurrent.ExecutionContext

class CertificatePdfTemplateViewSpec extends AnyWordSpec with Matchers with MockitoSugar with GuiceOneAppPerSuite {

  given ExecutionContext         = ExecutionContext.global
  given actorSystem: ActorSystem = ActorSystem()

  val certificateData: Certificate = PdfTestData.testCertificateData(3, Option(AdditionalInformationGenerator.generate(totalBytes = 32767L, 1)))
  val certificatePdfTemplate: CertificatePdfView = app.injector.instanceOf[CertificatePdfView]
  val doc: Document                                = Jsoup.parse(certificatePdfTemplate(certificateData).body)

  "CertificatePdfView" must {
    "display the logo container section of the pdf view" in {
      // image path to do
    }
    "displays the 'bookmarks' section of the pdf view" in {
      // to do
    }
    "display 'certificate submission record' section of the pdf" in {
      doc.heading.size() mustBe 1
      doc.heading.text() mustBe certificateHeader
      doc.paragraph1.text() mustBe paragraph1
    }
    "display the 'submission details' section of the pdf view" in {
      doc.saoDetailSubheading.text() mustBe subheadings(0)

      saoDetailsTableHeaders
        .zip(doc.saoDetailTableHeaders.eachText())
        .foreach((expectedHeader, actualHeader) => actualHeader mustBe expectedHeader)

      val expectedSaoDetailsData = List(certificateData.saoName, certificateData.saoEmail, certificateData.submitterName, certificateData.submissionDate, certificateData.submissionId)
      expectedSaoDetailsData
        .zip(doc.saoDetailTableData.eachText())
        .foreach((expectedVal, actualVal) => {

          actualVal mustBe expectedVal
//          val expectedStartDate = expectedRow.startDate.getOrElse("")
//          val expectedEndDate = expectedRow.endDate.getOrElse("")
//          val colVals = actualRow.select("td").eachText()
//          colVals.get(0) mustBe expectedRow.name
//          colVals.get(1) mustBe expectedStartDate
//          colVals.get(2) mustBe expectedEndDate
        })

    }
    "display the 'additional information' section of the pdf view when there is additional information" in {
      val expectedAddInfo = certificateData.additionalInformation.getOrElse("").split("\n")
      val actualAddInfo = doc.addInfo.eachText()

      doc.addInfoSubheading.text mustBe subheadings(1)
      doc.addInfoParagraph1.text mustBe addInfoParagraph1

      expectedAddInfo
        .zip(actualAddInfo)
        .foreach((expectedParagraph, actualParagraph) => actualParagraph mustBe expectedParagraph.stripTrailing())
    }
    "not display the 'additional information' section when additional information is not given" in {
      val certificate = PdfTestData.testCertificateData(3, None)
      val doc: Document = Jsoup.parse(certificatePdfTemplate(certificate).body)
      val expectedAddInfo = certificate.additionalInformation.getOrElse("").split("\n")
      val actualAddInfo = doc.addInfo.eachText()

      doc.addInfoSubheading.text mustBe ""
      doc.addInfoParagraph1.text mustBe ""

      expectedAddInfo
        .zip(actualAddInfo)
        .foreach((expectedParagraph, actualParagraph) => actualParagraph mustBe expectedParagraph)

    }
//    "display the 'company details' section of the pdf view" in {
//      doc.submissionDetailsSubheading.text() mustBe subheadings(0)
//      companyDetailsHeaders
//        .zip(doc.companyDetailsTableHeaders.eachText())
//        .foreach((expectedHeader, actualHeader) => actualHeader mustBe expectedHeader)
//      doc.companyDetailsTableHeaders.size() mustBe 4
//
//      val actualCompanyDetails = List(
//        certificateHeader.companyName,
//        certificateHeader.financialYearEndDate,
//        certificateHeader.submissionDate,
//        certificateHeader.submissionId
//      )
//      actualCompanyDetails
//        .zip(doc.companyDetailsTableData.eachText())
//        .foreach((expectedHeader, actualHeader) => actualHeader mustBe expectedHeader)
//      actualCompanyDetails.size mustBe 4
//    }
//    "display the 'companies-list' section of the pdf view" in {
//      doc.companiesSubheading.text mustBe subheadings(3)
//      companiesTableHeaders
//        .zip(doc.companiesTableHeaders.eachText())
//        .foreach((expectedHeader, actualHeader) => expectedHeader mustBe actualHeader)
//      doc.companiesTableHeaders.size() mustBe 6
//
//      certificateData.companies
//        .zip(doc.companiesTableData)
//        .foreach((expectedRow, actualRow) => {
//          val flattened = List(
//            expectedRow.companyName,
//            expectedRow.crn,
//            expectedRow.utr,
//            expectedRow.companyType,
//            expectedRow.status,
//            expectedRow.financialYearEndDate
//          )
//          flattened
//            .zip(actualRow.select("td").eachText())
//            .foreach((expectedCol, actualCol) => actualCol mustBe expectedCol)
//        })
//
//      doc.companiesTableData.size() mustBe certificateData.companies.size
//    }
  }
}

object CertificatePdfTemplateViewSpec {

  extension (doc: Document) {
    def heading: Elements                     = doc.select("h1")
    def paragraph1: Elements                  = doc.select("h1 + p")
    def submissionDetailsSubheading: Elements = doc.select("h1 + p + h2")
    def companyDetailsTableHeaders: Elements  = doc.select("h1 + p + h2 + table").select("thead").select("th")
    def companyDetailsTableData: Elements     = doc.select("h1 + p + h2 + table").select("tbody > tr > td")

    def saoDetailSubheading: Elements   = doc.select("#submission-details")
    def saoDetailTableHeaders: Elements = doc.select("#contact-details + table > thead > tr > th")
    def saoDetailTableData: Elements    = doc.select("#contact-details + table > tbody > tr")

    def addInfoSubheading: Elements     = doc.select("#additional-information")
    def addInfoParagraph1: Elements     = doc.select("#additional-information+h4")
    def addInfo: Elements               = doc.select("#additional-information + p ~ p")
    def companiesSubheading = doc.select("#companies-list")
    def companiesTableHeaders: Elements = doc.select("tables-page > h2 + table > thead > tr > th")
    def companiesTableData: Elements    = doc.select("tables-page > h2 + table > tbody > tr")

  }

  val imgPath            = "src/gov-uk-logo.png/"
  val certificateHeader = "Certificate submission record"
  val paragraph1         =
    "This document records the information submitted to HMRC through the Digital SAO service at the time of submission, including the SAO's name as it appears on the certificate, the declaration made by the SAO or their authorised representative, the certificate type (unqualified or qualified), and the group's tax accounting arrangements."
  val subheadings: Seq[String] =
    Seq("Submission details", "Additional information and explanation", "Companies with qualified certificates", "Companies with unqualified certificates")
  val saoDetailsTableHeaders: Seq[String] = Seq("Senior Accounting Officer", "Email address", "Authorised submitter", "Submission date", "Reference number")
//  val companyDetailsHeaders: Seq[String] =
//    Seq("Company name", "Financial year end date", "Submission date", "Reference number")

  val addInfoParagraph1                   = "Information about your certificate"

  val qualifiedCompaniesPara_A = "In accordance with paragraph 2 of Schedule 46 to the Finance Act 2009, I {submitterName}, on the behalf of, {certificate.saoName} the Senior Accounting Officer, hereby certify that throughout the company's financial year ended 31 December 2024, {length} companies did not have appropriate tax accounting arrangements."
  val qualifiedCompaniesPara_B = "In accordance with paragraph 2 of Schedule 46 to the Finance Act 2009, I {saoName} the Senior Accounting Officer, hereby certify that throughout the company's financial year ended 31 December 2024, {length} companies did not have appropriate tax accounting arrangements."
  val qualCompaniesTableHeaders: Seq[String] = Seq("Company name", "UTR", "Tax regimes", "Additional information")

  val unQualifiedCompaniesPara_A = "In accordance with Paragraph 2 Schedule 46 Finance Act 2009, I {submitterName}, on the behalf of, {saoName} the Senior Accounting Officer hereby certify, in respect of the financial year ended 31 December 2024 that {length} companies had appropriate tax accounting arrangements throughout the year."
  val unQualifiedCompaniesPara_B = "In accordance with Paragraph 2 Schedule 46 Finance Act 2009, I {saoName} the Senior Accounting Officer hereby certify, in respect of the financial year ended 31 December 2024 that {length} companies had appropriate tax accounting arrangements throughout the year."
  val unQualCompaniesTableHeaders: Seq[String] = Seq("Company name", "CRN", "UTR", "Type", "Status", "Financial year end")
}
