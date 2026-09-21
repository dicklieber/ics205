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
import java.time.format.DateTimeFormatter
import scalatags.Text.all.*

/** Printable adaptation of the first page of the ICS 205 form. */
object Ics205Page:
  private val dateFormat = DateTimeFormatter.ofPattern("MM/dd/yyyy")
  private val timeFormat = DateTimeFormatter.ofPattern("HH:mm")
  private val rowsPerPage = 8
  private val columns = Seq(
    "Zone / Grp.", "Ch #", "Function", "Channel Name / Trunked Radio System Talkgroup",
    "Assignment", "RX Freq (MHz)", "Offset (MHz)", "Bandwidth",
    "RX Signaling", "TX Signaling", "Mode", "Remarks"
  )

  def render(plan: Ics205): String = Ics205Editor.render(plan)

  def renderPrintable(plan: Ics205): String =
    val pages = if plan.channels.isEmpty then Seq(Seq.empty[Ics205Channel])
                else plan.channels.grouped(rowsPerPage).toSeq
    doctype("html")(
      html(lang := "en")(
        head(
          meta(charset := "utf-8"),
          meta(name := "viewport", content := "width=device-width, initial-scale=1"),
          scalatags.Text.tags2.title(s"ICS 205 — ${plan.incidentName}"),
          link(rel := "stylesheet", href := "/css/ics205.css")
        ),
        body(pages.zipWithIndex.map { case (channels, index) =>
          formPage(plan, channels, index + 1)
        })
      )
    ).render

  private def formPage(plan: Ics205, channels: Seq[Ics205Channel], pageNumber: Int): Frag =
    val preparedBy = plan.preparedBy.map(p =>
      (Seq(p.name) ++ p.callsign.toSeq).filter(_.nonEmpty).mkString(" / ")
    ).getOrElse("")
    div(cls := "sheet")(
      h1("Incident Radio Communications Plan (ICS 205)"),
      div(cls := "form")(
        div(cls := "metadata")(
          div(cls := "field")(
            strong("1. Incident Name:"),
            div(cls := "value incident-name")(plan.incidentName)
          ),
          div(cls := "field")(
            strong("2. Date/Time Prepared:"),
            div("Date: ", plan.prepared.format(dateFormat)),
            div("Time: ", plan.prepared.format(timeFormat))
          ),
          div(cls := "field")(
            strong("3. Operational Period:"),
            div(cls := "period")(
              period("From", plan.operationalPeriod.from),
              period("To", plan.operationalPeriod.to)
            )
          )
        ),
        div(cls := "section-label")(strong("4. Basic Radio Channel Use:")),
        table(cls := "channels", attr("aria-label") := "Basic Radio Channel Use")(
          colgroup(columns.map(_ => col())),
          thead(tr(columns.map(label => th(attr("scope") := "col")(label)))),
          tbody(
            channels.map(channelRow),
            (channels.size until rowsPerPage).map(_ =>
              tr(cls := "channel-row")(columns.map(_ => td()))
            )
          )
        ),
        div(cls := "instructions")(
          strong("5. Special Instructions:"),
          div(cls := "value")(plan.specialInstructions)
        ),
        div(cls := "prepared-by")(
          span(strong("6. Prepared by "), "(Communications Unit Leader)"),
          span(cls := "name-line")("Name / Callsign: ", span(cls := "entry")(preparedBy)),
          span(cls := "signature")("Signature: ", span(cls := "entry")())
        ),
        div(cls := "footer")(
          strong("ICS 205"),
          span("IAP Page ", pageNumber.toString),
          span("Date/Time: ", plan.prepared.format(dateFormat), " ", plan.prepared.format(timeFormat))
        )
      )
    )

  private def period(label: String, value: Option[LocalDateTime]): Frag =
    div(
      div(s"Date ${label}: ", value.map(_.format(dateFormat)).getOrElse("")),
      div(s"Time ${label}: ", value.map(_.format(timeFormat)).getOrElse(""))
    )

  private def channelRow(channel: Ics205Channel): Frag =
    val offset = channel.frequency.offset.mhz
    val offsetText = if offset > 0 then s"+${decimal(offset)}" else decimal(offset)
    val modeText = channel.mode match
      case RadioMode.Fm => "FM"
      case RadioMode.Am => "AM"
      case RadioMode.Digital => "Digital"
    tr(cls := "channel-row", attr("data-channel-id") := channel.id)(
      Seq(
        channel.zoneGroup.getOrElse(""), channel.channelNumber.getOrElse(""),
        channel.function, channel.name, channel.assignment,
        decimal(channel.frequency.rx.mhz), offsetText,
        channel.bandwidth.map(_.toString).getOrElse(""),
        channel.receiveSignaling.map(signalingText).getOrElse(""),
        channel.transmitSignaling.map(signalingText).getOrElse(""),
        modeText, channel.remarks
      ).map(value => td(cls := "value")(value))
    )

  private def decimal(value: BigDecimal): String = value.bigDecimal.stripTrailingZeros.toPlainString

  // Type prefixes distinguish tone frequencies, DCS codes, and NAC identifiers.
  // Missing signaling stays blank: absence in the model does not imply "off".
  private def signalingText(signaling: Signaling): String = signaling match
    case Signaling.Ctcss(hz) => s"CTCSS ${decimal(hz)} Hz"
    case Signaling.Dcs(code) => f"DCS ${code}%03d"
    case Signaling.Nac(code) => s"NAC ${code}"
