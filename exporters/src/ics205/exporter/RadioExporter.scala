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
    generateCsv(baseName, ics205, includeHeader, None)

  def generateCsv(baseName: String, ics205: Ics205, includeHeader: Boolean, groupOrBank: Option[String]): String =
    val definition = definitions.get(baseName)
    generateCsv(definition, ics205, includeHeader, groupOrBank)

  def generateCsv(definition: RadioExportDefinition, ics205: Ics205, includeHeader: Boolean): String =
    generateCsv(definition, ics205, includeHeader, None)

  def generateCsv(definition: RadioExportDefinition, ics205: Ics205, includeHeader: Boolean, groupOrBank: Option[String]): String =
    val effectiveDef = groupOrBank.filter(_.trim.nonEmpty) match
      case Some(v) => definition.withGroupOrBank(v.trim)
      case None    => definition

    val writer = new java.io.StringWriter()
    val printer = new CSVPrinter(writer, CSVFormat.DEFAULT)
    try
      if includeHeader then
        val headerRow = effectiveDef.headers
        printer.printRecord(headerRow.asJava)

      for channel <- ics205.channels do
        val row = effectiveDef.orderedColumns.map(_.extract(channel, effectiveDef.channelNameBuilder))
        printer.printRecord(row.asJava)

      printer.flush()
      writer.toString
    finally
      printer.close()
