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

object StandardRadioFormats:

  val baseColumns: Map[CsvColumn, RadioColumn] = Map(
    CsvColumn.ChannelNumber -> RadioColumn(CsvColumn.ChannelNumber)(_.channelNumber.getOrElse("")),
    CsvColumn.ReceiveFrequency -> RadioColumn(CsvColumn.ReceiveFrequency)(_.frequency.rx.toString),
    CsvColumn.OffsetFrequency -> RadioColumn(CsvColumn.OffsetFrequency)(_.frequency.offsetAbs.toString),
    CsvColumn.OffsetDirection -> RadioColumn.direction(CsvColumn.OffsetDirection, minus = "DUP-", simplex = "Simplex", plus = "DUP+"),
    CsvColumn.OperatingMode -> RadioColumn(CsvColumn.OperatingMode)(_.mode.toString),
    CsvColumn.Name -> RadioColumn.channelName(CsvColumn.Name, maxLength = 16),
    CsvColumn.ToneMode -> RadioColumn(CsvColumn.ToneMode)(_.ctcss.mode.toString),
    CsvColumn.Ctcss -> RadioColumn(CsvColumn.Ctcss)(_.ctcss.frequency.map(_.hz.toString).getOrElse("")),
    CsvColumn.RxCtcss -> RadioColumn(CsvColumn.RxCtcss)(_.ctcss.frequency.map(_.hz.toString).getOrElse("")),
    CsvColumn.Comment -> RadioColumn(CsvColumn.Comment)(_.remarks)
  )

  /** Canonical base export mapping with standard column extractors and defaults. */
  val base: RadioExportDefinition = RadioExportDefinition(
    name = "Base Format",
    columns = baseColumns
  )

object KenwoodTHD75 extends RadioExport:
  val definition: RadioExportDefinition = StandardRadioFormats.base
    .copy(name = "Kenwood TH-D75")
    .withColumn(RadioColumn.const(CsvColumn.Group, "1"))
    .withColumn(
      RadioColumn.direction(CsvColumn.OffsetDirection, minus = "Minus", simplex = "Simplex", plus = "Plus")
    )

object YaesuFTM510 extends RadioExport:
  val definition: RadioExportDefinition = StandardRadioFormats.base
    .copy(name = "Yaesu FTM-510")
    .withColumn(
      RadioColumn.direction(CsvColumn.OffsetDirection, minus = "Minus", plus = "Plus")
    )
    .withColumn(RadioColumn.const(CsvColumn.Ams, "N"))

object YaesuFTM500 extends RadioExport:
  val definition: RadioExportDefinition = YaesuFTM510.definition.copy(name = "Yaesu FTM-500")

object IcomID52Plus extends RadioExport:
  val definition: RadioExportDefinition = StandardRadioFormats.base
    .copy(name = "Icom ID-52Plus")
    .withColumn(RadioColumn.const(CsvColumn.Bank, "1"))
    .withColumn(
      RadioColumn.direction(CsvColumn.OffsetDirection, minus = "DUP-", simplex = "Simplex", plus = "DUP+")
    )
