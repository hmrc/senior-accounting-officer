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

import org.scalatest.concurrent.ScalaFutures
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.mockito.MockitoSugar
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.senioraccountingofficer.connectors.EmailConnector

import scala.concurrent.ExecutionContext

class EmailServiceSpec extends AnyFreeSpec with Matchers with MockitoSugar with ScalaFutures {

  given ExecutionContext                 = ExecutionContext.global
  given HeaderCarrier                    = HeaderCarrier()
  val mockEmailConnector: EmailConnector = mock[EmailConnector]

  "sendEmail" - {
//    "notification" in {
//
//    }
//    "certificate" in {
//
//    }
  }
}
