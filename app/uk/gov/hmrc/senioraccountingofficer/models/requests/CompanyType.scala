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

package uk.gov.hmrc.senioraccountingofficer.models.requests

import play.api.libs.json.*
import uk.gov.hmrc.senioraccountingofficer.models.ApiError.Reason

import scala.util.Try

enum CompanyType {
  case PLC, LTD
}

object CompanyType {
  given Reads[CompanyType] = JsPath
    .read[String]
    .flatMapResult(name =>
      Try(CompanyType.valueOf(name)).toEither match {
        case Left(_)      => JsError(Reason.INVALID_ENUM_VALUE.toString)
        case Right(value) => JsSuccess(value)
      }
    )
  given Writes[CompanyType] = Writes[CompanyType](r => JsString(r.toString))
}
