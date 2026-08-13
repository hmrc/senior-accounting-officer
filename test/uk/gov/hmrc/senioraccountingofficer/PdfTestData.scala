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

package uk.gov.hmrc.senioraccountingofficer

import org.apache.pekko.stream.Materializer
import uk.gov.hmrc.domain.SaUtrGenerator
import uk.gov.hmrc.senioraccountingofficer.models.requests.{CompanyStatus, CompanyType}
import uk.gov.hmrc.senioraccountingofficer.services.PdfService.*

import scala.annotation.tailrec
import scala.collection.mutable.ListBuffer
import scala.concurrent.ExecutionContext
import scala.util.Random

object PdfTestData {
  private def generateCrn = {
    val num = Random.nextInt(1000000)
    f"$num%08d"
  }

  private def generateUtr = {
    val seed = Random.nextInt(1000000)
    SaUtrGenerator(seed).nextSaUtr.toString
  }
  private val testCompanySeeds: Seq[Certificate.Row] = Seq(
    Certificate.Row(
      companyName =
        "TEST NAME OF THE COMPANY WITH THE LONGEST NAME SO FAR INCORPORATED AT THE REGISTRY OF COMPANIES IN ENGLAND AND WALES AND ENCOMPASSING THE REGISTRIES BASED IN SCOTLAND $index",
      utr = generateUtr,
      crn = generateCrn,
      companyType = CompanyType.LTD,
      status = CompanyStatus.Administration,
      financialYearEndDate = "31 Jan 2025",
      qualifiedRegimes = TaxRegimes(
        corporationTax = true,
        vat = true,
        paye = true,
        insurancePremiumTax = true,
        stampDutyLandTax = true,
        stampDutyReserveTax = true,
        petroleumRevenueTax = true,
        customsDuties = true,
        exciseDuties = true,
        bankLevy = true
      )
    ),
    Certificate.Row(
      companyName = "Test Company $index",
      utr = generateUtr,
      crn = generateCrn,
      companyType = CompanyType.PLC,
      CompanyStatus.Active,
      financialYearEndDate = "31 Jan 2025",
      qualifiedRegimes = TaxRegimes(
        corporationTax = true,
        vat = true,
        paye = true
      )
    ),
    Certificate.Row(
      companyName = "Test Halcyon Merchants International $index",
      utr = generateUtr,
      crn = generateCrn,
      companyType = CompanyType.PLC,
      CompanyStatus.Active,
      financialYearEndDate = "31 Mar 2025"
    ),
    Certificate.Row(
      companyName = "Test Pinnacle Freight and Forwarding Solutions $index",
      utr = generateUtr,
      crn = generateCrn,
      companyType = CompanyType.PLC,
      status = CompanyStatus.Administration,
      financialYearEndDate = "31 Mar 2025"
    ),
    Certificate.Row(
      companyName = "Test Arkwright and Co $index",
      utr = generateUtr,
      crn = generateCrn,
      companyType = CompanyType.PLC,
      CompanyStatus.Active,
      financialYearEndDate = "31 Mar 2025",
      qualifiedRegimes = TaxRegimes(
        vat = true
      )
    ),
    Certificate.Row(
      companyName = "Test Vortex Supply Co $index",
      utr = generateUtr,
      crn = generateCrn,
      companyType = CompanyType.LTD,
      CompanyStatus.Active,
      financialYearEndDate = "31 Mar 2025"
    ),
    Certificate.Row(
      companyName = "Test Nexora Trading $index",
      utr = generateUtr,
      crn = generateCrn,
      companyType = CompanyType.LTD,
      CompanyStatus.Active,
      financialYearEndDate = "31 Mar 2025"
    ),
    Certificate.Row(
      companyName = "Test Caldwell Imports and Distribution Partners $index",
      utr = generateUtr,
      crn = generateCrn,
      companyType = CompanyType.PLC,
      CompanyStatus.Active,
      financialYearEndDate = "31 Mar 2025"
    ),
    Certificate.Row(
      companyName = "Test Stratos Ventures $index",
      utr = generateUtr,
      crn = generateCrn,
      companyType = CompanyType.LTD,
      status = CompanyStatus.Dormant,
      financialYearEndDate = "31 Mar 2025"
    ),
    Certificate.Row(
      companyName = "Test Ironclad Exports $index",
      utr = generateUtr,
      crn = generateCrn,
      companyType = CompanyType.LTD,
      CompanyStatus.Active,
      financialYearEndDate = "31 Mar 2025"
    ),
    Certificate.Row(
      companyName = "Test Luminary Goods and Global Trade Services $index",
      utr = generateUtr,
      crn = generateCrn,
      companyType = CompanyType.LTD,
      status = CompanyStatus.Administration,
      financialYearEndDate = "31 Mar 2025"
    ),
    Certificate.Row(
      companyName = "Test Tesseract Cargo $index",
      utr = generateUtr,
      crn = generateCrn,
      companyType = CompanyType.LTD,
      CompanyStatus.Active,
      financialYearEndDate = "31 Mar 2025"
    ),
    Certificate.Row(
      companyName = "Test Drift and Sons $index",
      utr = generateUtr,
      crn = generateCrn,
      companyType = CompanyType.LTD,
      status = CompanyStatus.Dormant,
      financialYearEndDate = "31 Mar 2025"
    ),
    Certificate.Row(
      companyName = "Test Orizon Distributers $index",
      utr = generateUtr,
      crn = generateCrn,
      companyType = CompanyType.PLC,
      CompanyStatus.Active,
      financialYearEndDate = "31 Mar 2025"
    ),
    Certificate.Row(
      companyName = "Test Meridian Haulers International Freight $index",
      utr = generateUtr,
      crn = generateCrn,
      companyType = CompanyType.LTD,
      CompanyStatus.Active,
      financialYearEndDate = "31 Mar 2025"
    ),
    Certificate.Row(
      companyName = "Test Cobalt Solutions $index",
      utr = generateUtr,
      crn = generateCrn,
      companyType = CompanyType.LTD,
      status = CompanyStatus.Administration,
      financialYearEndDate = "31 Mar 2025"
    ),
    Certificate.Row(
      companyName = "Test Farpoint Trading $index",
      utr = generateUtr,
      crn = generateCrn,
      companyType = CompanyType.LTD,
      CompanyStatus.Active,
      financialYearEndDate = "31 Mar 2025"
    ),
    Certificate.Row(
      companyName = "Test Verity Logistics and Supply Chain Management $index",
      utr = generateUtr,
      crn = generateCrn,
      companyType = CompanyType.PLC,
      CompanyStatus.Active,
      financialYearEndDate = "31 Mar 2025"
    ),
    Certificate.Row(
      companyName = "Test Quantum Carriers $index",
      utr = generateUtr,
      crn = generateCrn,
      companyType = CompanyType.LTD,
      status = CompanyStatus.Dormant,
      financialYearEndDate = "31 Mar 2025"
    ),
    Certificate.Row(
      companyName = "Test Solace Freight $index",
      utr = generateUtr,
      crn = generateCrn,
      companyType = CompanyType.LTD,
      CompanyStatus.Active,
      financialYearEndDate = "31 Mar 2025"
    ),
    Certificate.Row(
      companyName = "Test Templar Supplies and Procurement Solutions $index",
      utr = generateUtr,
      crn = generateCrn,
      companyType = CompanyType.LTD,
      status = CompanyStatus.Liquidation,
      financialYearEndDate = "31 Mar 2025"
    ),
    Certificate.Row(
      companyName = "Test Echelon Brokers $index",
      utr = generateUtr,
      crn = generateCrn,
      companyType = CompanyType.LTD,
      CompanyStatus.Active,
      financialYearEndDate = "31 Mar 2025"
    ),
    Certificate.Row(
      companyName = "Test Silverline Cargo $index",
      utr = generateUtr,
      crn = generateCrn,
      companyType = CompanyType.LTD,
      CompanyStatus.Active,
      financialYearEndDate = "31 Mar 2025"
    ),
    Certificate.Row(
      companyName = "Test Wavecrest Imports $index",
      utr = generateUtr,
      crn = generateCrn,
      companyType = CompanyType.LTD,
      status = CompanyStatus.Dormant,
      financialYearEndDate = "31 Mar 2025"
    ),
    Certificate.Row(
      companyName = "Test Crestview Partners and Associated Trading $index",
      utr = generateUtr,
      crn = generateCrn,
      companyType = CompanyType.PLC,
      CompanyStatus.Active,
      financialYearEndDate = "31 Mar 2025"
    ),
    Certificate.Row(
      companyName = "Test Novaline Exports $index",
      utr = generateUtr,
      crn = generateCrn,
      companyType = CompanyType.LTD,
      CompanyStatus.Active,
      financialYearEndDate = "31 Mar 2025"
    ),
    Certificate.Row(
      companyName = "Test Tangent Wholesale $index",
      utr = generateUtr,
      crn = generateCrn,
      companyType = CompanyType.LTD,
      status = CompanyStatus.Liquidation,
      financialYearEndDate = "31 Mar 2025"
    ),
    Certificate.Row(
      companyName = "Test Fieldstone Commerce and Overseas Distribution $index",
      utr = generateUtr,
      crn = generateCrn,
      companyType = CompanyType.LTD,
      CompanyStatus.Active,
      financialYearEndDate = "31 Mar 2025"
    ),
    Certificate.Row(
      companyName = "Test Auris Distribution $index",
      utr = generateUtr,
      crn = generateCrn,
      companyType = CompanyType.LTD,
      CompanyStatus.Active,
      financialYearEndDate = "31 Mar 2025"
    ),
    Certificate.Row(
      companyName = "Test Stellarex Holdings $index",
      utr = generateUtr,
      crn = generateCrn,
      companyType = CompanyType.LTD,
      status = CompanyStatus.Dormant,
      financialYearEndDate = "31 Mar 2025"
    ),
    Certificate.Row(
      companyName = "Test Ravenport Traders and International Brokers $index",
      utr = generateUtr,
      crn = generateCrn,
      companyType = CompanyType.LTD,
      CompanyStatus.Active,
      financialYearEndDate = "31 Mar 2025"
    ),
    Certificate.Row(
      companyName = "Test Ironveil Ventures $index",
      utr = generateUtr,
      crn = generateCrn,
      companyType = CompanyType.PLC,
      CompanyStatus.Active,
      financialYearEndDate = "31 Mar 2025"
    )
  )

