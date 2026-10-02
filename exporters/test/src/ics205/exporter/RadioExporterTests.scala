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

import ics205.model.{Frequency, Ics205, Ics205Channel, OperationalPeriod, RadioMode, RxWithOffset}
import org.apache.commons.csv.CSVParser
import org.apache.commons.csv.CSVFormat
import java.io.StringReader
import scala.jdk.CollectionConverters.*

class RadioExporterTests extends munit.FunSuite:
  val sampleChannel1 = Ics205Channel(
    id = "1",
    zoneGroup = Some("Zone 1"),
    channelNumber = Some("CH-01"),
    function = "Tac 1",
    name = "TAC1",
    assignment = "Operations",
    frequency = RxWithOffset(Frequency(BigDecimal("146.520")), Frequency(BigDecimal("0.600"))),
    mode = RadioMode.Fm,
    remarks = "Primary tactical channel"
  )

  val sampleChannel2 = Ics205Channel(
    id = "2",
    zoneGroup = Some("Zone 1"),
    channelNumber = Some("CH-02"),
    function = "Command",
    name = "CMD",
    assignment = "Incident Command",
    frequency = RxWithOffset(Frequency(BigDecimal("446.000")), Frequency(BigDecimal("0.000"))),
    mode = RadioMode.Fm,
    remarks = "Command net"
  )

  val sampleIcs205 = Ics205(
    incidentName = "Test Incident",
    operationalPeriod = OperationalPeriod(),
    channels = Seq(sampleChannel1, sampleChannel2)
  )

  val exporter = new RadioExporter()

  test("RadioExporter exports CSV with header when includeHeader is true"):
    val csv = exporter.generateCsv("Kenwood TH-D75", sampleIcs205, includeHeader = true)
    val parser = CSVParser.parse(new StringReader(csv), CSVFormat.DEFAULT)
    val records = parser.getRecords.asScala.toList
    assertEquals(records.length, 3) // 1 header + 2 channels

    val header = records(0).toList.asScala.toList
    assertEquals(header.length, 11)
    assertEquals(header(0), "Channel Number")
    assertEquals(header(1), "Receive Frequency")
    assertEquals(header(2), "Offset Frequency")
    assertEquals(header(3), "Offset Direction")
    assertEquals(header(4), "Operating Mode")
    assertEquals(header(5), "Name")
    assertEquals(header(9), "Group")
    assertEquals(header(10), "Comment")

    val row1 = records(1).toList.asScala.toList
    assertEquals(row1.length, 11)
    assertEquals(row1(0), "CH-01")
    assertEquals(row1(1), "146.520")
    assertEquals(row1(2), "0.600")
    assertEquals(row1(3), "Plus") // Kenwood direction for +0.600 offset
    assertEquals(row1(5), "TAC1 Operations") // RadioChannelName: name + assignment
    assertEquals(row1(9), "1")
    assertEquals(row1(10), "Primary tactical channel")

    val row2 = records(2).toList.asScala.toList
    assertEquals(row2.length, 11)
    assertEquals(row2(0), "CH-02")
    assertEquals(row2(1), "446.000")
    assertEquals(row2(3), "Simplex") // 0.0 offset -> Simplex
    assertEquals(row2(5), "CMD Incident Cmd")
    assertEquals(row2(9), "1")
    assertEquals(row2(10), "Command net")

  test("RadioExporter exports FTM-510 CSV with header"):
    val csv = exporter.generateCsv("Yaesu FTM-510", sampleIcs205, includeHeader = true)
    val parser = CSVParser.parse(new StringReader(csv), CSVFormat.DEFAULT)
    val records = parser.getRecords.asScala.toList
    assertEquals(records.length, 3)

    val header = records(0).toList.asScala.toList
    assertEquals(header.length, 11)
    assertEquals(header(0), "Channel Number")
    assertEquals(header(1), "Receive Frequency")
    assertEquals(header(2), "Offset Frequency")
    assertEquals(header(3), "Offset Direction")
    assertEquals(header(4), "Operating Mode")
    assertEquals(header(5), "AMS")
    assertEquals(header(6), "Name")
    assertEquals(header(10), "Comment")

    val row1 = records(1).toList.asScala.toList
    assertEquals(row1.length, 11)
    assertEquals(row1(0), "CH-01")
    assertEquals(row1(1), "146.520")
    assertEquals(row1(2), "0.600")
    assertEquals(row1(3), "Plus")
    assertEquals(row1(4), "Fm")
    assertEquals(row1(5), "N")
    assertEquals(row1(6), "TAC1 Operations")
    assertEquals(row1(10), "Primary tactical channel")

  test("RadioExporter exports FTM-500 CSV with header"):
    val csv = exporter.generateCsv("Yaesu FTM-500", sampleIcs205, includeHeader = true)
    val parser = CSVParser.parse(new StringReader(csv), CSVFormat.DEFAULT)
    val records = parser.getRecords.asScala.toList
    assertEquals(records.length, 3)
    assertEquals(records(0).get(0), "Channel Number")
    assertEquals(records(1).get(6), "TAC1 Operations")

  test("RadioExporter exports ID-52Plus CSV with header"):
    val csv = exporter.generateCsv("Icom ID-52Plus", sampleIcs205, includeHeader = true)
    val parser = CSVParser.parse(new StringReader(csv), CSVFormat.DEFAULT)
    val records = parser.getRecords.asScala.toList
    assertEquals(records.length, 3)

    val header = records(0).toList.asScala.toList
    assertEquals(header.length, 11)
    assertEquals(header(0), "Channel Number")
    assertEquals(header(1), "Bank")
    assertEquals(header(2), "Receive Frequency")
    assertEquals(header(3), "Offset Frequency")
    assertEquals(header(4), "Offset Direction")
    assertEquals(header(5), "Operating Mode")
    assertEquals(header(6), "Name")
    assertEquals(header(7), "Tone Mode")
    assertEquals(header(8), "CTCSS")
    assertEquals(header(9), "Rx CTCSS")
    assertEquals(header(10), "Comment")

    val row1 = records(1).toList.asScala.toList
    assertEquals(row1.length, 11)
    assertEquals(row1(0), "CH-01")
    assertEquals(row1(1), "1")
    assertEquals(row1(2), "146.520")
    assertEquals(row1(3), "0.600")
    assertEquals(row1(4), "DUP+")
    assertEquals(row1(5), "Fm")
    assertEquals(row1(6), "TAC1 Operations")
    assertEquals(row1(10), "Primary tactical channel")

  test("RadioExporter exports CSV without header when includeHeader is false"):
    val csv = exporter.generateCsv("Kenwood TH-D75", sampleIcs205, includeHeader = false)
    val parser = CSVParser.parse(new StringReader(csv), CSVFormat.DEFAULT)
    val records = parser.getRecords.asScala.toList
    assertEquals(records.length, 2) // 2 channels only, no header

    val row1 = records(0).toList.asScala.toList
    assertEquals(row1(0), "CH-01")
    assertEquals(row1(1), "146.520")

  test("RadioExporter throws IllegalArgumentException for missing resource"):
    intercept[IllegalArgumentException]:
      exporter.generateCsv("non-existent-radio", sampleIcs205, includeHeader = true)
