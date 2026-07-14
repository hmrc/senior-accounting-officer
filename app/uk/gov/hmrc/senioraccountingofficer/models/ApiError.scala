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

package uk.gov.hmrc.senioraccountingofficer.models

import play.api.libs.json.*
import play.api.libs.json.Reads.*
import uk.gov.hmrc.senioraccountingofficer.models.ApiError.*

final case class ApiError(reason: Reason, path: Option[String] = None)

object ApiError {

  enum Reason {
    case DOWNSTREAM_SERVICE_ERROR,
      DOWNSTREAM_SERVICE_UNAVAILABLE,
      DOWNSTREAM_SERVICE_MISALIGNMENT,
      AUTH_MISALIGNMENT,
      SUBSCRIPTION_NOT_FOUND,
      SERVICE_MISCONFIGURATION,
      UNAUTHENTICATED,
      NO_ENROLMENT,
      MALFORMED_REQUEST,
      MISSING_CORRELATION_ID,
      MISSING_REQUIRED_FIELD,
      INVALID_DATA_TYPE,
      INVALID_FORMAT,
      INVALID_ENUM_VALUE,
      ARRAY_MIN_ITEMS_NOT_MET,
      LENGTH_OUT_OF_BOUNDS,
      CANNOT_BE_EMPTY
  }

  object Reason {
    given Reads[Reason]  = JsPath.read[String].map(name => Reason.valueOf(name))
    given Writes[Reason] = Writes[Reason](r => JsString(r.toString))
  }

  given OFormat[ApiError] = Json.format[ApiError]
}
