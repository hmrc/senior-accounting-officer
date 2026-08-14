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

package uk.gov.hmrc.senioraccountingofficer.services

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{reset, verify, when}
import org.mockito.internal.verification.Times
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.Eventually
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.mockito.MockitoSugar
import org.slf4j.LoggerFactory
import play.api.http.Status
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}
import uk.gov.hmrc.senioraccountingofficer.connectors.EmailConnector
import uk.gov.hmrc.senioraccountingofficer.models.*
import uk.gov.hmrc.senioraccountingofficer.models.dps.Contact

import scala.concurrent.{ExecutionContext, Future}
import scala.jdk.CollectionConverters.*
import scala.util.Try

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

class EmailServiceSpec
    extends AnyFreeSpec
    with Matchers
    with MockitoSugar
    with ScalaFutures
    with Eventually
    with BeforeAndAfterEach {

  given ExecutionContext                 = ExecutionContext.global
  given HeaderCarrier                    = HeaderCarrier()
  val mockEmailConnector: EmailConnector = mock[EmailConnector]
  val emailService: EmailService         = EmailService(mockEmailConnector)

  private val dateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMMM yyyy 'at' hh:mma", Locale.ENGLISH)
  private val testCorrelationId                = "e6b3b05b-1fd7-4b88-8fd5-06e7c9268885"

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockEmailConnector)
  }
  val contacts: List[Contact] = List(Contact(name = "name", email = "email@example.com", "en", "ACTIVE"))

  private def createNotificationEmails(contacts: List[Contact]) = {
    val notificationEmails: List[NotificationEmail] = contacts.map(contact => {
      val parameters = NotificationEmailParameters(
        recipientName = contact.name,
        companyName = "companyName",
        submittedDateTime = "any",
        referenceId = "abc"
      )
      NotificationEmail(
        to = List(contact.email),
        templateId = EmailTemplate.NotificationConfirmation,
        parameters = parameters
      )
    })
    notificationEmails
  }

  private def assertEmail(actualEmail: NotificationEmail, expectedEmail: NotificationEmail): Unit = {

    val actualParams   = actualEmail.parameters
    val expectedParams = expectedEmail.parameters

    actualEmail.to shouldBe expectedEmail.to
    actualEmail.templateId shouldBe EmailTemplate.NotificationConfirmation

    actualParams.recipientName shouldBe expectedParams.recipientName
    actualParams.companyName shouldBe expectedParams.companyName

    Try(LocalDateTime.parse(actualParams.submittedDateTime, dateFormatter)).isSuccess shouldBe true
    actualParams.referenceId shouldBe expectedParams.referenceId
  }

  private def withEmailServiceLogs[A](block: => A): Seq[String] = {
    val emailLogger = LoggerFactory.getLogger(classOf[EmailService]).asInstanceOf[Logger]
    val appender    = ListAppender[ILoggingEvent]()
    appender.start()
    emailLogger.addAppender(appender)

    try {
      block
      eventually {
        appender.list.asScala.toSeq.map(_.getFormattedMessage) should not be empty
      }
      appender.list.asScala.toSeq.map(_.getFormattedMessage)
    } finally {
      emailLogger.detachAppender(appender)
      appender.stop()
    }
  }

  "sendEmail" - {

    "send notification email to no contacts" in {
      when(mockEmailConnector.postEmail(any())(using any())).thenReturn(Future.successful(HttpResponse(202)))
      val result: Unit = emailService.sendNotificationEmail(List(), "companyName", "ABC").futureValue

      verify(mockEmailConnector, Times(0)).postEmail(any[Email])(using any())
      result shouldBe ()
    }
    "send notification email to one contact" in {
      val expectedEmails                = createNotificationEmails(contacts)
      val captor: ArgumentCaptor[Email] = ArgumentCaptor.forClass(classOf[Email])
      when(mockEmailConnector.postEmail(any)(using any)).thenReturn(Future.successful(HttpResponse(202)))
      val result: Unit = emailService.sendNotificationEmail(contacts, "companyName", "abc").futureValue

      verify(mockEmailConnector, Times(1)).postEmail(
        captor.capture()
      )(using any())
      val actualEmail: NotificationEmail = captor.getValue.asInstanceOf[NotificationEmail]
      assertEmail(actualEmail = actualEmail, expectedEmail = expectedEmails(0))
      result shouldBe ()
    }

    "send notification email to two contacts" in {
      val twoContacts                   = contacts ++ List(Contact("name2", "email2@example.com", "en", "ACTIVE"))
      val expectedEmails                = createNotificationEmails(twoContacts)
      val captor: ArgumentCaptor[Email] = ArgumentCaptor.forClass(classOf[Email])

      when(mockEmailConnector.postEmail(any)(using any)).thenReturn(Future.successful(HttpResponse(202)))
      val result: Unit = emailService.sendNotificationEmail(twoContacts, "companyName", "abc").futureValue

      verify(mockEmailConnector, Times(2)).postEmail(captor.capture())(using any())
      val actualEmails = captor.getAllValues.asScala.map(email => email.asInstanceOf[NotificationEmail])
      assertEmail(actualEmails(0), expectedEmails(0))
      assertEmail(actualEmails(1), expectedEmails(1))
      result shouldBe ()
    }

    "log BAD_REQUEST email service responses with the correlation ID" in {
      given HeaderCarrier = HeaderCarrier(extraHeaders = Seq("correlationId" -> testCorrelationId))
      when(mockEmailConnector.postEmail(any)(using any))
        .thenReturn(Future.successful(HttpResponse(Status.BAD_REQUEST, "")))

      val logs = withEmailServiceLogs {
        emailService.sendNotificationEmail(contacts, "companyName", "abc").futureValue
      }

      logs should contain(s"Error from HMRC email service: status=400 [CorrelationId=$testCorrelationId]")
    }

    "log BAD_REQUEST email service responses without a correlation ID" in {
      when(mockEmailConnector.postEmail(any)(using any))
        .thenReturn(Future.successful(HttpResponse(Status.BAD_REQUEST, "")))

      val logs = withEmailServiceLogs {
        emailService.sendNotificationEmail(contacts, "companyName", "abc").futureValue
      }

      logs should contain("Error from HMRC email service: status=400 [CorrelationId=not-provided]")
    }

    "log unexpected email service responses with the correlation ID" in {
      given HeaderCarrier = HeaderCarrier(extraHeaders = Seq("correlationId" -> testCorrelationId))
      when(mockEmailConnector.postEmail(any)(using any))
        .thenReturn(Future.successful(HttpResponse(Status.INTERNAL_SERVER_ERROR, "")))

      val logs = withEmailServiceLogs {
        emailService.sendNotificationEmail(contacts, "companyName", "abc").futureValue
      }

      logs should contain(s"Unexpected response from HMRC email service: status=500 [CorrelationId=$testCorrelationId]")
    }

    "log email connector failures with the correlation ID" in {
      given HeaderCarrier = HeaderCarrier(extraHeaders = Seq("correlationId" -> testCorrelationId))
      when(mockEmailConnector.postEmail(any)(using any))
        .thenReturn(Future.failed(RuntimeException("email unavailable")))

      val logs = withEmailServiceLogs {
        emailService
          .sendCertificateEmail(
            emailTemplate = EmailTemplate.CertificateConfirmationSAO,
            email = "email@example.com",
            companyName = "companyName",
            referenceId = "abc",
            submitterName = None,
            saoName = "name"
          )
          .futureValue
      }

      logs should contain(
        s"Unable to send certificate confirmation email: RuntimeException [CorrelationId=$testCorrelationId]"
      )
    }
  }
}
