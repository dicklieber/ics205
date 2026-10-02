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
 * Describes memory channel grouping schemes supported by various radios (e.g., Memory Group or Memory Bank).
 *
 * @param column Canonical CSV column identifier associated with this grouping scheme.
 * @param label Human-readable label for the UI prompt.
 * @param default Default group/bank value if none is specified by the user.
 */
enum RadioGrouping(val column: CsvColumn, val label: String, val default: String):
  case Group extends RadioGrouping(CsvColumn.Group, "Memory Group", "1")
  case Bank  extends RadioGrouping(CsvColumn.Bank, "Memory Bank", "1")

/**
 * Represents a single column in a radio CSV export.
 *
 * @param column Optional canonical CSV column identifier.
 * @param headerOverride Optional custom header string overriding `column.header`.
 * @param extract Function to compute the string representation from a channel.
 */
case class RadioColumn(
  column: CsvColumn,
  extract: (Ics205Channel, RadioChannelNameBuilder) => String
):
  /** CSV header emitted in the output */
  def header: String = column.header

object RadioColumn:
  def apply(column: CsvColumn)(f: Ics205Channel => Any): RadioColumn =
    RadioColumn(column, (ch, _) => Option(f(ch)).map(_.toString).getOrElse(""))

  def const(column: CsvColumn, value: Any): RadioColumn =
    RadioColumn(column, (_, _) => Option(value).map(_.toString).getOrElse(""))

  def empty(column: CsvColumn): RadioColumn =
    RadioColumn(column, (_, _) => "")

  def channelName(column: CsvColumn = CsvColumn.Name, maxLength: Int = 16): RadioColumn =
    RadioColumn(column, (ch, nameBuilder) => nameBuilder(ch).take(maxLength))

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

/**
 * Definition for exporting ICS-205 channels into a radio-specific CSV format.
 * Columns are stored in a Map keyed by CsvColumn and emitted in natural CsvColumn enum declaration order.
 *
 * @param name Model name (e.g., "Yaesu FTM-500", "Kenwood TH-D75").
 * @param columns Map of column specifications defining CSV output.
 * @param grouping Optional memory grouping scheme supported by this radio (e.g., Group or Bank).
 * @param channelNameBuilder Component to build radio channel name from channel fields.
 */
case class RadioExportDefinition(
  name: String,
  columns: Map[CsvColumn, RadioColumn],
  grouping: Option[RadioGrouping] = None,
  channelNameBuilder: RadioChannelNameBuilder = new RadioChannelNameBuilderDefault()
):
  /** Columns ordered deterministically by CsvColumn enum declaration order. */
  lazy val orderedColumns: Seq[RadioColumn] =
    columns.toSeq.sortBy(_._1.ordinal).map(_._2)

  def headers: Seq[String] = orderedColumns.map(_.header)

  /** Configures a grouping scheme and installs the default column value. */
  def withGrouping(g: RadioGrouping): RadioExportDefinition =
    copy(grouping = Some(g), columns = columns + (g.column -> RadioColumn.const(g.column, g.default)))

  /** Overrides or sets the value for the radio's group or bank column, if grouping is supported. */
  def withGroupOrBank(value: String): RadioExportDefinition =
    grouping match
      case Some(g) => withColumn(RadioColumn.const(g.column, value))
      case None    => this

  /** Add or override columns */
  def withColumns(newCols: (CsvColumn, RadioColumn)*): RadioExportDefinition =
    copy(columns = columns ++ newCols)

  def withColumns(newCols: Iterable[RadioColumn]): RadioExportDefinition =
    copy(columns = columns ++ newCols.map(c => c.column -> c))

  def withColumn(col: RadioColumn): RadioExportDefinition =
    copy(columns = columns + (col.column -> col))

  /** Remove columns */
  def withoutColumns(cols: CsvColumn*): RadioExportDefinition =
    copy(columns = columns -- cols)

object RadioExportDefinition:
  def apply(name: String, columns: Seq[RadioColumn]): RadioExportDefinition =
    RadioExportDefinition(name, columns.map(c => c.column -> c).toMap)

  def apply(name: String, columns: Seq[RadioColumn], channelNameBuilder: RadioChannelNameBuilder): RadioExportDefinition =
    RadioExportDefinition(name, columns.map(c => c.column -> c).toMap, None, channelNameBuilder)

  def apply(name: String, columns: Seq[RadioColumn], grouping: Option[RadioGrouping], channelNameBuilder: RadioChannelNameBuilder): RadioExportDefinition =
    RadioExportDefinition(name, columns.map(c => c.column -> c).toMap, grouping, channelNameBuilder)

trait RadioExportDefinitionProvider:
  def definition: RadioExportDefinition

type RadioExport = RadioExportDefinitionProvider

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