  private def genSeq[A](total: Int, generatorFunction: Int => A): Seq[A] = {
    val lb = ListBuffer[A]()
    @tailrec
    def loop(total: Int, counter: Int = 1): Unit = {
      if counter <= total then
        lb.append(generatorFunction(counter))
        loop(total, counter + 1)
      else ()
    }
    loop(total)
    lb.toSeq
  }

  def genNotificationTestCompanies(total: Int): Seq[Notification.Row] = {
    def getTestCompany(index: Int): Notification.Row = {
      val base = testCompanySeeds(index % testCompanySeeds.length)
      base.toNotificationRow(index)
    }
    genSeq(total, getTestCompany)
  }

  def genCertificateTestCompanies(
      total: Int,
      additionalInformation: Option[String] = None
  ): Seq[Certificate.Row] = {
    def getTestCompany(index: Int): Certificate.Row = {
      val base = testCompanySeeds(index % testCompanySeeds.length)
      base.copy(
        companyName = base.companyName.replace("$index", index.toString),
        qualifiedRegimes =
          if index < 100 then base.qualifiedRegimes else TaxRegimes(),
        additionalInformation = additionalInformation
      )
    }
    genSeq(total, getTestCompany)
  }

  def testNotificationData(rows: Int, additionalInformation: Option[String])(using
      Materializer,
      ExecutionContext
  ): Notification = {
    Notification(
      companyName = "Test ABC Limited",
      submissionDate = "12 May 2025",
      submissionId = "XMPLR0123456789",
      saoHistory = List(
        SaoTenure(name = "Fake Jackson Brown", startDate = Some("01 June 2024")),
        SaoTenure(name = "Fake Ashley Ross", startDate = Some("01 January 2024"), endDate = Some("31 May 20204"))
      ),
      companies = genNotificationTestCompanies(rows),
      additionalInformation = additionalInformation
    )
  }

