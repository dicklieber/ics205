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

import ics205.auth.AuthenticatedUser
import ics205.model.*
import ics205.exporter.{RadioChannelNameBuilder, RadioChannelNameBuilderDefault}
import scalatags.Text.all.*
import scalatags.Text.tags2.section

/** Read-only radio view with generated channel names and expanded nested values. */
object RadioPage:
  private case class Column(label: String, value: Ics205Channel => String, isRadio: Boolean = false)
  private case class Group(label: String, columns: Seq[Column])
  private def simple(label: String, isRadio: Boolean = false)(value: Ics205Channel => String): Group =
    Group(label, Seq(Column(label, value, isRadio)))
  private def decimal(value: BigDecimal): String = value.bigDecimal.toPlainString

  private val channelNameBuilder: RadioChannelNameBuilder = new RadioChannelNameBuilderDefault()

  private val groups = Seq(
    simple("radioChannelName", isRadio = true)(channelNameBuilder.apply),
    simple("function")(_.function),
    simple("name")(_.name),
    simple("assignment")(_.assignment),
    Group("frequency", Seq(
      Column("rxFrequency (MHz)", c => decimal(c.frequency.rxFrequency.mhz), isRadio = true),
      Column("offset (MHz)", c => decimal(c.frequency.offset.mhz), isRadio = true)
    )),
    simple("mode")(_.mode.toString),
    simple("bandwidth", isRadio = true)(_.bandwidth.toString),
    Group("ctcss", Seq(
      Column("frequency (Hz)", c => c.ctcss.frequency.fold("")(f => decimal(f.hz)), isRadio = true),
      Column("mode", _.ctcss.mode.toString, isRadio = true)
    )),
    simple("remarks")(_.remarks)
  )

  def render(plan: Ics205, currentUser: Option[AuthenticatedUser] = None): String =
    def field(label: String, value: String): Frag = div(cls := "radio-field")(dt(label), dd(value))
    doctype("html")(html(lang := "en")(
      head(
        meta(charset := "utf-8"),
        meta(name := "viewport", content := "width=device-width, initial-scale=1"),
        scalatags.Text.tags2.title("ICS 205 — Radio"),
        link(rel := "stylesheet", href := "/css/navbar.css"),
        link(rel := "stylesheet", href := "/css/radio.css")
      ),
      body(
        NavigationBar.render(NavigationBar.ActivePage.Radio, currentUser),
        div(cls := "radio-page")(
          header(
            h1("Radio")
          ),
          p("Current saved ICS205 plan. Save edits on the plan page before viewing them here."),
          section(attr("aria-label") := "Plan details")(
            dl(cls := "radio-details")(
              field("formatVersion", plan.formatVersion),
              field("incidentName", plan.incidentName),
              field("prepared", plan.prepared.toString),
              div(cls := "radio-field")(
                dt("operationalPeriod"),
                dd(dl(
                  field("from", plan.operationalPeriod.from.fold("")(_.toString)),
                  field("to", plan.operationalPeriod.to.fold("")(_.toString))
                ))
              ),
              div(cls := "radio-field")(
                dt("preparedBy"),
                dd(dl(
                  field("name", plan.preparedBy.fold("")(_.name)),
                  field("callsign", plan.preparedBy.flatMap(_.callsign).getOrElse(""))
                ))
              ),
              field("specialInstructions", plan.specialInstructions)
            )
          ),
          div(cls := "radio-table-scroll", tabindex := "0", attr("role") := "region", attr("aria-label") := "Radio channels")(
            table(
              caption("channels"),
              thead(
                tr(groups.map { group =>
                  val radioAttr = if group.columns.forall(_.isRadio) then Seq(cls := "radio") else Seq.empty
                  if group.columns.size == 1 then
                    th(radioAttr, rowspan := 2, attr("scope") := "col")(group.label)
                  else th(radioAttr, colspan := group.columns.size, attr("scope") := "colgroup")(group.label)
                }),
                tr(groups.filter(_.columns.size > 1).flatMap(_.columns).map { column =>
                  val radioAttr = if column.isRadio then Seq(cls := "radio") else Seq.empty
                  th(radioAttr, attr("scope") := "col")(column.label)
                })
              ),
              tbody(
                if plan.channels.isEmpty then
                  tr(td(colspan := groups.map(_.columns.size).sum)("No channels."))
                else frag(plan.channels.map { channel =>
                  tr(groups.flatMap(_.columns).map { column =>
                    if column.isRadio then td(cls := "radio")(column.value(channel))
                    else td(column.value(channel))
                  })
                })
              )
            )
          )
        )
      )
    )).render
