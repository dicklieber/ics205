/*
 * Copyright (c) 2026. Dick Lieber, WA9NNN
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package ics205.model

import io.circe.*
import io.circe.parser.*
import io.circe.syntax.*

object Ics205Json:
  private val printer: Printer = Printer.spaces2.copy(dropNullValues = true, colonLeft = "")

  /** Serializes an [[Ics205]] plan to a pretty-printed 2-space indented JSON string. */
  def toJson(plan: Ics205): String =
    plan.asJson.printWith(printer)

  /**
   * Deserializes an [[Ics205]] plan from a JSON string.
   * Supports JSON representing either an [[Ics205]] directly or wrapped within an [[Ics205Event]].
   */
  def fromJson(jsonStr: String): Either[String, Ics205] =
    parse(jsonStr) match
      case Left(failure) =>
        Left(s"Failed to parse JSON: ${failure.message}")
      case Right(json) =>
        if !json.isObject then
          Left("Invalid JSON format: expected a JSON object representing an ICS 205 plan or event.")
        else
          val planJson = json.asObject.flatMap(_("ics205")).getOrElse(json)
          planJson.as[Ics205].left.map(df => s"Failed to decode ICS 205: ${df.message}")