  def testCertificateData(rows: Int, submitterName: Option[String], additionalInfo: Option[String])(using
      Materializer,
      ExecutionContext
  ): Certificate = {
    Certificate(
      saoName = "Test Jackson Brown",
      saoEmail = "jbrown@test.co.uk",
      submitterName = submitterName,
      submissionDate = "12 May 2025",
      submissionId = "XMPLR0123456789",
      companies = genCertificateTestCompanies(rows, additionalInfo),
      additionalInformation = additionalInfo
    )
  }

}
object AdditionalInformationGenerator {

  val lorumIpsumBlock =
    "Lorem ipsum dolor sit amet, consectetur adipisicing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua. Ut enim ad minim veniam, quis nostrud exercitation ullamco laboris nisi ut aliquip ex ea commodo consequat. Duis aute irure dolor in reprehenderit in voluptate velit esse cillum dolore eu fugiat nulla pariatur. Excepteur sint occaecat cupidatat non proident, sunt in culpa qui officia deserunt mollit anim id est laborum.\n"

  def generate(totalBytes: Long, repeat: Int): String = {
    val byteArray = lorumIpsumBlock.getBytes("utf-8")
    val offset    = byteArray.slice(0, (totalBytes % byteArray.length).toInt)
    val sb        = StringBuilder()

    @tailrec
    def loop(total: Long, counter: Long = 0): Unit = {
      if counter < total then
        sb.append(lorumIpsumBlock)
        loop(total, counter + 1)
      else ()
    }

    loop(repeat)

    sb.append(String(offset))
    sb.mkString
  }
}
