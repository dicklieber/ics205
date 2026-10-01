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

import ics205.model.{Ctcss, CtcssFrequency, CtcssMode, Frequency, Ics205Channel, RadioMode, RxWithOffset}

class RadioExportDefinitionTests extends munit.FunSuite:
  val sampleChannel = Ics205Channel(
    id = "1",
    zoneGroup = Some("Zone 1"),
    channelNumber = Some("CH-01"),
    function = "Tac 1",
    name = "TAC1",
    assignment = "Operations",
    frequency = RxWithOffset(Frequency(BigDecimal("146.520")), Frequency(BigDecimal("0.600"))),
    mode = RadioMode.Fm,
    ctcss = Ctcss(frequency = Some(CtcssFrequency.Hz107_2), mode = CtcssMode.TSQL),
    remarks = "Test channel"
  )

  val simplexChannel = sampleChannel.copy(
    frequency = RxWithOffset(Frequency(BigDecimal("146.520")), Frequency(BigDecimal("0.000")))
  )

  val minusChannel = sampleChannel.copy(
    frequency = RxWithOffset(Frequency(BigDecimal("146.880")), Frequency(BigDecimal("-0.600")))
  )

  test("RadioColumn extracts channel values"):
    val col = RadioColumn(CsvColumn.ReceiveFrequency)(_.frequency.rx)
    assertEquals(col.header, "Receive Frequency")
    assertEquals(col.extract(sampleChannel, new RadioChannelNameBuilderDefault()), "146.520")

  test("RadioColumn extracts constant values"):
    val col = RadioColumn.const(CsvColumn.Step, "5 kHz")
    assertEquals(col.header, "Step")
    assertEquals(col.extract(sampleChannel, new RadioChannelNameBuilderDefault()), "5 kHz")

  test("RadioColumn extracts empty string"):
    val col = RadioColumn.empty(CsvColumn.Empty)
    assertEquals(col.header, "")
    assertEquals(col.extract(sampleChannel, new RadioChannelNameBuilderDefault()), "")

  test("RadioColumn extracts RadioChannelName via RadioChannelNameBuilder"):
    val col = RadioColumn.channelName(CsvColumn.Name, maxLength = 16)
    assertEquals(col.header, "Name")
    assertEquals(col.extract(sampleChannel, new RadioChannelNameBuilderDefault()), "TAC1 Operations")

  test("RadioColumn.direction formats simplex, plus, minus"):
    val icomDir = RadioColumn.direction(CsvColumn.OffsetDirection, minus = "DUP-", simplex = "Simplex", plus = "DUP+")
    assertEquals(icomDir.extract(sampleChannel, new RadioChannelNameBuilderDefault()), "DUP+")
    assertEquals(icomDir.extract(simplexChannel, new RadioChannelNameBuilderDefault()), "Simplex")
    assertEquals(icomDir.extract(minusChannel, new RadioChannelNameBuilderDefault()), "DUP-")

    val yaesuDir = RadioColumn.direction(CsvColumn.OffsetDirection, minus = "Minus", simplex = "Simplex", plus = "Plus")
    assertEquals(yaesuDir.extract(sampleChannel, new RadioChannelNameBuilderDefault()), "Plus")
    assertEquals(yaesuDir.extract(simplexChannel, new RadioChannelNameBuilderDefault()), "Simplex")
    assertEquals(yaesuDir.extract(minusChannel, new RadioChannelNameBuilderDefault()), "Minus")

  test("Infix DSL operators create valid RadioColumns"):
    import RadioColumn.:=
    val col1 = CsvColumn.ReceiveFrequency := (_.frequency.rx)
    assertEquals(col1.header, "Receive Frequency")
    assertEquals(col1.extract(sampleChannel, new RadioChannelNameBuilderDefault()), "146.520")

    val col2 = CsvColumn.Step := "25 kHz"
    assertEquals(col2.header, "Step")
    assertEquals(col2.extract(sampleChannel, new RadioChannelNameBuilderDefault()), "25 kHz")

  test("RadioExportDefinition overrideColumn replaces column"):
    val base = RadioExportDefinition("Test", List(
      RadioColumn(CsvColumn.ChannelNumber)(_.channelNumber.getOrElse("")),
      RadioColumn.const(CsvColumn.Step, "5 kHz")
    ))
    val overridden = base.overrideColumn(CsvColumn.Step, RadioColumn.const(CsvColumn.Step, "12.5 kHz"))
    assertEquals(overridden.columns.map(_.extract(sampleChannel, new RadioChannelNameBuilderDefault())), List("CH-01", "12.5 kHz"))

  test("RadioExportDefinition removeColumns and insertAfter"):
    val base = RadioExportDefinition("Test", List(
      RadioColumn(CsvColumn.ChannelNumber)(_.channelNumber.getOrElse("")),
      RadioColumn(CsvColumn.ReceiveFrequency)(_.frequency.rx.toString),
      RadioColumn(CsvColumn.TransmitFrequency)(_.frequency.tx.toString)
    ))
    val modified = base
      .insertAfter(CsvColumn.ChannelNumber, RadioColumn.const(CsvColumn.Bank, "1"))
      .removeColumns(CsvColumn.TransmitFrequency)

    assertEquals(modified.headers, List("Channel Number", "Bank", "Receive Frequency"))

  test("Kenwood TH-D75 definition has expected structure"):
    val defn = KenwoodTHD75.definition
    assertEquals(defn.name, "Kenwood TH-D75")
    assertEquals(defn.columns.length, 24)
    assertEquals(defn.headers.head, "Channel Number")
    assertEquals(defn.headers.last, "")

  test("Yaesu FTM-500 and FTM-510 definitions have expected structure"):
    val ftm510 = YaesuFTM510.definition
    assertEquals(ftm510.name, "Yaesu FTM-510")
    assertEquals(ftm510.columns.length, 21)
    assertEquals(ftm510.headers.head, "Channel Number")

    val ftm500 = YaesuFTM500.definition
    assertEquals(ftm500.name, "Yaesu FTM-500")
    assertEquals(ftm500.columns.length, 21)

  test("Icom ID-52Plus definition has expected structure"):
    val id52 = IcomID52Plus.definition
    assertEquals(id52.name, "Icom ID-52Plus")
    assertEquals(id52.columns.length, 25)
    assertEquals(id52.headers(0), "Channel Number")
    assertEquals(id52.headers(1), "Bank")
