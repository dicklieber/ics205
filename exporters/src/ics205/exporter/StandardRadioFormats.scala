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

  /** Canonical base export mapping with standard column extractors and defaults. */
  val base: RadioExportDefinition = RadioExportDefinition(
    name = "Base Format",
    columns = List(
      RadioColumn(CsvColumn.ChannelNumber)(_.channelNumber.getOrElse("")),
      RadioColumn(CsvColumn.ReceiveFrequency)(_.frequency.rx.toString),
//      RadioColumn(CsvColumn.TransmitFrequency)(_.frequency.tx.toString),
      RadioColumn(CsvColumn.OffsetFrequency)(_.frequency.offsetAbs.toString),
      RadioColumn.direction(CsvColumn.OffsetDirection, minus = "DUP-", simplex = "Simplex", plus = "DUP+"),
      RadioColumn(CsvColumn.OperatingMode)(_.mode.toString),
      RadioColumn.channelName(CsvColumn.Name, maxLength = 16),
      RadioColumn(CsvColumn.ToneMode)(_.ctcss.mode.toString),
      RadioColumn(CsvColumn.Ctcss)(_.ctcss.frequency.map(_.hz.toString).getOrElse("")),
      RadioColumn(CsvColumn.RxCtcss)(_.ctcss.frequency.map(_.hz.toString).getOrElse("")),
//      RadioColumn.const(CsvColumn.Dcs, "023"),
//      RadioColumn.const(CsvColumn.DcsPolarity, "Both N"),
//      RadioColumn.const(CsvColumn.Lockout, "Scan"),
//      RadioColumn.const(CsvColumn.Step, "5 kHz"),
//      RadioColumn.const(CsvColumn.FineStepEnable, "N"),
//      RadioColumn.const(CsvColumn.FineStep, "0"),
//      RadioColumn.const(CsvColumn.DigitalSquelch, "0"),
//      RadioColumn.const(CsvColumn.DigitalCode, "0"),
//      RadioColumn.const(CsvColumn.YourCallsign, "CQCQCQ"),
//      RadioColumn.const(CsvColumn.Rpt1Callsign, "DIRECT"),
//      RadioColumn.const(CsvColumn.Rpt2Callsign, "DIRECT"),
//      RadioColumn(CsvColumn.Group)(_.zoneGroup.getOrElse("")),
      RadioColumn(CsvColumn.Comment)(_.remarks)
    )
  )

object KenwoodTHD75 extends RadioExport:
  val definition: RadioExportDefinition = StandardRadioFormats.base
    .copy(name = "Kenwood TH-D75")
    .overrideColumn(
      CsvColumn.OffsetDirection,
      RadioColumn.direction(CsvColumn.OffsetDirection, minus = "Minus", simplex = "Simplex", plus = "Plus")
    )

object YaesuFTM510 extends RadioExport:
  val definition: RadioExportDefinition = StandardRadioFormats.base
    .copy(name = "Yaesu FTM-510")
    .overrideColumn(
      CsvColumn.OffsetDirection,
      RadioColumn.direction(CsvColumn.OffsetDirection, minus = "Minus", plus = "Plus")
    )
    .insertAfter(CsvColumn.OperatingMode, RadioColumn.const(CsvColumn.Ams, "N"))
//    .removeColumns(
//      CsvColumn.RxCtcss,
//      CsvColumn.DcsPolarity,
//      CsvColumn.Lockout,
//      CsvColumn.FineStepEnable,
//      CsvColumn.FineStep,
//      CsvColumn.DigitalSquelch,
//      CsvColumn.DigitalCode,
//      CsvColumn.YourCallsign,
//      CsvColumn.Rpt1Callsign,
//      CsvColumn.Rpt2Callsign,
//      CsvColumn.Group
//    )

object YaesuFTM500 extends RadioExport:
  val definition: RadioExportDefinition = YaesuFTM510.definition.copy(name = "Yaesu FTM-500")

object IcomID52Plus extends RadioExport:
  val definition: RadioExportDefinition = StandardRadioFormats.base
    .copy(name = "Icom ID-52Plus")
    .insertAfter(CsvColumn.ChannelNumber, RadioColumn.const(CsvColumn.Bank, "1"))
    .overrideColumn(
      CsvColumn.OffsetDirection,
      RadioColumn.direction(CsvColumn.OffsetDirection, minus = "DUP-", simplex = "Simplex", plus = "DUP+")
    )
