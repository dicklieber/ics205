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

package ics205.exporter

import ics205.model.Ics205Channel
import scala.annotation.targetName

/**
 * Represents a single column in a radio CSV export.
 *
 * @param column Optional canonical CSV column identifier.
 * @param headerOverride Optional custom header string overriding `column.header`.
 * @param extract Function to compute the string representation from a channel.
 */
case class RadioColumn(
  column: Option[CsvColumn],
  headerOverride: Option[String] = None,
  extract: (Ics205Channel, RadioChannelNameBuilder) => String
):
  /** CSV header emitted in the output */
  def header: String = headerOverride.orElse(column.map(_.header)).getOrElse("")

object RadioColumn:
  def apply(column: CsvColumn)(f: Ics205Channel => Any): RadioColumn =
    RadioColumn(Some(column), None, (ch, _) => Option(f(ch)).map(_.toString).getOrElse(""))

  def apply(column: CsvColumn, header: String)(f: Ics205Channel => Any): RadioColumn =
    RadioColumn(Some(column), Some(header), (ch, _) => Option(f(ch)).map(_.toString).getOrElse(""))

  def apply(header: String)(f: Ics205Channel => Any): RadioColumn =
    RadioColumn(CsvColumn.fromHeader(header), Some(header), (ch, _) => Option(f(ch)).map(_.toString).getOrElse(""))

  def const(column: CsvColumn, value: Any): RadioColumn =
    RadioColumn(Some(column), None, (_, _) => Option(value).map(_.toString).getOrElse(""))

  def const(header: String, value: Any): RadioColumn =
    RadioColumn(CsvColumn.fromHeader(header), Some(header), (_, _) => Option(value).map(_.toString).getOrElse(""))

  def empty(column: CsvColumn = CsvColumn.Empty): RadioColumn =
    RadioColumn(Some(column), None, (_, _) => "")

  def channelName(column: CsvColumn = CsvColumn.Name, maxLength: Int = 16): RadioColumn =
    RadioColumn(Some(column), None, (ch, nameBuilder) => nameBuilder(ch).take(maxLength))

  def direction(
    column: CsvColumn = CsvColumn.OffsetDirection,
    minus: String = "DUP-",
    simplex: String = "Simplex",
    plus: String = "DUP+"
  ): RadioColumn =
    RadioColumn(column): ch =>
      if ch.frequency.isSimplex || ch.frequency.offset.mhz == BigDecimal(0) then simplex
      else if ch.frequency.offset.mhz > BigDecimal(0) then plus
      else minus

  def ctcssHz(column: CsvColumn = CsvColumn.Ctcss, default: String = ""): RadioColumn =
    RadioColumn(column): ch =>
      ch.ctcss.frequency.map(_.hz.toString).getOrElse(default)

  extension (col: CsvColumn)
    def :=(f: Ics205Channel => Any): RadioColumn = RadioColumn(col)(f)
    def :=(constVal: String): RadioColumn = RadioColumn.const(col, constVal)

  extension (hdr: String)
    def :=(f: Ics205Channel => Any): RadioColumn = RadioColumn(hdr)(f)
    def :=(constVal: String): RadioColumn = RadioColumn.const(hdr, constVal)

/**
 * Definition for exporting ICS-205 channels into a radio-specific CSV format.
 *
 * @param name Model name (e.g., "Yaesu FTM-500", "Kenwood TH-D75").
 * @param columns List of column specifications defining CSV output.
 * @param channelNameBuilder Component to build radio channel name from channel fields.
 */
