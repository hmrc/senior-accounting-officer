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

import java.time.*
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
  val emailService: EmailService = EmailService(mockEmailConnector, Clock.systemDefaultZone.withZone(ZoneOffset.UTC))

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

  private def assertNotificationEmail(actualEmail: NotificationEmail, expectedEmail: NotificationEmail): Unit = {

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
      val result: Unit = emailService.sendNotificationEmail(Nil, "companyName", "ABC").futureValue

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
      assertNotificationEmail(actualEmail = actualEmail, expectedEmail = expectedEmails(0))
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
      assertNotificationEmail(actualEmails(0), expectedEmails(0))
      assertNotificationEmail(actualEmails(1), expectedEmails(1))
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
          .sendSaoCertificateEmail(
            email = "email@example.com",
            referenceId = "abc",
            saoName = "name"
          )
          .futureValue
      }

      logs should contain(
        s"Unable to send certificate confirmation email: RuntimeException [CorrelationId=$testCorrelationId]"
      )
    }

    "send notification email with a date in BST" in {
      val bstDateTime = ZonedDateTime.of(2026, 9, 21, 12, 1, 0, 0, ZoneId.of("UTC"))

      val emailService: EmailService =
        EmailService(
          mockEmailConnector,
          Clock.fixed(bstDateTime.toInstant(), ZoneId.of("UTC"))
        )

      val expectedEmails                = createNotificationEmails(contacts)
      val captor: ArgumentCaptor[Email] = ArgumentCaptor.forClass(classOf[Email])
      when(mockEmailConnector.postEmail(any)(using any)).thenReturn(Future.successful(HttpResponse(202)))
      val result: Unit = emailService.sendNotificationEmail(contacts, "companyName", "abc").futureValue

      verify(mockEmailConnector, Times(1)).postEmail(
        captor.capture()
      )(using any())
      val actualEmail: NotificationEmail = captor.getValue.asInstanceOf[NotificationEmail]

      val expectedEmail = expectedEmails.head

      val actualParams   = actualEmail.parameters
      val expectedParams = expectedEmail.parameters

      actualEmail.to shouldBe expectedEmail.to
      actualEmail.templateId shouldBe EmailTemplate.NotificationConfirmation

      actualParams.recipientName shouldBe expectedParams.recipientName
      actualParams.companyName shouldBe expectedParams.companyName

      val actualDateTry = Try(LocalDateTime.parse(actualParams.submittedDateTime, dateFormatter))

      actualDateTry.isSuccess shouldBe true

      val actualDate = actualDateTry.get

      withClue("hour must be 13 instead of 12 here, as the time should be represented in BST\n") {
        actualDate.getHour() shouldBe 13
      }

      actualParams.referenceId shouldBe expectedParams.referenceId

      result shouldBe ()
    }

    "send notification email with a date in GMT" in {
      val gmtDateTime = ZonedDateTime.of(2026, 3, 21, 12, 1, 0, 0, ZoneId.of("UTC"))

      val emailService: EmailService =
        EmailService(
          mockEmailConnector,
          Clock.fixed(gmtDateTime.toInstant(), ZoneId.of("UTC"))
        )

      val expectedEmails                = createNotificationEmails(contacts)
      val captor: ArgumentCaptor[Email] = ArgumentCaptor.forClass(classOf[Email])
      when(mockEmailConnector.postEmail(any)(using any)).thenReturn(Future.successful(HttpResponse(202)))
      val result: Unit = emailService.sendNotificationEmail(contacts, "companyName", "abc").futureValue

      verify(mockEmailConnector, Times(1)).postEmail(
        captor.capture()
      )(using any())
      val actualEmail: NotificationEmail = captor.getValue.asInstanceOf[NotificationEmail]

      val expectedEmail = expectedEmails.head

      val actualParams   = actualEmail.parameters
      val expectedParams = expectedEmail.parameters

      actualEmail.to shouldBe expectedEmail.to
      actualEmail.templateId shouldBe EmailTemplate.NotificationConfirmation

      actualParams.recipientName shouldBe expectedParams.recipientName
      actualParams.companyName shouldBe expectedParams.companyName

      val actualDateTry = Try(LocalDateTime.parse(actualParams.submittedDateTime, dateFormatter))

      actualDateTry.isSuccess shouldBe true

      val actualDate = actualDateTry.get

      withClue("hour must be 12 here, as the time should be represented in GMT, which is the same as UTC\n") {
        actualDate.getHour() shouldBe 12
      }

      actualParams.referenceId shouldBe expectedParams.referenceId

      result shouldBe ()
    }

    "send submitter certificate email confirmation to one contact" in {
      when(mockEmailConnector.postEmail(any())(using any))
        .thenReturn(Future.successful(HttpResponse(202)))

      val captor: ArgumentCaptor[Email] = ArgumentCaptor.forClass(classOf[Email])

      val expectedContactEmails = contacts.map { contact =>
        val emailParams = SubmitterCertificateEmailParameters(
          contactName = contact.name,
          submitterName = Some("submitterName"),
          saoName = "saoName",
          submittedDateTime = "any",
          referenceId = "certRef"
        )
        SubmitterCertificateEmail(to = List(contact.email), parameters = emailParams)
      }
      expectedContactEmails.foreach { contactEmail =>
        val result: Unit = emailService
          .sendSubmitterCertificateEmail(
            email = contactEmail.to.head,
            contactName = contactEmail.parameters.contactName,
            referenceId = "certRef",
            submitterName = "submitterName",
            saoName = "saoName"
          )
          .futureValue
        result shouldBe ()
      }

      expectedContactEmails.foreach { expectedContactEmail =>
        verify(mockEmailConnector, Times(1)).postEmail(captor.capture())(using any)
        val actualEmail = captor.getValue.asInstanceOf[SubmitterCertificateEmail]
        actualEmail.copy(parameters =
          actualEmail.parameters.copy(submittedDateTime = "any")
        ) shouldBe expectedContactEmail
      }
    }

    "send submitter email certificate confirmation to SAO" in {
      when(mockEmailConnector.postEmail(any)(using any)).thenReturn(Future.successful(HttpResponse(202)))
      val captor: ArgumentCaptor[Email] = ArgumentCaptor.forClass(classOf[Email])

      val expectedSaoEmail = Some(
        SubmitterSaoCertificateEmail(
          to = List("sao@example.com"),
          parameters = SubmitterSaoCertificateEmailParameters(
            saoName = "saoName",
            submitterName = "submitterName",
            submittedDateTime = "any",
            referenceId = "certRef"
          )
        )
      )

      expectedSaoEmail.foreach { sao =>
        val saoParams    = sao.parameters
        val result: Unit = emailService
          .sendSubmitterSaoCertificateEmail(
            email = sao.to.head,
            saoName = saoParams.saoName,
            submitterName = saoParams.submitterName,
            referenceId = saoParams.referenceId
          )
          .futureValue

        verify(mockEmailConnector, Times(1)).postEmail(captor.capture())(using any)

        captor.getValue.asInstanceOf[SubmitterSaoCertificateEmail]
        result shouldBe ()
      }

    }

    "send sao email certificate confirmation to two contacts" in {
      when(mockEmailConnector.postEmail(any)(using any)).thenReturn(Future.successful(HttpResponse(202)))
      val captor: ArgumentCaptor[Email] = ArgumentCaptor.forClass(classOf[Email])

      val twoContacts    = contacts ++ List(Contact("name2", "email2@example.com", "en", "ACTIVE"))
      val expectedEmails = twoContacts.map { contact =>
        SaoContactCertificateEmail(
          to = List(contact.email),
          parameters = SaoContactCertificateEmailParameters(
            contactName = contact.name,
            saoName = "saoName",
            submittedDateTime = "any",
            referenceId = "certRef"
          )
        )
      }

      val actualEmails = twoContacts.zipWithIndex.map { (contact, index) =>
        val result: Unit = emailService
          .sendSaoContactCertificateEmail(
            email = contact.email,
            contactName = contact.name,
            referenceId = "certRef",
            saoName = "saoName"
          )
          .futureValue
        result shouldBe ()

        verify(mockEmailConnector, Times(index + 1)).postEmail(captor.capture())(using any)
        val actualEmail = captor.getValue.asInstanceOf[SaoContactCertificateEmail]

        actualEmail.copy(parameters = actualEmail.parameters.copy(submittedDateTime = "any"))

      }
      actualEmails shouldBe expectedEmails
    }

    "send sao email certificate confirmation to two contacts and the SAO" in {
      when(mockEmailConnector.postEmail(any)(using any)).thenReturn(Future.successful(HttpResponse(202)))

      val captor: ArgumentCaptor[Email] = ArgumentCaptor.forClass(classOf[Email])

      val expectedEmail = SaoCertificateEmail(
        to = List("sao@example.com"),
        parameters = SaoCertificateEmailParameters(
          saoName = "saoName",
          submittedDateTime = "any",
          referenceId = "certRef"
        )
      )

      val result: Unit =
        emailService.sendSaoCertificateEmail(email = "sao@example.com", saoName = "saoName", referenceId = "certRef")

      verify(mockEmailConnector, Times(1)).postEmail(captor.capture())(using any)

      val actualEmail = captor.getValue.asInstanceOf[SaoCertificateEmail]

      result shouldBe ()
      actualEmail.copy(parameters = actualEmail.parameters.copy(submittedDateTime = "any")) shouldBe expectedEmail

    }
  }
}
