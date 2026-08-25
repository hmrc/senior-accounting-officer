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

package uk.gov.hmrc.senioraccountingofficer.controllers.testOnly

import play.api.libs.json.Json
import play.api.mvc.*
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController
import uk.gov.hmrc.play.http.HeaderCarrierConverter
import uk.gov.hmrc.senioraccountingofficer.models.EmailTemplate
import uk.gov.hmrc.senioraccountingofficer.models.dps.Contact
import uk.gov.hmrc.senioraccountingofficer.services.EmailService

import scala.concurrent.{ExecutionContext, Future}

import javax.inject.{Inject, Singleton}

@Singleton()
class TestOnlyEmailController @Inject() (
    cc: ControllerComponents,
    emailService: EmailService
)(using ExecutionContext)
    extends BackendController(cc) {

  private val testCompanyName           = "Test Company Ltd"
  private val testSaoName               = "Test SAO"
  private val testNotificationReference = "NOT0123456789"
  private val testCertificateReference  = "CRT0123456789"

  private val validTemplates = Seq("notification", "certificate-sao", "certificate-submitter")

  def sendEmail(email: String, name: Option[String], template: Option[String]): Action[AnyContent] =
    Action.async { request =>
      given HeaderCarrier = HeaderCarrierConverter.fromRequest(request)

      val recipientName = name.map(_.trim).filter(_.nonEmpty).getOrElse(TestOnlyEmailController.nameFrom(email))

      template.map(_.trim.toLowerCase).getOrElse("notification") match {
        case "notification" =>
          emailService
            .sendNotificationEmail(
              contacts = List(Contact(name = recipientName, email = email, language = "en-GB", status = "valid")),
              companyName = testCompanyName,
              referenceId = testNotificationReference
            )
            .map(_ => accepted(email, recipientName, EmailTemplate.NotificationConfirmation))

        case "certificate-sao" =>
          emailService
            .sendSaoCertificateEmail(
              email = email,
              recipientName = recipientName,
              companyName = testCompanyName,
              referenceId = testCertificateReference,
              saoName = recipientName
            )
            .map(_ => accepted(email, recipientName, EmailTemplate.CertificateConfirmationSAO))

        case "certificate-submitter" =>
          emailService
            .sendSubmitterCertificateEmail(
              email = email,
              recipientName = recipientName,
              companyName = testCompanyName,
              referenceId = testCertificateReference,
              submitterName = recipientName,
              saoName = testSaoName
            )
            .map(_ => accepted(email, recipientName, EmailTemplate.CertificateConfirmationSubmitter))

        case unknown =>
          Future.successful(
            BadRequest(
              Json.obj(
                "error"          -> s"Unknown template '$unknown'",
                "validTemplates" -> validTemplates
              )
            )
          )
      }
    }

  private def accepted(email: String, recipientName: String, emailTemplate: EmailTemplate): Result =
    Accepted(
      Json.obj(
        "sentTo"     -> email,
        "name"       -> recipientName,
        "templateId" -> emailTemplate.templateId
      )
    )
}

object TestOnlyEmailController {

  def nameFrom(email: String): String =
    email.takeWhile(_ != '@').split("[._+-]+").filter(_.nonEmpty).map(_.capitalize).mkString(" ") match {
      case ""   => "QA Tester"
      case name => name
    }
}
