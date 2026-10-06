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
import scala.util.Try

/** The same field names are used by the HTML form and its server-side validation. */
private[web] object Ics205Form:
  def fields(plan: Ics205): Map[String, String] =
    Map(
      "incidentName" -> plan.incidentName,
      "prepared" -> plan.prepared.toString,
      "from" -> plan.operationalPeriod.from.fold("")(_.toString),
      "to" -> plan.operationalPeriod.to.fold("")(_.toString),
      "specialInstructions" -> plan.specialInstructions,
      "preparedBy" -> plan.preparedBy.fold("")(_.name),
      "callsign" -> plan.preparedBy.flatMap(_.callsign).getOrElse(""),
      "rowCount" -> plan.channels.size.toString
    ) ++ plan.channels.zipWithIndex.flatMap { (channel, index) =>
      Map(
        "id" -> channel.id, "zoneGroup" -> channel.zoneGroup.getOrElse(""),
        "channelNumber" -> channel.channelNumber.getOrElse(""), "function" -> channel.function,
        "name" -> channel.name, "extra" -> channel.extra, "assignment" -> channel.assignment,
        "rx" -> channel.frequency.rx.mhz.bigDecimal.toPlainString,
        "offset" -> channel.frequency.offset.mhz.bigDecimal.toPlainString,
        "bandwidth" -> channel.bandwidth.toString,
        "mode" -> channel.mode.toString,
        "remarks" -> channel.remarks,
        "ctcssMode" -> channel.ctcss.mode.toString,
        "ctcssFrequency" -> channel.ctcss.frequency.fold("")(_.hz.bigDecimal.toPlainString)
      )
        .map((key, value) => s"row.$index.$key" -> value)
    }

  def decode(data: Map[String, String], original: Ics205): Either[String, Ics205] =
    def text(key: String): String = data.getOrElse(key, "")
    def optional(key: String): Option[String] = Option(text(key)).filter(_.nonEmpty)
    def fail(message: String): Nothing = throw new IllegalArgumentException(message)
    def parse[A](label: String)(value: => A): A =
      Try(value).getOrElse(fail(s"$label is invalid."))
    def date(key: String, label: String): Option[LocalDateTime] =
      optional(key).map(value => parse(label)(LocalDateTime.parse(value)))
    Try {
      val count = parse("Channel count")(text("rowCount").toInt)
      if count < 0 || count > 1000 then fail("Channel count must be between 0 and 1000.")
      val from = date("from", "Operational period start")
      val to = date("to", "Operational period end")
      if from.exists(start => to.exists(_.isBefore(start))) then
        fail("Operational period end must be at or after its start.")
      val channels = (0 until count).map { index =>
        val prefix = s"row.$index."
        val label = s"Row ${index + 1}"
        def get(key: String): String = text(prefix + key)
        def opt(key: String): Option[String] = optional(prefix + key)
        val mode = parse(s"$label mode")(RadioMode.valueOf(get("mode")))
        val isOther = mode == RadioMode.Other
        def rowValue[A](label: String, fallback: => A)(value: => A): A =
          if isOther then Try(value).getOrElse(fallback) else parse(label)(value)
        def number(key: String): BigDecimal = rowValue(s"$label $key", BigDecimal(0))(BigDecimal(get(key)))
        val ctcssMode = rowValue(s"$label CTCSS mode", CtcssMode.None)(CtcssMode.valueOf(get("ctcssMode")))
        val ctcssFrequency = if ctcssMode == CtcssMode.None then None
          else if isOther then Try(BigDecimal(get("ctcssFrequency"))).toOption.flatMap(CtcssFrequency.fromHz)
          else Some(CtcssFrequency.fromHz(number("ctcssFrequency")).getOrElse(
            fail(s"$label: select a standard CTCSS frequency for Tone or TSQL.")
          ))
        val rx = number("rx")
        val offset = number("offset")
        if !isOther && (rx <= 0 || rx + offset <= 0) then fail(s"$label: RX and derived TX frequencies must be positive.")
        val id = get("id")
        if id.isEmpty then fail(s"$label: missing channel ID.")
        Ics205Channel(
          id = id, zoneGroup = opt("zoneGroup"), channelNumber = opt("channelNumber"),
          function = get("function"), name = get("name"), assignment = get("assignment"),
          frequency = RxWithOffset(Frequency(rx), Frequency(offset)),
          mode = mode,
          bandwidth = opt("bandwidth").map(value => rowValue(s"$label bandwidth", Bandwidth.Wide)(Bandwidth.valueOf(value))).getOrElse(Bandwidth.Wide),
          ctcss = Ctcss(ctcssFrequency, ctcssMode),
          remarks = get("remarks"),
          extra = get("extra")
        )
      }
      if channels.map(_.id).distinct.size != channels.size then fail("Channel IDs must be unique.")
      original.copy(
        incidentName = text("incidentName"), operationalPeriod = OperationalPeriod(from, to),
        prepared = date("prepared", "Prepared date/time").getOrElse(fail("Prepared date/time is required.")),
        channels = channels, specialInstructions = text("specialInstructions"),
        preparedBy = if text("preparedBy").isEmpty && text("callsign").isEmpty then None
          else Some(PreparedBy(text("preparedBy"), optional("callsign")))
      )
    }.toEither.left.map(_.getMessage)
