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

import play.api.libs.json.{Format, Json}
import uk.gov.hmrc.senioraccountingofficer.models.dps.Sao as DpsSao

import java.time.LocalDate

final case class Sao(
    name: PersonName,
    fromDate: Option[LocalDate],
    toDate: Option[LocalDate]
)

object Sao {

  given Format[Sao] = Json.format[Sao]

  extension (sao: Sao) {
    def toDpsSao: DpsSao = {
      DpsSao(
        name = sao.name.value,
        fromDate = sao.fromDate.map(_.toString),
        toDate = sao.toDate.map(_.toString)
      )
    }
  }

}
