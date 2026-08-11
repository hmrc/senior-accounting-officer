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

import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{reset, verify, when}
import org.mockito.internal.verification.Times
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.mockito.MockitoSugar
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}
import uk.gov.hmrc.senioraccountingofficer.connectors.EmailConnector
import uk.gov.hmrc.senioraccountingofficer.models.Email
import uk.gov.hmrc.senioraccountingofficer.models.dps.Contact

import scala.concurrent.{ExecutionContext, Future}

class EmailServiceSpec extends AnyFreeSpec with Matchers with MockitoSugar with ScalaFutures with BeforeAndAfterEach {

  given ExecutionContext                 = ExecutionContext.global
  given HeaderCarrier                    = HeaderCarrier()
  val mockEmailConnector: EmailConnector = mock[EmailConnector]
  val emailService: EmailService         = EmailService(mockEmailConnector)

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockEmailConnector)
  }
  val contacts: List[Contact] = List(Contact(name = "name", email = "email@example.com", "en", "ACTIVE"))
  "sendEmail" - {

    "send notification email to no contacts" in {
      when(mockEmailConnector.postEmail(any())(using any())).thenReturn(Future(HttpResponse(202)))
      val result: Unit = emailService.sendNotificationEmail(List(), "companyName", "ABC")
      verify(mockEmailConnector, Times(0)).postEmail(any[Email])(using any())

      result shouldBe ()
    }
    "send notification email to one contact" in {
      when(mockEmailConnector.postEmail(any())(using any())).thenReturn(Future(HttpResponse(202)))
      val result: Unit = emailService.sendNotificationEmail(contacts, "companyName", "ABC")
      verify(mockEmailConnector, Times(1)).postEmail(any[Email])(using any())

      result shouldBe ()
    }

    "send notification email to two contacts" in {
      val twoContacts = contacts ++ List(Contact("name2", "email2@example.com", "en", "ACTIVE"))
      when(mockEmailConnector.postEmail(any())(using any())).thenReturn(Future(HttpResponse(202)))
      val result: Unit = emailService.sendNotificationEmail(twoContacts, "companyName", "ABC")
      verify(mockEmailConnector, Times(2)).postEmail(any[Email])(using any())

      result shouldBe ()
    }
  }
}
