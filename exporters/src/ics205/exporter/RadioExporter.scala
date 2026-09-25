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

import ics205.model.Ics205
import jakarta.inject.{Inject, Singleton}
import org.apache.commons.csv.{CSVFormat, CSVPrinter}
import scala.jdk.CollectionConverters.*

@Singleton
class RadioExporter @Inject()(definitions: RadioExportDefinitions):

  def this() = this(new RadioExportDefinitions())

  def generateCsv(baseName: String, ics205: Ics205, includeHeader: Boolean): String =
    val definition = definitions.get(baseName)
    val writer = new java.io.StringWriter()
    val printer = new CSVPrinter(writer, CSVFormat.DEFAULT)
    try
      if includeHeader then
        val headerRow = definition.fields.map(_.headerName)
        printer.printRecord(headerRow.asJava)

      for channel <- ics205.channels do
        val row = definition.fields.map(_.extract(channel, definition.radioChannelNameBuilder))
        printer.printRecord(row.asJava)

      printer.flush()
      writer.toString
    finally
      printer.close()
