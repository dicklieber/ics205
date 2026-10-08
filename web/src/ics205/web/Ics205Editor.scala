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

import ics205.auth.{AuthenticatedUser, Permission}
import ics205.model.{CtcssFrequency, Ics205, Ics205Metadata}
import scalatags.Text.all.*

private[web] object Ics205Editor:
  def render(plan: Ics205, submitted: Option[Map[String, String]] = None,
             error: Option[String] = None, saved: Boolean = false,
             currentUser: Option[AuthenticatedUser] = None,
             metadata: Option[Ics205Metadata] = None,
             currentEventName: Option[String] = None,
             currentEventId: Option[String] = None,
             availableEvents: Seq[String] = Seq.empty,
             message: Option[String] = None): String =
    val canEdit = currentUser.exists(u => metadata.map(_.canEdit(u)).getOrElse(u.hasPermission(Permission.EditPlans)))
    val values = submitted.getOrElse(Ics205Form.fields(plan))
    val count = values.get("rowCount").flatMap(_.toIntOption).filter(n => n >= 0 && n <= 1000).getOrElse(0)
    val effectiveEventId = currentEventId.orElse(currentEventName)
    val formAction = effectiveEventId.filter(_.nonEmpty).map(id => s"/?event=${java.net.URLEncoder.encode(id, "UTF-8")}").getOrElse("/")
    val successMessage = message.orElse(if saved then Some("Plan saved.") else None)
    def field(key: String, caption: String, kind: String = "text"): Frag =
      label(caption, input(name := key, attr("aria-label") := caption, tpe := kind,
        value := values.getOrElse(key, ""),
        if kind == "datetime-local" then step := "any" else cls := "",
        if !canEdit then readonly else cls := ""
      ))
    doctype("html")(html(lang := "en")(
      head(
        meta(charset := "utf-8"), meta(name := "viewport", content := "width=device-width, initial-scale=1"),
        scalatags.Text.tags2.title(s"ICS 205 — Edit plan${currentEventName.filter(_.nonEmpty).map(n => s" ($n)").getOrElse("")}"),
        link(rel := "stylesheet", href := "/css/navbar.css"),
        link(rel := "stylesheet", href := "/css/ics205.css"),
        link(rel := "stylesheet", href := "/css/ics205-editor.css")
      ),
      body(
        NavigationBar.render(
          activePage = NavigationBar.ActivePage.Plan,
          currentUser = currentUser,
          currentEventName = currentEventName,
          availableEvents = availableEvents
        ),
        form(id := "plan-form", method := "post", action := formAction, attr("data-unsaved") := (canEdit && submitted.isDefined).toString)(
          input(tpe := "hidden", name := "eventId", value := currentEventId.getOrElse("")),
          input(tpe := "hidden", name := "eventName", value := currentEventName.getOrElse("")),
          div(cls := "toolbar")(
            button(tpe := "submit", cls := "btn btn-primary", if !canEdit then disabled else ())("Save plan"),
            button(tpe := "button", cls := "btn btn-primary renumber-channels-btn", id := "renumber-channels-top", if !canEdit then disabled else ())("Channel numbers"),
            button(tpe := "button", id := "export-json", cls := "btn btn-primary export-btn", title := "Export ICS 205 as pretty JSON")("Export"),
            button(tpe := "button", id := "import-json", cls := "btn btn-primary import-btn", title := "Import ICS 205 JSON file", if !canEdit then disabled else ())("Import"),
            span(id := "status", attr("role") := "status")()
          ),
          successMessage.filter(_.nonEmpty).map(msg => div(cls := "alert alert-success", attr("role") := "status")(msg)),
          div(id := "client-error", cls := "alert alert-error", attr("role") := "alert", style := "display: none;")(),
          error.map(message => div(cls := "alert alert-error", attr("role") := "alert")(message, " Your edits have been kept below.")),
          div(cls := "sheet")(
            h1("Incident Radio Communications Plan (ICS 205)"),
            div(cls := "form")(
              div(cls := "metadata")(
                div(cls := "field")(strong("1. Incident Name:"), field("incidentName", "Incident name")),
                div(cls := "field")(strong("2. Date/Time Prepared:"), field("prepared", "Prepared", "datetime-local")),
                div(cls := "field")(strong("3. Operational Period:"),
                  field("from", "From", "datetime-local"), field("to", "To", "datetime-local"))
              ),
              div(cls := "section-label")(strong("4. Basic Radio Channel Use:")),
              div(cls := "table-scroll")(
                table(cls := "channels", attr("aria-label") := "Editable radio channels",
                  style := s"--row-number-width: ${count.toString.length}ch;")(
                  thead(tr(Seq("#", "Zone / Grp.", "Ch #", "Function", "Channel Name", "Assignment",
                    "RX Freq (MHz)", "Offset (MHz)", "Bandwidth", "CTCSS", "Mode",
                    "Remarks", "Row controls").map(text => th(attr("scope") := "col")(text)))),
                  tbody(id := "channel-rows")((0 until count).map(index => row(values, index.toString, canEdit)))
                )
              ),
              div(cls := "row-toolbar")(
                button(tpe := "button", id := "add-row", cls := "btn btn-primary", if !canEdit then disabled else ())("Add channel"),
                button(tpe := "button", id := "paste-row", cls := "btn btn-primary", disabled)("Paste channel"),
                span(id := "clipboard-status", attr("role") := "status")(),
                span(id := "row-status", attr("role") := "status")(),
                p("Offset is in MHz; use 0 for simplex. CTCSS is in Hz: None disables it, Tone transmits a tone, and TSQL uses the tone for transmit and receive squelch.")
              ),
              input(tpe := "hidden", name := "rowCount", id := "row-count", value := count.toString),
              div(cls := "instructions")(
                label(strong("5. Special Instructions:"),
                  textarea(name := "specialInstructions", rows := 5, if !canEdit then readonly else cls := "")(values.getOrElse("specialInstructions", "")))
              ),
              div(cls := "prepared-by")(
                strong("6. Prepared by (Communications Unit Leader)"),
                field("preparedBy", "Name"), field("callsign", "Callsign"),
                span(cls := "signature")("Signature: ", span(cls := "entry")())
              ),
              div(cls := "footer")(strong("ICS 205"), span("Incident Radio Communications Plan"))
            )
          )
        ),
        if canEdit then tag("template")(id := "channel-template")(row(Map("row.NEW.offset" -> "0", "row.NEW.bandwidth" -> "Wide", "row.NEW.mode" -> "Fm", "row.NEW.ctcssMode" -> "None"), "NEW", canEdit = true)) else span(),
        if canEdit then tag("dialog")(id := "channel-numbers-dialog", cls := "channel-numbers-dialog")(
          form(method := "dialog", id := "channel-numbers-form")(
            h3("Channel numbers"),
            div(cls := "dialog-body")(
              label(attr("for") := "starting-channel-number")("Starting channel number:"),
              input(tpe := "number", id := "starting-channel-number", name := "startingChannelNumber", value := "1", step := "1", required)
            ),
            div(cls := "dialog-actions")(
              button(tpe := "button", id := "channel-numbers-cancel", cls := "btn btn-secondary", value := "cancel")("Cancel"),
              button(tpe := "submit", id := "channel-numbers-ok", cls := "btn btn-primary", value := "ok")("OK")
            )
          )
        ) else span(),
        if canEdit then input(tpe := "file", id := "import-json-file", accept := ".json,application/json", style := "display: none;") else span(),
        script(src := "/js/ics205-export.js"),
        if canEdit then script(src := "/js/ics205-editor.js") else span(),
        script(src := "/js/ics205-remarks.js")
      )
    )).render

  private def row(values: Map[String, String], index: String, canEdit: Boolean): Frag =
    val prefix = s"row.$index."
    def current(key: String): String = values.getOrElse(prefix + key, "")
    val isOther = current("mode") == "Other"
    def edit(key: String, caption: String, numeric: Boolean = false, tooltip: String = ""): Frag =
      input(name := prefix + key, attr("data-field") := key, attr("aria-label") := caption,
        if tooltip.nonEmpty then title := tooltip else cls := "",
        tpe := (if numeric && !isOther then "number" else "text"), value := current(key),
        if numeric then step := "any" else cls := "",
        if !canEdit then readonly else if !isOther && (key == "rx" || key == "offset") then required else cls := "")
    def choose(key: String, caption: String, choices: Seq[(String, String)], isHidden: Boolean = false): Frag =
      select(name := prefix + key, attr("data-field") := key, attr("aria-label") := caption,
        if isHidden then hidden else cls := "",
        if isHidden || !canEdit then disabled else cls := ""
      )(
        choices.map { (keyValue, captionValue) =>
          option(value := keyValue, if keyValue == current(key) then selected else cls := "")(captionValue)
        }
      )
    val isCtcssNone = current("ctcssMode") == "None" || current("ctcssMode").isEmpty
    tr(cls := "channel-row")(
      th(cls := "row-number", attr("scope") := "row")(index.toIntOption.map(_ + 1).fold("")(_.toString)),
      td(input(tpe := "hidden", name := prefix + "id", attr("data-field") := "id", value := current("id")),
        edit("zoneGroup", "Zone / Group")),
      td(edit("channelNumber", "Channel number")),
      td(edit("function", "Function")),
      td(div(cls := "name-controls")(
        edit("name", "Channel name / Talkgroup"),
        edit("extra", "Append to Channel Name", tooltip = "Append to Channel Name")
      )),
      td(edit("assignment", "Assignment")),
      td(edit("rx", "RX frequency in MHz", true)),
      td(edit("offset", "Offset in MHz", true)),
      td(choose("bandwidth", "Bandwidth", Seq("Wide" -> "Wide", "Narrow" -> "Narrow"))),
      td(div(cls := "ctcss-controls")(
        choose("ctcssMode", "CTCSS mode", Seq("None" -> "None", "Tone" -> "Tone", "TSQL" -> "TSQL")),
        choose("ctcssFrequency", "CTCSS frequency in Hz",
          Seq("" -> "Select tone") ++ CtcssFrequency.values.toSeq.map(tone =>
            tone.hz.bigDecimal.toPlainString -> s"${tone.hz} Hz"
          ),
          isHidden = isCtcssNone
        )
      )),
      td(choose("mode", "Mode", Seq("Fm" -> "FM", "Am" -> "AM", "Digital" -> "Digital", "Other" -> "Other"))),
      td(
        textarea(name := prefix + "remarks", attr("data-field") := "remarks", attr("aria-label") := "Remarks", rows := current("remarks").split("\n", -1).length,
          if !canEdit then readonly else cls := ""
        )(current("remarks"))
      ),
      td(cls := "row-controls")(
        button(tpe := "button", attr("data-action") := "up", attr("aria-label") := "Move channel up", if !canEdit then disabled else cls := "")("↑"),
        button(tpe := "button", attr("data-action") := "down", attr("aria-label") := "Move channel down", if !canEdit then disabled else cls := "")("↓"),
        button(tpe := "button", attr("data-action") := "copy",
          attr("aria-label") := "Copy channel", title := "Copy channel", if !canEdit then disabled else cls := "")(
          img(src := "/icons/copy.svg", alt := "", width := 16, height := 16)
        ),
        button(tpe := "button", attr("data-action") := "delete",
          attr("aria-label") := "Delete channel", title := "Delete channel", if !canEdit then disabled else cls := "")(
          img(src := "/icons/trash.svg", alt := "", width := 16, height := 16)
        )
      )
    )
