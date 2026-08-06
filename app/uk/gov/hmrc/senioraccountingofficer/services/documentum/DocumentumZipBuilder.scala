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
import org.apache.pekko.stream.scaladsl.{Source, SourceQueueWithComplete, StreamConverters}
import org.apache.pekko.util.ByteString

import scala.concurrent.*
import scala.concurrent.duration.DurationInt
import scala.util.{Failure, Success, Using}

import java.io.{IOException, OutputStream}
import java.nio.charset.StandardCharsets
import java.util.zip.{ZipEntry, ZipOutputStream}
import javax.inject.Inject

class DocumentumZipBuilder @Inject() ()(using Materializer, ExecutionContext) {

  private val blockingEc =
    summon[Materializer].system.dispatchers.lookup(
      "pekko.stream.materializer.blocking-io-dispatcher"
    )

  def build(
      pdfSource: Source[ByteString, ?],
      pdfFileName: String,
      metadataXml: String,
      metadataFileName: String
  ): Source[ByteString, NotUsed] =

    Source
      .queue[ByteString](bufferSize = 16, OverflowStrategy.backpressure)
      .mapMaterializedValue { queue =>
        Future {
          blocking {
            val zipStream = new ZipOutputStream(new QueueOutputStream(queue))

            writeZip(
              pdfSource,
              pdfFileName,
              metadataXml,
              metadataFileName,
              zipStream
            )
          }
        }(blockingEc).onComplete {
          case Success(_)         => queue.complete()
          case Failure(exception) => queue.fail(exception)
        }

        NotUsed
      }

  def resourceSource(resourcePath: String): Source[ByteString, Future[IOResult]] =
    StreamConverters.fromInputStream(() =>
      Option(getClass.getClassLoader.getResourceAsStream(resourcePath))
        .getOrElse(throw new IllegalStateException(s"Resource not found: $resourcePath"))
    )

  private def writeZip(
      pdfSource: Source[ByteString, ?],
      pdfFileName: String,
      metadataXml: String,
      metadataFileName: String,
      zipStream: ZipOutputStream
  ): Unit = {
    Using.Manager { managed =>
      val zip            = managed(zipStream)
      val pdfInputStream = managed(pdfSource.runWith(StreamConverters.asInputStream(5.seconds)))

      addEntry(zip, pdfFileName) {
        pdfInputStream.transferTo(zip)
      }

      addEntry(zip, metadataFileName) {
        zip.write(metadataXml.getBytes(StandardCharsets.UTF_8))
      }
    } match {
      case Success(_)         => ()
      case Failure(exception) => throw exception
    }
  }

  private def addEntry(zipStream: ZipOutputStream, fileName: String)(writeContent: => Unit): Unit = {
    zipStream.putNextEntry(new ZipEntry(fileName))
    try writeContent
    finally zipStream.closeEntry()
  }

  private class QueueOutputStream(queue: SourceQueueWithComplete[ByteString]) extends OutputStream {
    private val QueueOfferTimeout = 30.seconds

    override def write(byte: Int): Unit =
      offer(ByteString((byte & 0xff).toByte))

    override def write(bytes: Array[Byte], offset: Int, length: Int): Unit =
      if length > 0 then offer(ByteString.fromArray(bytes, offset, length))

    private def offer(bytes: ByteString): Unit =
      Await.result(queue.offer(bytes), QueueOfferTimeout) match {
        case QueueOfferResult.Enqueued    => ()
        case QueueOfferResult.Dropped     => throw new IOException("Zip stream chunk dropped")
        case QueueOfferResult.QueueClosed => throw new IOException("Zip stream queue closed")
        case QueueOfferResult.Failure(ex) => throw ex
      }
  }
}
