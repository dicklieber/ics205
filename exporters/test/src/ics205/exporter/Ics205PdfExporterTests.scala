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

import ics205.model.*
import org.apache.pdfbox.Loader
import org.apache.pdfbox.text.PDFTextStripper
import java.time.LocalDateTime
import scala.util.Using

class Ics205PdfExporterTests extends munit.FunSuite:
  private val exporter = new Ics205PdfExporter()
  private val channel = Ics205Channel(
    zoneGroup = Some("Z1"),
    channelNumber = Some("1"),
    function = "Command",
    name = "Repeater",
    assignment = "Operations",
    frequency = RxWithOffset(Frequency(BigDecimal("146.94")), Frequency(BigDecimal("-0.6"))),
    bandwidth = Bandwidth.Narrow,
    remarks = "Primary",
    id = "1"
  )
  private val plan = Ics205(incidentName = "Flood Exercise", operationalPeriod = OperationalPeriod(
    Some(LocalDateTime.of(2026, 9, 28, 8, 0)), Some(LocalDateTime.of(2026, 9, 28, 20, 0))),
    channels = Seq(channel), specialInstructions = "Monitor command", preparedBy = Some(PreparedBy("Jane Doe", Some("W1ABC"))),
    prepared = LocalDateTime.of(2026, 9, 27, 17, 30))

  test("exports a flattened landscape FEMA form with current plan data"):
    Using.resource(Loader.loadPDF(exporter.generatePdf(plan))) { pdf =>
      assertEquals(pdf.getNumberOfPages, 1)
      assertEquals(pdf.getPage(0).getMediaBox.getWidth, 792f)
      assertEquals(pdf.getPage(0).getMediaBox.getHeight, 612f)
      assert(pdf.getPage(0).getAnnotations.isEmpty)
      val text = new PDFTextStripper().getText(pdf)
      Seq("Flood Exercise", "09/27/2026", "17:30", "08:00", "20:00", "Repeater", "146.9400", "146.3400", "Monitor command", "Jane Doe", "W1ABC").foreach(value => assert(text.contains(value), text))
    }

  test("all channels stay in order on one expanding page with the footer below them"):
    Seq(0, 8, 9, 17, 50).foreach { count =>
      val input = plan.copy(channels = (1 to count).map(i => channel.copy(name = f"Radio-$i%03d")))
      Using.resource(Loader.loadPDF(exporter.generatePdf(input))) { pdf =>
        assertEquals(pdf.getNumberOfPages, 1)
        assertEquals(pdf.getPage(0).getMediaBox.getWidth, 792f)
        assertEquals(pdf.getPage(0).getMediaBox.getHeight, 612f + math.max(0, count - 8) * 33f)
        val positions = scala.collection.mutable.Map.empty[String, Float]
        val stripper = new PDFTextStripper():
          override protected def writeString(text: String, chars: java.util.List[org.apache.pdfbox.text.TextPosition]): Unit =
            if !chars.isEmpty then positions(text.trim) = chars.get(0).getYDirAdj
            super.writeString(text, chars)
        val text = stripper.getText(pdf)
        assert(text.contains("Flood Exercise"))
        val channelPositions = (1 to count).map { row =>
          val name = f"Radio-$row%03d"
          assert(text.contains(name))
          positions(name)
        }
        assertEquals(channelPositions, channelPositions.sorted)
        channelPositions.sliding(2).filter(_.size == 2).foreach { pair =>
          assert(pair(1) - pair(0) >= 32f)
        }
        channelPositions.lastOption.foreach { last =>
          assert(positions("Monitor command") > last)
        }
      }
    }

  test("maps tone direction, bandwidth, mode, and simplex frequencies correctly"):
    val tone = CtcssFrequency.values.head
    Seq(CtcssMode.None, CtcssMode.Tone, CtcssMode.TSQL).foreach { mode =>
      val fields = exporter.channelFields(channel.copy(ctcss = Ctcss(Some(tone), mode))).toMap
      val toneText = tone.hz.bigDecimal.stripTrailingZeros.toPlainString
      assertEquals(fields("RX ToneNAC"), if mode == CtcssMode.TSQL then toneText else "")
      assertEquals(fields("TX ToneNAC"), if mode == CtcssMode.None then "" else toneText)
    }
    val fields = exporter.channelFields(channel.copy(frequency = RxWithOffset(Frequency(BigDecimal("146.52"))), bandwidth = Bandwidth.Wide, mode = RadioMode.Digital)).toMap
    assertEquals(fields("RX Freq N or W"), "146.5200 W")
    assertEquals(fields("TX Freq N or W"), "146.5200 W")
    assertEquals(fields("Mode A D or M"), "D")

  test("optional metadata and unsupported characters do not prevent export"):
    val input = plan.copy(incidentName = "Exercise 🚒", preparedBy = None, operationalPeriod = OperationalPeriod(), channels = Seq.empty)
    Using.resource(Loader.loadPDF(exporter.generatePdf(input))) { pdf =>
      assert(new PDFTextStripper().getText(pdf).contains("Exercise ?"))
    }

  test("long multiline instructions and remarks retain their final words"):
    val input = plan.copy(
      specialInstructions = ("Monitor command and report conditions. " * 100) + "\nInstructionsEnd",
      channels = Seq(channel.copy(remarks = ("Relay via operations. " * 20) + "RemarksEnd")))
    Using.resource(Loader.loadPDF(exporter.generatePdf(input))) { pdf =>
      val text = new PDFTextStripper().getText(pdf)
      assert(text.contains("InstructionsEnd"))
      assert(text.contains("RemarksEnd"))
    }
