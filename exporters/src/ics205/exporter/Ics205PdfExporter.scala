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
import jakarta.inject.{Inject, Singleton}
import org.apache.pdfbox.Loader
import org.apache.pdfbox.pdmodel.{PDDocument, PDPage, PDPageContentStream}
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.multipdf.LayerUtility
import org.apache.pdfbox.util.Matrix
import org.apache.pdfbox.pdmodel.font.{PDType1Font, Standard14Fonts}
import java.io.ByteArrayOutputStream
import java.time.format.DateTimeFormatter
import scala.util.Using

/** Populates the FEMA ICS 205 v3.1 form on one page, extending the channel table as needed. */
@Singleton
class Ics205PdfExporter @Inject()():
  private val dateFormat = DateTimeFormatter.ofPattern("MM/dd/yyyy")
  private val timeFormat = DateTimeFormatter.ofPattern("HH:mm")
  private val template = Using.resource(getClass.getResourceAsStream("/ics205/ics205-template.pdf"))(_.readAllBytes())

  def generatePdf(plan: Ics205): Array[Byte] = Using.resource(new PDDocument()) { output =>
    Using.resource(Loader.loadPDF(template)) { source =>
      val form = source.getDocumentCatalog.getAcroForm
      val font = new PDType1Font(Standard14Fonts.FontName.HELVETICA)
      val rowHeight = 33f
      // Top edge of the template's heavy section 5 border.
      val tableBottom = 193.32f
      val extraHeight = math.max(0, plan.channels.size - 8) * rowHeight
      val page = new PDPage(new PDRectangle(792f, 612f + extraHeight))
      output.addPage(page)
      val artwork = new LayerUtility(output).importPageAsForm(source, 0)
      val content = new PDPageContentStream(output, page)
      try
        // Keep the header and original eight rows at the top, and the footer at the bottom.
        // The space between them accommodates additional full-height channel rows.
        def drawArtwork(bottom: Float, height: Float, shift: Float): Unit =
          content.saveGraphicsState()
          content.addRect(0, bottom + shift, 792f, height)
          content.clip()
          content.transform(Matrix.getTranslateInstance(0, shift))
          content.drawForm(artwork)
          content.restoreGraphicsState()
        drawArtwork(tableBottom, 612f - tableBottom, extraHeight)
        drawArtwork(0, tableBottom, 0)
        if extraHeight > 0 then
          // Match the template's 0.48 pt cell rules and 1.44 pt outer border.
          val columns = Seq(67.56f, 90f, 175.56f, 274.56f, 337.32f,
            382.56f, 436.56f, 481.56f, 535.56f, 594f)
          val left = 36.12f
          val right = 755.88f
          content.setLineWidth(0.48f)
          columns.foreach { x =>
            content.moveTo(x, tableBottom)
            content.lineTo(x, tableBottom + extraHeight)
          }
          (1 to plan.channels.size - 8).foreach { row =>
            val y = tableBottom + row * rowHeight
            content.moveTo(left, y)
            content.lineTo(right, y)
          }
          content.stroke()
          content.setLineWidth(1.44f)
          Seq(left, right).foreach { x =>
            content.moveTo(x, tableBottom)
            content.lineTo(x, tableBottom + extraHeight)
          }
          content.stroke()
        def set(name: String, value: String, rowShift: Float = 0): Unit =
          val original = form.getField(name).getWidgets.get(0).getRectangle
          val shift = (if original.getLowerLeftY >= tableBottom then extraHeight else 0f) + rowShift
          val rect = new PDRectangle(original.getLowerLeftX, original.getLowerLeftY + shift,
            original.getWidth, original.getHeight)
          // The official form uses WinAnsi. Replace unsupported glyphs rather than failing an export.
          val printable = value.codePoints().toArray.map { code =>
            val s = new String(Character.toChars(code))
            if s == "\n" then s
            else if Character.isISOControl(code) then " "
            else
              try
                font.encode(s)
                s
              catch case _: IllegalArgumentException => "?"
          }.mkString
          // Keep words (especially frequencies) intact and fit the original field.
          def wrap(size: Float): Seq[String] =
            printable.split("\n", -1).toSeq.flatMap { paragraph =>
              val lines = scala.collection.mutable.ArrayBuffer.empty[String]
              var line = ""
              paragraph.split("\\s+").filter(_.nonEmpty).foreach { word =>
                val candidate = if line.isEmpty then word else s"$line $word"
                if line.nonEmpty && font.getStringWidth(candidate) * size / 1000 > rect.getWidth - 4 then
                  lines += line
                  line = word
                else line = candidate
              }
              lines += line
              lines.toSeq
            }
          val widestWord = printable.split("\\s+").map(font.getStringWidth).maxOption.getOrElse(0f)
          var size = if widestWord == 0 then 10f else math.min(10f, (rect.getWidth - 4) * 1000 / widestWord)
          var lines = wrap(size)
          while lines.size * size * 1.2f > rect.getHeight - 4 && size > 0.01f do
            size *= 0.9f
            lines = wrap(size)
          content.beginText()
          content.setFont(font, size)
          content.setLeading(size * 1.2f)
          content.newLineAtOffset(rect.getLowerLeftX + 2, rect.getUpperRightY - 2 - size)
          lines.foreach { line =>
            content.showText(line)
            content.newLine()
          }
          content.endText()
        set("1 Incident Name_8", plan.incidentName)
        set("2 Date/Time Prepared", s"${plan.prepared.format(dateFormat)}\n${plan.prepared.format(timeFormat)}")
        set("Date From", plan.operationalPeriod.from.fold("")(_.format(dateFormat)))
        set("Date To", plan.operationalPeriod.to.fold("")(_.format(dateFormat)))
        set("Time From", plan.operationalPeriod.from.fold("")(_.format(timeFormat)))
        set("Time To", plan.operationalPeriod.to.fold("")(_.format(timeFormat)))
        set("5 Special Instructions", plan.specialInstructions)
        set("6 Prepared by Communications Unit Leader Name", plan.preparedBy.fold("")(p => (Seq(p.name) ++ p.callsign.toSeq).filter(_.nonEmpty).mkString(" / ")))
        set("IAP Page_4", "1")
        set("DateTime_8", s"${plan.prepared.format(dateFormat)} ${plan.prepared.format(timeFormat)}")
        plan.channels.zipWithIndex.foreach { (channel, row) =>
          val templateRow = math.min(row + 1, 8)
          val rowShift = -math.max(0, row - 7) * rowHeight
          channelFields(channel).foreach { (name, value) => set(s"${name}Row$templateRow", value, rowShift) }
        }
      finally content.close()
    }
    output.getDocumentInformation.setTitle(s"ICS 205 - ${plan.incidentName}")
    val bytes = new ByteArrayOutputStream()
    output.save(bytes)
    bytes.toByteArray
  }

  private[exporter] def channelFields(channel: Ics205Channel): Seq[(String, String)] =
    val bandwidth = if channel.bandwidth == Bandwidth.Narrow then "N" else "W"
    def frequency(value: Frequency) = s"${value.mhz.setScale(4, BigDecimal.RoundingMode.HALF_UP)} $bandwidth"
    val tone = channel.ctcss.frequency.fold("")(_.hz.bigDecimal.stripTrailingZeros.toPlainString)
    val mode = channel.mode match
      case RadioMode.Fm | RadioMode.Am => "A"
      case RadioMode.Digital => "D"
      case RadioMode.Other => "Other"
    Seq(
      "Zone Grp" -> channel.zoneGroup.getOrElse(""),
      "Ch " -> channel.channelNumber.getOrElse(""),
      "Function" -> channel.function,
      "Channel NameTrunked Radio System Talkgroup" -> channel.name,
      "Assignment" -> channel.assignment,
      "RX Freq N or W" -> frequency(channel.frequency.rx),
      "RX ToneNAC" -> (if channel.ctcss.mode == CtcssMode.TSQL then tone else ""),
      "TX Freq N or W" -> frequency(channel.frequency.tx),
      "TX ToneNAC" -> (if channel.ctcss.mode != CtcssMode.None then tone else ""),
      "Mode A D or M" -> mode,
      "Remarks" -> channel.remarks
    )
