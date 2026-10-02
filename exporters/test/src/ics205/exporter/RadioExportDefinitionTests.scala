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
    assertEquals(col.extract(sampleChannel), "146.520")

  test("RadioColumn extracts constant values"):
    val col = RadioColumn.const(CsvColumn.Step, "5 kHz")
    assertEquals(col.header, "Step")
    assertEquals(col.extract(sampleChannel), "5 kHz")

  test("RadioColumn extracts empty string"):
    val col = RadioColumn.empty(CsvColumn.Comment)
    assertEquals(col.header, "Comment")
    assertEquals(col.extract(sampleChannel), "")

  test("RadioColumn.direction formats simplex, plus, minus"):
    val icomDir = RadioColumn.direction(CsvColumn.OffsetDirection, minus = "DUP-", simplex = "Simplex", plus = "DUP+")
    assertEquals(icomDir.extract(sampleChannel), "DUP+")
    assertEquals(icomDir.extract(simplexChannel), "Simplex")
    assertEquals(icomDir.extract(minusChannel), "DUP-")

    val yaesuDir = RadioColumn.direction(CsvColumn.OffsetDirection, minus = "Minus", simplex = "Simplex", plus = "Plus")
    assertEquals(yaesuDir.extract(sampleChannel), "Plus")
    assertEquals(yaesuDir.extract(simplexChannel), "Simplex")
    assertEquals(yaesuDir.extract(minusChannel), "Minus")

  test("Infix DSL operators create valid RadioColumns"):
    import RadioColumn.:=
    val col1 = CsvColumn.ReceiveFrequency := (_.frequency.rx)
    assertEquals(col1.header, "Receive Frequency")
    assertEquals(col1.extract(sampleChannel), "146.520")

    val col2 = CsvColumn.Step := "25 kHz"
    assertEquals(col2.header, "Step")
    assertEquals(col2.extract(sampleChannel), "25 kHz")

  test("RadioExportDefinition withColumn overrides existing column definition"):
    val base = RadioExportDefinition("Test", List(
      RadioColumn(CsvColumn.ChannelNumber)(_.channelNumber.getOrElse("")),
      RadioColumn.const(CsvColumn.Step, "5 kHz")
    ))
    val overridden = base.withColumn(RadioColumn.const(CsvColumn.Step, "12.5 kHz"))
    assertEquals(overridden.orderedColumns.map(_.extract(sampleChannel)), List("CH-01", "12.5 kHz"))

  test("RadioExportDefinition automatically orders columns by CsvColumn ordinal and withoutColumns removes them"):
    val base = RadioExportDefinition("Test", List(
      RadioColumn(CsvColumn.ReceiveFrequency)(_.frequency.rx.toString),
      RadioColumn(CsvColumn.ChannelNumber)(_.channelNumber.getOrElse("")),
      RadioColumn(CsvColumn.TransmitFrequency)(_.frequency.tx.toString)
    ))
    // Bank is ordinal 1 (after ChannelNumber at 0 and before ReceiveFrequency at 2)
    val modified = base
      .withColumn(RadioColumn.const(CsvColumn.Bank, "1"))
      .withoutColumns(CsvColumn.TransmitFrequency)

    assertEquals(modified.headers, List("Channel Number", "Bank", "Receive Frequency"))

  test("Kenwood TH-D75 definition has expected structure"):
    val defn = KenwoodTHD75.definition
    assertEquals(defn.name, "Kenwood TH-D75")
    assertEquals(defn.grouping, Some(RadioGrouping.Group))
    assertEquals(defn.columns.size, 11)
    assertEquals(defn.headers.head, "Channel Number")
    assertEquals(defn.headers.last, "Comment")

  test("Yaesu FTM-500 and FTM-510 definitions have expected structure"):
    val ftm510 = YaesuFTM510.definition
    assertEquals(ftm510.name, "Yaesu FTM-510")
    assertEquals(ftm510.grouping, None)
    assertEquals(ftm510.columns.size, 11)
    assertEquals(ftm510.headers.head, "Channel Number")

    val ftm500 = YaesuFTM500.definition
    assertEquals(ftm500.name, "Yaesu FTM-500")
    assertEquals(ftm500.grouping, None)
    assertEquals(ftm500.columns.size, 11)

  test("Icom ID-52Plus definition has expected structure"):
    val id52 = IcomID52Plus.definition
    assertEquals(id52.name, "Icom ID-52Plus")
    assertEquals(id52.grouping, Some(RadioGrouping.Bank))
    assertEquals(id52.columns.size, 11)
    assertEquals(id52.headers(0), "Channel Number")
    assertEquals(id52.headers(1), "Bank")

  test("RadioExportDefinition withGroupOrBank dynamically overrides group/bank value"):
    val kenwood = KenwoodTHD75.definition.withGroupOrBank("5")
    assertEquals(kenwood.columns(CsvColumn.Group).extract(sampleChannel), "5")

    val icom = IcomID52Plus.definition.withGroupOrBank("B")
    assertEquals(icom.columns(CsvColumn.Bank).extract(sampleChannel), "B")

    val yaesu = YaesuFTM500.definition.withGroupOrBank("99")
    assertEquals(yaesu.columns.contains(CsvColumn.Group), false)
    assertEquals(yaesu.columns.contains(CsvColumn.Bank), false)