case class RadioExportDefinition(
  name: String,
  columns: List[RadioColumn],
  channelNameBuilder: RadioChannelNameBuilder = new RadioChannelNameBuilderDefault()
):
  def headers: List[String] = columns.map(_.header)

  /** Emits columns sorted by the natural CsvColumn enum declaration order. */
  def sortedColumns: List[RadioColumn] =
    columns.sortBy { col =>
      col.column.map(_.ordinal).getOrElse(Int.MaxValue)
    }

  /** Replaces an existing column by CsvColumn with a new column definition */
  def overrideColumn(target: CsvColumn, newColumn: RadioColumn): RadioExportDefinition =
    copy(columns = columns.map(c => if c.column.contains(target) then newColumn else c))

  /** Overrides extraction logic for a specific CsvColumn */
  def overrideColumn(target: CsvColumn)(f: Ics205Channel => Any): RadioExportDefinition =
    overrideColumn(target, RadioColumn(target)(f))

  /** Replaces an existing column by header name with a new column definition */
  @targetName("overrideColumnByHeader")
  def overrideColumn(header: String, newColumn: RadioColumn): RadioExportDefinition =
    copy(columns = columns.map(c => if c.header == header then newColumn else c))

  /** Overrides extraction logic for a specific header name */
  @targetName("overrideColumnByHeaderExtract")
  def overrideColumn(header: String)(f: Ics205Channel => Any): RadioExportDefinition =
    overrideColumn(header, RadioColumn(header)(f))

  /** Removes columns by CsvColumn */
  def removeColumns(cols: CsvColumn*): RadioExportDefinition =
    val colSet = cols.toSet
    copy(columns = columns.filterNot(c => c.column.exists(colSet.contains)))

  /** Removes columns by header name */
  @targetName("removeHeaders")
  def removeColumns(headers: String*): RadioExportDefinition =
    val headerSet = headers.toSet
    copy(columns = columns.filterNot(c => headerSet.contains(c.header)))

  /** Appends additional columns */
  def addColumns(newCols: RadioColumn*): RadioExportDefinition =
    copy(columns = columns ++ newCols)

  /** Inserts a new column right after a specific CsvColumn */
  def insertAfter(target: CsvColumn, newCol: RadioColumn): RadioExportDefinition =
    val idx = columns.indexWhere(_.column.contains(target))
    if idx < 0 then addColumns(newCol)
    else
      val (front, back) = columns.splitAt(idx + 1)
      copy(columns = front ++ List(newCol) ++ back)

  /** Inserts a new column right after a specific header */
  @targetName("insertAfterHeader")
  def insertAfter(targetHeader: String, newCol: RadioColumn): RadioExportDefinition =
    val idx = columns.indexWhere(_.header == targetHeader)
    if idx < 0 then addColumns(newCol)
    else
      val (front, back) = columns.splitAt(idx + 1)
      copy(columns = front ++ List(newCol) ++ back)

trait RadioChannelNameBuilder:
  def apply(ics205Channel: Ics205Channel): String

class RadioChannelNameBuilderDefault() extends RadioChannelNameBuilder:
  private val maxLength = 16
  private val abbreviations = Map(
    "operations" -> "Ops", "operation" -> "Ops", "command" -> "Cmd",
    "medical" -> "Med", "emergency" -> "Emerg", "logistics" -> "Log",
    "communications" -> "Comms", "tactical" -> "Tac", "support" -> "Sup",
    "division" -> "Div", "branch" -> "Br", "group" -> "Grp",
    "administration" -> "Admin", "administrative" -> "Admin",
    "primary" -> "Pri", "secondary" -> "Sec", "alternate" -> "Alt",
    "north" -> "N", "south" -> "S", "east" -> "E", "west" -> "W",
    "central" -> "Ctr", "station" -> "Sta", "channel" -> "Ch"
  )
  private val word = "[A-Za-z]+".r

  private def normalize(value: String): String = value.trim.replaceAll("\\s+", " ")

  // Avoid leaving half a UTF-16 surrogate pair at the end of a truncated label.
  private def truncate(value: String, limit: Int): String =
    val result = value.take(limit).trim
    if result.nonEmpty && Character.isHighSurrogate(result.last) then result.dropRight(1) else result

  override def apply(ics205Channel: Ics205Channel): String =
    val name = normalize(ics205Channel.name)
    val assignment = normalize(ics205Channel.assignment)
    val separator = if name.nonEmpty then " " else ""
    val budget = maxLength - name.length - separator.length
    if assignment.isEmpty || budget <= 0 then truncate(name, maxLength)
    else
      val abbreviated = word.replaceAllIn(assignment, m =>
        abbreviations.getOrElse(m.matched.toLowerCase(java.util.Locale.ROOT), m.matched))
      val consonants = word.replaceAllIn(abbreviated, m =>
        m.matched.head.toString + m.matched.tail.replaceAll("(?i)[aeiou]", ""))
      // Only alphabetic runs become initials, retaining identifiers such as 16-20 or CMT1.
      val initials = word.replaceAllIn(abbreviated, m => m.matched.head.toString)
      val candidates = Seq(assignment, abbreviated, consonants, initials, initials.replace(" ", ""))
      val compact = candidates.find(_.length <= budget).getOrElse(truncate(candidates.last, budget))
      s"$name$separator$compact".trim
