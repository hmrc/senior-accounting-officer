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

package uk.gov.hmrc.senioraccountingofficer.utils

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder

import java.io.File

class OpenHtmlToPdfService {
  def builderFor(content: String): PdfRendererBuilder = {

    val builder = new PdfRendererBuilder
    builder.withProducer("HMRC forms services")

    // 1. Enable PDF/UA-1 conformance and tagging
    // Core Conformance Levels
    //
    //    Level a (Accessible): Full compliance, including document structure and tags for accessibility.
    //    Level b (Basic): Ensures visual integrity (appearance) over the long term.
    //    Level u (Unicode): Requires text to be mapped to Unicode, ensuring reliable searching and copying.
    //
    // PDF/A Versions
    //
    //    PDF/A-1: Based on PDF 1.4; defines minimum requirements for archival.
    //    PDF/A-2: Based on PDF 1.7; supports JPEG2000, transparency, and PDF/A file embedding.
    //    PDF/A-3: Allows embedding arbitrary file formats (e.g., XML, CSV).
    //    PDF/A-4: Aligns with PDF 2.0; includes support for interactive forms and annotations.
    builder.usePdfAConformance(PdfRendererBuilder.PdfAConformance.PDFA_3_A)
    builder.usePdfUaAccessibility(true)

    builder.useFont(
      File(this.getClass.getResource("/pdf/fonts/Helvetica.ttf").getFile),
      "Helvetica"
    )

    // 2. Point to your source (file, URI, or string)
    builder.withHtmlContent(
      content,
      this.getClass.getResource("/pdf/").toURI.toString
    )

    builder
  }
}
