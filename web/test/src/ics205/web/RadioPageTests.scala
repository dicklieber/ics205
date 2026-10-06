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

package ics205.web

import ics205.model.*
import java.time.LocalDateTime
import javax.xml.parsers.DocumentBuilderFactory
import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets.UTF_8
import org.w3c.dom.Element

class RadioPageTests extends munit.FunSuite:
  private val plan = Ics205(formatVersion = "1.2", incidentName = "Exercise <North>",
    operationalPeriod = OperationalPeriod(Some(LocalDateTime.of(2026, 9, 28, 8, 0))),
    prepared = LocalDateTime.of(2026, 9, 27, 17, 30),
    preparedBy = Some(PreparedBy("Alex", Some("W9ABC"))), specialInstructions = "First line\nSecond line",
    channels = Seq(Ics205Channel(Some("Zone 1"), Some("7"), "Command", "Repeater", "All",
      RxWithOffset(mhz"146.94000", mhz"-0.600"), RadioMode.Digital, Bandwidth.Narrow,
      Ctcss(Some(CtcssFrequency.Hz100_0), CtcssMode.TSQL), "<script>alert(1)</script>", id = "channel-id")))

  private def table(html: String): Element =
    val markup = html.substring(html.indexOf("<table>"), html.indexOf("</table>") + 8)
    DocumentBuilderFactory.newInstance().newDocumentBuilder()
      .parse(new ByteArrayInputStream(markup.getBytes(UTF_8))).getDocumentElement
  private def elements(parent: Element, tag: String): Seq[Element] =
    val nodes = parent.getElementsByTagName(tag)
    (0 until nodes.getLength).map(i => nodes.item(i).asInstanceOf[Element])

  test("two header rows expand nested fields and align with all channel values"):
    val markup = table(RadioPage.render(plan))
    val headerRows = elements(elements(markup, "thead").head, "tr")
    assertEquals(headerRows.size, 2)
    val first = elements(headerRows.head, "th")
    assertEquals(first.map(_.getTextContent), Seq("function", "name", "assignment", "frequency", "mode", "bandwidth", "ctcss", "remarks"))
    first.foreach { cell =>
      if Set("frequency", "ctcss").contains(cell.getTextContent) then
        assertEquals(cell.getAttribute("colspan"), "2")
        assertEquals(cell.getAttribute("scope"), "colgroup")
      else assertEquals(cell.getAttribute("rowspan"), "2")
    }
    assertEquals(elements(headerRows(1), "th").map(_.getTextContent),
      Seq("rxFrequency (MHz)", "offset (MHz)", "frequency (Hz)", "mode"))
    assertEquals(elements(elements(markup, "tbody").head, "td").map(_.getTextContent),
      Seq("Command", "Repeater", "All", "146.94000", "-0.600", "Digital", "Narrow", "100.0", "TSQL", "<script>alert(1)</script>"))

  test("radio columns have radio css class on headers and cells"):
    val markup = table(RadioPage.render(plan))
    val headerRows = elements(elements(markup, "thead").head, "tr")
    val firstRowHeaders = elements(headerRows.head, "th")
    val secondRowHeaders = elements(headerRows(1), "th")
    val bodyCells = elements(elements(markup, "tbody").head, "td")

    val radioFirstRowHeaders = firstRowHeaders.filter(_.getAttribute("class") == "radio").map(_.getTextContent)
    assertEquals(radioFirstRowHeaders, Seq("frequency", "bandwidth", "ctcss"))

    val nonRadioFirstRowHeaders = firstRowHeaders.filter(_.getAttribute("class") != "radio").map(_.getTextContent)
    assertEquals(nonRadioFirstRowHeaders, Seq("function", "name", "assignment", "mode", "remarks"))

    val radioSecondRowHeaders = secondRowHeaders.filter(_.getAttribute("class") == "radio").map(_.getTextContent)
    assertEquals(radioSecondRowHeaders, Seq("rxFrequency (MHz)", "offset (MHz)", "frequency (Hz)", "mode"))

    val radioBodyCells = bodyCells.filter(_.getAttribute("class") == "radio").map(_.getTextContent)
    assertEquals(radioBodyCells, Seq("146.94000", "-0.600", "Narrow", "100.0", "TSQL"))

    val nonRadioBodyCells = bodyCells.filter(_.getAttribute("class") != "radio").map(_.getTextContent)
    assertEquals(nonRadioBodyCells, Seq("Command", "Repeater", "All", "Digital", "<script>alert(1)</script>"))

  test("shows every plan field, nested metadata, and escaped text"):
    val html = RadioPage.render(plan)
    Seq("formatVersion", "1.2", "incidentName", "operationalPeriod", "from", "to", "2026-09-28T08:00",
      "prepared", "2026-09-27T17:30", "preparedBy", "Alex", "W9ABC", "specialInstructions", "First line\nSecond line").foreach(value => assert(html.contains(value), value))
    assert(html.contains("Exercise &lt;North&gt;"))
    assert(!html.contains("<script>"))
    assert(html.contains("&lt;script&gt;"))

  test("missing optional values are blank and empty plans retain table headers"):
    val blank = plan.copy(preparedBy = None, operationalPeriod = OperationalPeriod(), channels = Seq.empty)
    val html = RadioPage.render(blank)
    assert(!html.contains("Some("))
    assert(!html.contains("W9ABC"))
    val markup = table(html)
    assertEquals(elements(elements(markup, "thead").head, "tr").size, 2)
    val cell = elements(elements(markup, "tbody").head, "td").head
    assertEquals(cell.getAttribute("colspan"), "10")
    assertEquals(cell.getTextContent, "No channels.")
