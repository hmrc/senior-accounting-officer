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

import uk.gov.hmrc.senioraccountingofficer.models.documentum.{DocumentumPackageContext, SubmissionType}

import scala.xml.{Elem, PrettyPrinter}

import javax.inject.Inject

class DocumentumMetadataXmlGenerator @Inject() () {

  def generate(context: DocumentumPackageContext, documentTitle: String, reconciliationId: String): String = {
    val nominatedCompany       = context.companies.headOption
    val referenceAttributeName = context.submissionType match {
      case SubmissionType.Certificate  => "hm_certificate_ref"
      case SubmissionType.Notification => "hm_notification_ref"
    }

    val xml: Elem =
      <documents xmlns="http://govtalk.gov.uk/hmrc/bit/content/1">
        <document>
          <header>
            <title>{documentTitle}</title>
            <format>pdf</format>
            <mime_type>application/pdf</mime_type>
            <store>true</store>
            <source>DSAO</source>
            <target>CRMM</target>
            <reconciliation_id>{reconciliationId}</reconciliation_id>
          </header>
          <metadata>
            {attribute("hm_unique_doc_id", documentTitle)}
            {attribute("hm_customer_id", context.customerId.getOrElse("DSAO"))}
            {attribute(referenceAttributeName, s"SAO${context.submissionId}")}
            {attribute("hm_utr", nominatedCompany.map(_.utr).getOrElse(""))}
            {attribute("hm_crn", nominatedCompany.flatMap(_.crn).getOrElse(""))}
          </metadata>
        </document>
      </documents>

    """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""" + System.lineSeparator() +
      new PrettyPrinter(120, 2).format(xml)
  }

  private def attribute(name: String, value: String) =
    <attribute>
      <attribute_name>{name}</attribute_name>
      <attribute_type>string</attribute_type>
      <attribute_values>
        <attribute_value>{value}</attribute_value>
      </attribute_values>
    </attribute>
}
