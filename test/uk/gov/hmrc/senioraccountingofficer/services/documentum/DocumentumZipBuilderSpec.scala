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

import org.apache.pekko.stream.Materializer
import org.apache.pekko.stream.scaladsl.{Sink, Source}
import org.apache.pekko.util.ByteString
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite

import scala.util.Using

import java.io.{ByteArrayInputStream, ByteArrayOutputStream}
import java.nio.charset.StandardCharsets
import java.util.zip.ZipInputStream

class DocumentumZipBuilderSpec extends AnyWordSpec with Matchers with ScalaFutures with GuiceOneAppPerSuite {

  private lazy given Materializer = app.injector.instanceOf[Materializer]

  private lazy val zipBuilder = app.injector.instanceOf[DocumentumZipBuilder]

  "build" must {
    "create a zip containing the PDF and metadata XML entries" in {
      val source = Source.single(ByteString("pdf-content"))

      val zipSource = zipBuilder.build(source, "submission.pdf", "<metadata/>", "metadata.xml")

      val zipBytes = zipSource.runWith(Sink.fold(ByteString.empty)(_ ++ _)).futureValue
      val entries  = unzip(zipBytes.toArray)
      val entryMap = entries.map((fileName, content) => fileName -> content).toMap

      entries.map(_._1) mustBe List("submission.pdf", "metadata.xml")
      entryMap("submission.pdf") mustBe "pdf-content"
      entryMap("metadata.xml") mustBe "<metadata/>"
    }

    "stream multiple PDF chunks into a single PDF zip entry" in {
      val source = Source(List(ByteString("pdf-"), ByteString("content")))

      val zipSource = zipBuilder.build(source, "submission.pdf", "<metadata/>", "metadata.xml")

      val zipBytes = zipSource.runWith(Sink.fold(ByteString.empty)(_ ++ _)).futureValue
      val entries  = unzip(zipBytes.toArray)
      val entryMap = entries.map((fileName, content) => fileName -> content).toMap

      entries.map(_._1) mustBe List("submission.pdf", "metadata.xml")
      entryMap("submission.pdf") mustBe "pdf-content"
      entryMap("metadata.xml") mustBe "<metadata/>"
    }

    "load the blank PDF resource as a Pekko stream" in {
      val pdfBytes = zipBuilder
        .resourceSource("documentum/blank-submission.pdf")
        .runWith(Sink.fold(ByteString.empty)(_ ++ _))
        .futureValue

      pdfBytes.utf8String must startWith("%PDF-1.4")
    }
  }

  private def unzip(bytes: Array[Byte]): List[(String, String)] =
    Using.resource(new ZipInputStream(new ByteArrayInputStream(bytes))) { zipInputStream =>
      Iterator
        .continually(Option(zipInputStream.getNextEntry))
        .takeWhile(_.isDefined)
        .flatten
        .map { entry =>
          val outputStream = new ByteArrayOutputStream()
          zipInputStream.transferTo(outputStream)
          zipInputStream.closeEntry()
          entry.getName -> outputStream.toString(StandardCharsets.UTF_8)
        }
        .toList
    }
}
