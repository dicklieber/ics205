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

import ics205.model.{CtcssFrequency, Ics205}
import scalatags.Text.all.*

private[web] object Ics205Editor:
  def render(plan: Ics205, submitted: Option[Map[String, String]] = None,
             error: Option[String] = None, saved: Boolean = false): String =
    val values = submitted.getOrElse(Ics205Form.fields(plan))
    val count = values.get("rowCount").flatMap(_.toIntOption).filter(n => n >= 0 && n <= 1000).getOrElse(0)
    def field(key: String, caption: String, kind: String = "text"): Frag =
      label(caption, input(name := key, attr("aria-label") := caption, tpe := kind,
        value := values.getOrElse(key, ""), if kind == "datetime-local" then step := "any" else cls := ""))
    doctype("html")(html(lang := "en")(
      head(
        meta(charset := "utf-8"), meta(name := "viewport", content := "width=device-width, initial-scale=1"),
        scalatags.Text.tags2.title("ICS 205 — Edit plan"),
        link(rel := "stylesheet", href := "/css/ics205.css"),
        link(rel := "stylesheet", href := "/css/ics205-editor.css")
      ),
      body(
        form(id := "plan-form", method := "post", action := "/", attr("data-unsaved") := submitted.isDefined.toString)(
          div(cls := "toolbar")(
            button(tpe := "submit")("Save plan"),
            button(tpe := "submit", attr("formaction") := "/preview", attr("formtarget") := "_blank")("Print preview"),
            span(id := "status", attr("role") := "status")(if saved then "Plan saved." else ""),
            p("Changes are saved with Save plan. Print preview includes your current edits.")
          ),
          error.map(message => div(cls := "error", attr("role") := "alert")(message, " Your edits have been kept below.")),
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
                table(cls := "channels", attr("aria-label") := "Editable radio channels")(
                  thead(tr(Seq("Zone / Grp.", "Ch #", "Function", "Channel Name / Talkgroup", "Assignment",
                    "RX Freq (MHz)", "Offset (MHz)", "Bandwidth", "CTCSS", "Mode",
                    "Remarks", "Row controls").map(text => th(attr("scope") := "col")(text)))),
                  tbody(id := "channel-rows")((0 until count).map(index => row(values, index.toString)))
                )
              ),
              div(cls := "row-toolbar")(
                button(tpe := "button", id := "add-row")("Add channel"),
                button(tpe := "button", id := "paste-row", disabled)("Paste channel"),
                span(id := "clipboard-status", attr("role") := "status")(),
                span(id := "row-status", attr("role") := "status")(),
                p("Offset is in MHz; use 0 for simplex. CTCSS is in Hz: None disables it, Tone transmits a tone, and TSQL uses the tone for transmit and receive squelch.")
              ),
              input(tpe := "hidden", name := "rowCount", id := "row-count", value := count.toString),
              div(cls := "instructions")(
                label(strong("5. Special Instructions:"),
                  textarea(name := "specialInstructions", rows := 5)(values.getOrElse("specialInstructions", "")))
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
        tag("template")(id := "channel-template")(row(Map("row.NEW.offset" -> "0", "row.NEW.mode" -> "Fm", "row.NEW.ctcssMode" -> "None"), "NEW")),
        script(raw(editorScript))
      )
    )).render

  private def row(values: Map[String, String], index: String): Frag =
    val prefix = s"row.$index."
    def current(key: String): String = values.getOrElse(prefix + key, "")
    def edit(key: String, caption: String, numeric: Boolean = false): Frag =
      input(name := prefix + key, attr("data-field") := key, attr("aria-label") := caption,
        tpe := (if numeric then "number" else "text"), value := current(key),
        if numeric then step := "any" else cls := "",
        if key == "rx" || key == "offset" then required else cls := "")
    def choose(key: String, caption: String, choices: Seq[(String, String)]): Frag =
      select(name := prefix + key, attr("data-field") := key, attr("aria-label") := caption)(
        choices.map { (keyValue, captionValue) =>
          option(value := keyValue, if keyValue == current(key) then selected else cls := "")(captionValue)
        }
      )
    tr(cls := "channel-row")(
      td(input(tpe := "hidden", name := prefix + "id", attr("data-field") := "id", value := current("id")),
        edit("zoneGroup", "Zone / Group")),
      td(edit("channelNumber", "Channel number")),
      td(edit("function", "Function")),
      td(edit("name", "Channel name / Talkgroup")),
      td(edit("assignment", "Assignment")),
      td(edit("rx", "RX frequency in MHz", true)),
      td(edit("offset", "Offset in MHz", true)),
      td(choose("bandwidth", "Bandwidth", Seq("" -> "—", "Narrow" -> "Narrow", "Wide" -> "Wide"))),
      td(div(cls := "ctcss-controls")(
        choose("ctcssMode", "CTCSS mode", Seq("None" -> "None", "Tone" -> "Tone", "TSQL" -> "TSQL")),
        choose("ctcssFrequency", "CTCSS frequency in Hz",
          Seq("" -> "Select tone") ++ CtcssFrequency.values.toSeq.map(tone =>
            tone.hz.bigDecimal.toPlainString -> s"${tone.hz} Hz"
          ))
      )),
      td(choose("mode", "Mode", Seq("Fm" -> "FM", "Am" -> "AM", "Digital" -> "Digital"))),
      td(
        textarea(name := prefix + "remarks", attr("data-field") := "remarks", attr("aria-label") := "Remarks", rows := 1)(current("remarks"))
      ),
      td(cls := "row-controls")(
        button(tpe := "button", attr("data-action") := "up", attr("aria-label") := "Move channel up")("↑"),
        button(tpe := "button", attr("data-action") := "down", attr("aria-label") := "Move channel down")("↓"),
        button(tpe := "button", attr("data-action") := "copy",
          attr("aria-label") := "Copy channel", title := "Copy channel")(
          img(src := "/icons/copy.svg", alt := "", width := 16, height := 16)
        ),
        button(tpe := "button", attr("data-action") := "delete",
          attr("aria-label") := "Delete channel", title := "Delete channel")(
          img(src := "/icons/trash.svg", alt := "", width := 16, height := 16)
        )
      )
    )

  private val editorScript = """
    (() => {
      const form = document.getElementById('plan-form');
      const rows = document.getElementById('channel-rows');
      const status = document.getElementById('status');
      const paste = document.getElementById('paste-row');
      const clipboardStatus = document.getElementById('clipboard-status');
      let copiedChannel = null;
      let dirty = form.dataset.unsaved === 'true';
      function changed() { dirty = true; status.textContent = 'Unsaved changes'; }
      function refresh() {
        const list = [...rows.children];
        list.forEach((row, index) => {
          row.querySelectorAll('[data-field]').forEach(control => {
            control.name = 'row.' + index + '.' + control.dataset.field;
          });
          const frequency = row.querySelector('[data-field=ctcssFrequency]');
          const enabled = row.querySelector('[data-field=ctcssMode]').value !== 'None';
          frequency.hidden = !enabled;
          frequency.disabled = !enabled;
          frequency.required = enabled;
          row.querySelector('[data-action=up]').disabled = index === 0;
          row.querySelector('[data-action=down]').disabled = index === list.length - 1;
          row.querySelectorAll('[data-action]').forEach(button => {
            button.setAttribute('aria-label', button.dataset.action + ' channel ' + (index + 1));
          });
        });
        document.getElementById('row-count').value = list.length;
        document.getElementById('row-status').textContent = list.length + (list.length === 1 ? ' channel' : ' channels');
      }
      function insertChannel(values = {}) {
        const row = document.getElementById('channel-template').content.firstElementChild.cloneNode(true);
        row.querySelectorAll('[data-field]').forEach(control => {
          if (control.dataset.field !== 'id' && Object.hasOwn(values, control.dataset.field)) {
            control.value = values[control.dataset.field];
          }
        });
        row.querySelector('[data-field=id]').value = crypto.randomUUID();
        rows.append(row);
        refresh();
        changed();
        row.querySelector('[data-field=name]').focus();
      }
      document.getElementById('add-row').addEventListener('click', () => insertChannel());
      paste.addEventListener('click', () => {
        if (!copiedChannel) return;
        insertChannel(copiedChannel);
        clipboardStatus.textContent = 'Channel pasted at the end. Save plan to keep it.';
      });
      rows.addEventListener('click', event => {
        const button = event.target.closest('[data-action]');
        if (!button) return;
        const row = button.closest('tr');
        const action = button.dataset.action;
        if (action === 'copy') {
          copiedChannel = Object.fromEntries(
            [...row.querySelectorAll('[data-field]')]
              .filter(control => control.dataset.field !== 'id')
              .map(control => [control.dataset.field, control.value])
          );
          paste.disabled = false;
          clipboardStatus.textContent = 'Channel copied. Use Paste channel to add a copy.';
          return;
        }
        if (action === 'up' && row.previousElementSibling) rows.insertBefore(row, row.previousElementSibling);
        if (action === 'down' && row.nextElementSibling) rows.insertBefore(row.nextElementSibling, row);
        if (action === 'delete') {
          const next = row.nextElementSibling || row.previousElementSibling;
          row.remove();
          (next ? next.querySelector('[data-field=name]') : document.getElementById('add-row')).focus();
        } else row.querySelector('[data-field=name]').focus();
        refresh();
        changed();
      });
      form.addEventListener('input', changed);
      form.addEventListener('change', () => { refresh(); changed(); });
      form.addEventListener('submit', event => {
        refresh();
        if (event.submitter && event.submitter.getAttribute('formaction') === '/preview') return;
        dirty = false;
      });
      window.addEventListener('beforeunload', event => {
        if (dirty) { event.preventDefault(); event.returnValue = ''; }
      });
      refresh();
    })();
  """
