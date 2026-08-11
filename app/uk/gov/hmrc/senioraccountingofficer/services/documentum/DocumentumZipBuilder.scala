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
import org.apache.pekko.stream.*
import org.apache.pekko.stream.connectors.file.ArchiveMetadata
import org.apache.pekko.stream.connectors.file.scaladsl.Archive
import org.apache.pekko.stream.scaladsl.{Source, StreamConverters}
import org.apache.pekko.util.ByteString

import scala.concurrent.Future

import java.nio.charset.StandardCharsets
import javax.inject.Inject

class DocumentumZipBuilder @Inject() () {

  def build(
      pdfSource: Source[ByteString, ?],
      pdfFileName: String,
      metadataXml: String,
      metadataFileName: String
  ): Source[ByteString, NotUsed] = {
    val pdf: Source[ByteString, NotUsed] = pdfSource.mapMaterializedValue(_ => NotUsed)
    val xml: Source[ByteString, NotUsed] = Source.single(ByteString(metadataXml, StandardCharsets.UTF_8.name()))

    val files: Source[(ArchiveMetadata, Source[ByteString, NotUsed]), NotUsed] =
      Source(
        List(
          ArchiveMetadata(pdfFileName)      -> pdf,
          ArchiveMetadata(metadataFileName) -> xml
        )
      )

    files.via(Archive.zip())
  }

  def resourceSource(resourcePath: String): Source[ByteString, Future[IOResult]] =
    StreamConverters.fromInputStream(() =>
      Option(getClass.getClassLoader.getResourceAsStream(resourcePath))
        .getOrElse(throw new IllegalStateException(s"Resource not found: $resourcePath"))
    )
}
