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

enum CompanyStatus {
  case ACTIVE
  case DORMANT
  case ADMINISTRATION
  case LIQUIDATION
}

object CompanyStatus {
  given Reads[CompanyStatus] = JsPath
    .read[String]
    .flatMapResult(name =>
      Try(CompanyStatus.valueOf(name)).toEither match {
        case Left(_)      => JsError(Reason.INVALID_ENUM_VALUE.toString)
        case Right(value) => JsSuccess(value)
      }
    )

  given Writes[CompanyStatus] = Writes(r => JsString(r.toString))

}
