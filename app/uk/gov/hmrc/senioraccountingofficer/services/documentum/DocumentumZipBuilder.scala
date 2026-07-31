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

import org.apache.pekko.NotUsed
import org.apache.pekko.stream.IOResult
import org.apache.pekko.stream.Materializer
import org.apache.pekko.stream.scaladsl.{Sink, Source, StreamConverters}
import org.apache.pekko.util.ByteString

import scala.concurrent.{ExecutionContext, Future}

import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.util.zip.{ZipEntry, ZipOutputStream}
import javax.inject.Inject

class DocumentumZipBuilder @Inject() ()(using Materializer, ExecutionContext) {

  def build(
      pdfSource: Source[ByteString, ?],
      pdfFileName: String,
      metadataXml: String,
      metadataFileName: String
  ): Future[Source[ByteString, NotUsed]] =
    pdfSource.runWith(Sink.fold(ByteString.empty)(_ ++ _)).map { pdfBytes =>
      val outputStream = new ByteArrayOutputStream()
      val zipStream    = new ZipOutputStream(outputStream)

      try {
        addEntry(zipStream, pdfFileName, pdfBytes.toArray)
        addEntry(zipStream, metadataFileName, metadataXml.getBytes(StandardCharsets.UTF_8))
      } finally {
        zipStream.close()
      }

      Source.single(ByteString(outputStream.toByteArray))
    }

  def resourceSource(resourcePath: String): Source[ByteString, Future[IOResult]] =
    StreamConverters.fromInputStream(() =>
      Option(getClass.getClassLoader.getResourceAsStream(resourcePath))
        .getOrElse(throw new IllegalStateException(s"Resource not found: $resourcePath"))
    )

  private def addEntry(zipStream: ZipOutputStream, fileName: String, content: Array[Byte]): Unit = {
    zipStream.putNextEntry(new ZipEntry(fileName))
    zipStream.write(content)
    zipStream.closeEntry()
  }
}
