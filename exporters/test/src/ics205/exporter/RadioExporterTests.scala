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
    frequency = RxWithOffset(Frequency(BigDecimal("446.000")), Frequency(BigDecimal("5.000"))),
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
    val csv = exporter.generateCsv("TH-D75", sampleIcs205, includeHeader = true)
    val parser = CSVParser.parse(new StringReader(csv), CSVFormat.DEFAULT)
    val records = parser.getRecords.asScala.toList
    assertEquals(records.length, 3) // 1 header + 2 channels

    val header = records(0).toList.asScala.toList
    assertEquals(header.length, 24)
    assertEquals(header(0), "")
    assertEquals(header(1), "Receive Frequency")
    assertEquals(header(2), "Transmit Frequency")
    assertEquals(header(6), "Name")

    val row1 = records(1).toList.asScala.toList
    assertEquals(row1.length, 24)
    assertEquals(row1(0), "CH-01")
    assertEquals(row1(1), "146.520")
    assertEquals(row1(2), "147.120") // 146.520 + 0.600
    assertEquals(row1(4), "Simplex") // constant
    assertEquals(row1(6), "TAC1 Operations") // RadioChannelName: name + assignment
    assertEquals(row1(21), "Zone 1")
    assertEquals(row1(22), "Primary tactical channel")

    val row2 = records(2).toList.asScala.toList
    assertEquals(row2.length, 24)
    assertEquals(row2(0), "CH-02")
    assertEquals(row2(1), "446.000")
    assertEquals(row2(6), "CMD Incident Cmd")

  test("RadioExporter exports FTM-510 CSV with header"):
    val csv = exporter.generateCsv("FTM-510", sampleIcs205, includeHeader = true)
    val parser = CSVParser.parse(new StringReader(csv), CSVFormat.DEFAULT)
    val records = parser.getRecords.asScala.toList
    assertEquals(records.length, 3)

    val header = records(0).toList.asScala.toList
    assertEquals(header.length, 21)
    assertEquals(header(0), "")
    assertEquals(header(1), "Receive Frequency")
    assertEquals(header(2), "Transmit Frequency")
    assertEquals(header(6), "AMS")
    assertEquals(header(7), "Name")
    assertEquals(header(19), "Comment")
    assertEquals(header(20), "")

    val row1 = records(1).toList.asScala.toList
    assertEquals(row1.length, 21)
    assertEquals(row1(0), "CH-01")
    assertEquals(row1(1), "146.520")
    assertEquals(row1(2), "147.120")
    assertEquals(row1(6), "N")
    assertEquals(row1(7), "TAC1 Operations")
    assertEquals(row1(19), "Primary tactical channel")
    assertEquals(row1(20), "")

  test("RadioExporter exports CSV without header when includeHeader is false"):
    val csv = exporter.generateCsv("TH-D75.json", sampleIcs205, includeHeader = false)
    val parser = CSVParser.parse(new StringReader(csv), CSVFormat.DEFAULT)
    val records = parser.getRecords.asScala.toList
    assertEquals(records.length, 2) // 2 channels only, no header

    val row1 = records(0).toList.asScala.toList
    assertEquals(row1(0), "CH-01")
    assertEquals(row1(1), "146.520")

  test("RadioExporter throws IllegalArgumentException for missing resource"):
    intercept[IllegalArgumentException]:
      exporter.generateCsv("non-existent-radio", sampleIcs205, includeHeader = true)
