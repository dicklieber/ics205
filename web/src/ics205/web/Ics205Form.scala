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
      val digital = channel.digital match
        case Some(DigitalParameters.Dmr(cc, ts, tg)) =>
          Map("digitalType" -> "DMR", "colorCode" -> cc.toString, "timeSlot" -> ts.toString, "talkGroup" -> tg.toString)
        case Some(DigitalParameters.DStar(ur, r1, r2)) =>
          Map("digitalType" -> "D-STAR", "urCall" -> ur.getOrElse(""), "rpt1" -> r1.getOrElse(""), "rpt2" -> r2.getOrElse(""))
        case Some(DigitalParameters.P25(nac, tg)) =>
          Map("digitalType" -> "P25", "nac" -> nac.getOrElse(""), "talkGroup" -> tg.fold("")(_.toString))
        case None => Map.empty[String, String]
      (Map(
        "id" -> channel.id, "zoneGroup" -> channel.zoneGroup.getOrElse(""),
        "channelNumber" -> channel.channelNumber.getOrElse(""), "function" -> channel.function,
        "name" -> channel.name, "assignment" -> channel.assignment,
        "rx" -> channel.frequency.rx.mhz.bigDecimal.toPlainString,
        "offset" -> channel.frequency.offset.mhz.bigDecimal.toPlainString,
        "bandwidth" -> channel.bandwidth.fold("")(_.toString),
        "mode" -> channel.mode.toString,
        "remarks" -> channel.remarks.getOrElse("")
      ) ++ signalingFields("rxSignal", channel.receiveSignaling) ++
        signalingFields("txSignal", channel.transmitSignaling) ++ digital)
        .map((key, value) => s"row.$index.$key" -> value)
    }

  private def signalingFields(prefix: String, value: Option[Signaling]): Map[String, String] =
    val (kind, code) = value match
      case None => ("", "")
      case Some(Signaling.Ctcss(hz)) => ("CTCSS", hz.toString)
      case Some(Signaling.Dcs(code)) => ("DCS", f"$code%03d")
      case Some(Signaling.Nac(code)) => ("NAC", code)
    Map(s"${prefix}Type" -> kind, s"${prefix}Value" -> code)

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
        def number(key: String): BigDecimal = parse(s"$label $key")(BigDecimal(get(key)))
        def int(key: String): Int = parse(s"$label $key")(get(key).toInt)
        def signal(key: String): Option[Signaling] =
          val value = get(key + "Value").trim
          get(key + "Type") match
            case "" =>
              if value.nonEmpty then fail(s"$label: choose a signaling type for $value.")
              None
            case "CTCSS" =>
              val hz = parse(s"$label CTCSS tone")(BigDecimal(value))
              if hz <= 0 then fail(s"$label: CTCSS tone must be positive.")
              Some(Signaling.Ctcss(hz))
            case "DCS" =>
              if !value.matches("[0-7]{3}") then fail(s"$label: DCS requires three octal digits, such as 023.")
              Some(Signaling.Dcs(value.toInt))
            case "NAC" =>
              if !value.matches("(?i)[0-9a-f]{3}") then fail(s"$label: NAC requires three hexadecimal digits.")
              Some(Signaling.Nac(value))
            case _ => fail(s"$label: unknown signaling type.")
        val rx = number("rx")
        val offset = number("offset")
        if rx <= 0 || rx + offset <= 0 then fail(s"$label: RX and derived TX frequencies must be positive.")
        val digital = get("digitalType") match
          case "" => None
          case "DMR" =>
            val cc = int("colorCode")
            val ts = int("timeSlot")
            val tg = int("talkGroup")
            if cc < 0 || cc > 15 || (ts != 1 && ts != 2) || tg < 0 then
              fail(s"$label: DMR requires color code 0–15, time slot 1 or 2, and a nonnegative talkgroup.")
            Some(DigitalParameters.Dmr(cc, ts, tg))
          case "D-STAR" => Some(DigitalParameters.DStar(opt("urCall"), opt("rpt1"), opt("rpt2")))
          case "P25" =>
            val nac = opt("nac")
            if nac.exists(v => !v.matches("(?i)[0-9a-f]{3}")) then fail(s"$label: P25 NAC requires three hexadecimal digits.")
            val tg = opt("talkGroup").map(_ => int("talkGroup"))
            if tg.exists(_ < 0) then fail(s"$label: talkgroup must be nonnegative.")
            Some(DigitalParameters.P25(nac, tg))
          case _ => fail(s"$label: unknown digital system.")
        val id = get("id")
        if id.isEmpty then fail(s"$label: missing channel ID.")
        Ics205Channel(
          id = id, zoneGroup = opt("zoneGroup"), channelNumber = opt("channelNumber"),
          function = get("function"), name = get("name"), assignment = get("assignment"),
          frequency = RxWithOffset(Frequency(rx), Frequency(offset)),
          mode = parse(s"$label mode")(RadioMode.valueOf(get("mode"))),
          bandwidth = opt("bandwidth").map(value => parse(s"$label bandwidth")(Bandwidth.valueOf(value))),
          transmitSignaling = signal("txSignal"), receiveSignaling = signal("rxSignal"),
          digital = digital, remarks = opt("remarks")
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
