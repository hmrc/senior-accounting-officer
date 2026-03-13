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

package uk.gov.hmrc.senioraccountingofficer.helpers

import play.api.libs.json.*

import scala.util.Try

object JsonErrorHandling {

  final case class ApiError(path: Option[String], reason: String)

  def parseJson(body: String): Either[JsValue, JsValue] =
    Try(Json.parse(body)).toEither.left.map(_ => Json.arr(Json.obj("reason" -> "MALFORMED_REQUEST")))

  def serverError: JsValue = Json.arr(Json.obj("reason" -> "INTERNAL_SERVER_ERROR"))

  def toJson(errors: collection.Seq[(JsPath, collection.Seq[JsonValidationError])]): JsValue = {
    JsArray(
      errors.flatMap { case (path, errors) =>
        errors.map { error =>
          Json.obj(
            "path"   -> normalizePath(path),
            "reason" -> errors.headOption.map(_.message).getOrElse("INVALID_DATA")
          )
        }
      }.toSeq
    )
  }
  private def normalizePath(path: JsPath): String =
    path
      .toString()
      .stripPrefix("obj.")
      .stripPrefix("/")
}
