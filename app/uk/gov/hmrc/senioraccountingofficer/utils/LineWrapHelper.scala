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

package uk.gov.hmrc.senioraccountingofficer.utils

import scala.annotation.tailrec

// TODO: maybe overengineered. how does the line wrap in the pdf happen currently?
object LineWrapHelper {
  // def wrapLines(text: String, maxLength: Int): String = {
  //   val words = text.split(" ").toList

  //   @tailrec
  //   def loop(words: List[String], spaceLeft: Int, result: String): String = {
  //     val isFirstWord = result == ""
  //     words match {
  //       case word :: words if (word.length > maxLength) => {
  //         val splitWord = word.take(spaceLeft) :: word.drop(spaceLeft).grouped(maxLength).toList
  //         loop(
  //           words,
  //           maxLength - splitWord.last.length - 1,
  //           if isFirstWord then splitWord.mkString("-\n") else result + " " + splitWord.mkString("-\n")
  //         )
  //       }
  //       case word :: words if (word.length + 1) > spaceLeft => {
  //         loop(words, maxLength - word.length, if isFirstWord then word else result + "\n" + word)
  //       }
  //       case word :: words => {
  //         loop(words, spaceLeft - word.length - 1, if isFirstWord then word else result + " " + word)
  //       }
  //       case Nil => { result }
  //     }
  //   }

  //   loop(words, maxLength, "")
  // }

  def wrapLines(text: String, maxLineLength: Int, spaceOnCurrentLine: Int): String = {
    val firstLine      = text.take(spaceOnCurrentLine)
    val remainingLines = text.drop(spaceOnCurrentLine).grouped(maxLineLength).toList
    val lines          = firstLine :: remainingLines
    lines.mkString("\n")
  }
}
