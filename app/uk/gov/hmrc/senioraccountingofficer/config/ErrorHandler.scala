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

package uk.gov.hmrc.senioraccountingofficer.config

import play.api.http.HeaderNames
import play.api.http.Status.*
import play.api.mvc.{RequestHeader, Result}
import play.api.{Configuration, Logging}
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.bootstrap.backend.http.JsonErrorHandler
import uk.gov.hmrc.play.bootstrap.config.HttpAuditEvent

import scala.concurrent.{ExecutionContext, Future}

import javax.inject.Inject

class ErrorHandler @Inject() (
    auditConnector: AuditConnector,
    httpAuditEvent: HttpAuditEvent,
    configuration: Configuration
)(implicit ec: ExecutionContext)
    extends JsonErrorHandler(auditConnector, httpAuditEvent, configuration)
    with Logging {

  override def onClientError(request: RequestHeader, statusCode: Int, message: String): Future[Result] = {
    if statusCode == REQUEST_ENTITY_TOO_LARGE then {
      val contentLength = request.headers.get(HeaderNames.CONTENT_LENGTH).getOrElse("undefined")
      val correlationId = request.headers.get("correlationId").getOrElse("undefined")
      logger.warn(
        s"${request.method} ${request.uri} [REQUEST_ENTITY_TOO_LARGE][CorrelationId=$correlationId] CONTENT_LENGTH=$contentLength "
      )
    }

    super.onClientError(request = request, statusCode = statusCode, message = message)
  }

}
