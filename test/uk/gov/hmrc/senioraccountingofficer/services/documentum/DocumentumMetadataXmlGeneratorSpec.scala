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

package uk.gov.hmrc.senioraccountingofficer.services.documentum

import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec
import uk.gov.hmrc.senioraccountingofficer.models.documentum.{
  DocumentumCompany,
  DocumentumPackageContext,
  SubmissionType
}

import scala.xml.{Elem, XML}

import java.io.StringReader
import javax.xml.XMLConstants
import javax.xml.transform.stream.StreamSource
import javax.xml.validation.SchemaFactory

class DocumentumMetadataXmlGeneratorSpec extends AnyWordSpec with Matchers {

  private val generator = new DocumentumMetadataXmlGenerator()

  "generate" must {
    "generate schema-valid metadata XML for a notification" in {
      val xml = generator.generate(
        notificationContext,
        "20260728_NOT0123456789_SAO_Notification_OFFICIAL_SENSITIVE",
        "20260728_NOT0123456789_SAO_Notification_OFFICIAL_SENSITIVE-20260728120000"
      )

      validateAgainstSchema(xml)

      val parsed = XML.loadString(xml)
      parsed.namespace mustBe "http://govtalk.gov.uk/hmrc/bit/content/1"
      (parsed \\ "title").text mustBe "20260728_NOT0123456789_SAO_Notification_OFFICIAL_SENSITIVE"
      (parsed \\ "format").text mustBe "pdf"
      (parsed \\ "mime_type").text mustBe "application/pdf"
      (parsed \\ "source").text mustBe "DSAO"
      (parsed \\ "target").text mustBe "CRMM"
      (parsed \\ "reconciliation_id").text mustBe
        "20260728_NOT0123456789_SAO_Notification_OFFICIAL_SENSITIVE-20260728120000"
      attributeValue(parsed, "hm_unique_doc_id") mustBe "20260728_NOT0123456789_SAO_Notification_OFFICIAL_SENSITIVE"
      attributeValue(parsed, "hm_customer_id") mustBe "DSAO"
      attributeValue(parsed, "hm_notification_ref") mustBe "SAONOT0123456789"
      attributeValue(parsed, "hm_utr") mustBe "1234567890"
      attributeValue(parsed, "hm_crn") mustBe "AB123456"
    }

    "include certificate customer ID when present" in {
      val xml = generator.generate(
        certificateContext,
        "20260728_CRT0001234567_SAO_Certificate_OFFICIAL_SENSITIVE",
        "20260728_CRT0001234567_SAO_Certificate_OFFICIAL_SENSITIVE-20260728120000"
      )

      validateAgainstSchema(xml)

      val parsed = XML.loadString(xml)
      attributeValue(parsed, "hm_customer_id") mustBe "customer-1"
      attributeValue(parsed, "hm_certificate_ref") mustBe "SAOCRT0001234567"
    }
  }

  private def validateAgainstSchema(xml: String): Unit = {
    val schemaUrl = getClass.getClassLoader.getResource("documentum/documentum-metadata.xsd")
    val schema    = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI).newSchema(schemaUrl)
    schema.newValidator().validate(new StreamSource(new StringReader(xml)))
  }

  private def attributeValue(xml: Elem, attributeName: String): String =
    (xml \\ "attribute")
      .find(attribute => (attribute \ "attribute_name").text == attributeName)
      .map(attribute => (attribute \\ "attribute_value").text)
      .getOrElse(fail(s"Attribute not found: $attributeName"))

  private val notificationContext =
    DocumentumPackageContext(
      submissionId = "SAONOT0123456789",
      submissionType = SubmissionType.Notification,
      saoSubscriptionId = "XASAO1234567890",
      nominatedCompany = DocumentumCompany(utr = "1234567890", name = "Test Ltd", crn = Some("AB123456")),
      customerId = None
    )

  private val certificateContext =
    DocumentumPackageContext(
      submissionId = "SAOCRT0001234567",
      submissionType = SubmissionType.Certificate,
      saoSubscriptionId = "XASAO1234567890",
      nominatedCompany = DocumentumCompany(utr = "1234567890", name = "Test Ltd", crn = Some("AB123456")),
      customerId = Some("customer-1")
    )
}
