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
             availableEvents: Seq[String] = Seq.empty): String =
    val canEdit = currentUser.exists(u => metadata.map(_.canEdit(u)).getOrElse(u.hasPermission(Permission.EditPlans)))
    val values = submitted.getOrElse(Ics205Form.fields(plan))
    val count = values.get("rowCount").flatMap(_.toIntOption).filter(n => n >= 0 && n <= 1000).getOrElse(0)
    val formAction = currentEventName.filter(_.nonEmpty).map(n => s"/?event=${java.net.URLEncoder.encode(n, "UTF-8")}").getOrElse("/")
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
          input(tpe := "hidden", name := "eventName", value := currentEventName.getOrElse("")),
          div(cls := "toolbar")(
            button(tpe := "submit", if !canEdit then disabled else cls := "")("Save plan"),
            button(tpe := "button", cls := "renumber-channels-btn", id := "renumber-channels-top", if !canEdit then disabled else cls := "")("Channel numbers"),
            button(tpe := "button", id := "export-json", cls := "export-btn", title := "Export ICS 205 as pretty JSON")("Export"),
            button(tpe := "button", id := "import-json", cls := "import-btn", title := "Import ICS 205 JSON file", if !canEdit then disabled else cls := "")("Import"),
            span(id := "status", attr("role") := "status")(if saved then "Plan saved." else "")
          ),
          div(id := "client-error", cls := "error", attr("role") := "alert", style := "display: none;")(),
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
                table(cls := "channels", attr("aria-label") := "Editable radio channels",
                  style := s"--row-number-width: ${count.toString.length}ch;")(
                  thead(tr(Seq("#", "Zone / Grp.", "Ch #", "Function", "Channel Name / Talkgroup", "Assignment",
                    "RX Freq (MHz)", "Offset (MHz)", "Bandwidth", "CTCSS", "Mode",
                    "Remarks", "Row controls").map(text => th(attr("scope") := "col")(text)))),
                  tbody(id := "channel-rows")((0 until count).map(index => row(values, index.toString, canEdit)))
                )
              ),
              div(cls := "row-toolbar")(
                button(tpe := "button", id := "add-row", if !canEdit then disabled else cls := "")("Add channel"),
                button(tpe := "button", id := "paste-row", disabled)("Paste channel"),
                button(tpe := "button", id := "renumber-channels", cls := "renumber-channels-btn", if !canEdit then disabled else cls := "")("Channel numbers"),
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
              button(tpe := "button", id := "channel-numbers-cancel", value := "cancel")("Cancel"),
              button(tpe := "submit", id := "channel-numbers-ok", value := "ok")("OK")
            )
          )
        ) else span(),
        if canEdit then input(tpe := "file", id := "import-json-file", accept := ".json,application/json", style := "display: none;") else span(),
        script(raw(exportScript)),
        if canEdit then script(raw(editorScript)) else span(),
        script(raw(remarksSizingScript))
      )
    )).render

  private def row(values: Map[String, String], index: String, canEdit: Boolean): Frag =
    val prefix = s"row.$index."
    def current(key: String): String = values.getOrElse(prefix + key, "")
    val isOther = current("mode") == "Other"
    def edit(key: String, caption: String, numeric: Boolean = false): Frag =
      input(name := prefix + key, attr("data-field") := key, attr("aria-label") := caption,
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
      td(edit("name", "Channel name / Talkgroup")),
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

  private val exportScript = """
    (() => {
      function getEditorIcs205() {
        const planForm = document.getElementById('plan-form');
        const incidentName = planForm ? (planForm.querySelector('[name=incidentName]')?.value || '') : '';
        const prepared = planForm ? (planForm.querySelector('[name=prepared]')?.value || new Date().toISOString().slice(0, 19)) : new Date().toISOString().slice(0, 19);
        const from = planForm ? (planForm.querySelector('[name=from]')?.value || null) : null;
        const to = planForm ? (planForm.querySelector('[name=to]')?.value || null) : null;
        const specialInstructions = planForm ? (planForm.querySelector('[name=specialInstructions]')?.value || '') : '';
        const preparedByName = planForm ? (planForm.querySelector('[name=preparedBy]')?.value || '') : '';
        const callsign = planForm ? (planForm.querySelector('[name=callsign]')?.value || null) : null;

        const rows = document.getElementById('channel-rows');
        const channelRows = rows ? [...rows.children] : [];
        const channels = channelRows.map(row => {
          const id = row.querySelector('[data-field=id]')?.value || crypto.randomUUID();
          const zoneGroup = row.querySelector('[data-field=zoneGroup]')?.value || null;
          const channelNumber = row.querySelector('[data-field=channelNumber]')?.value || null;
          const func = row.querySelector('[data-field=function]')?.value || '';
          const name = row.querySelector('[data-field=name]')?.value || '';
          const assignment = row.querySelector('[data-field=assignment]')?.value || '';
          const rxVal = row.querySelector('[data-field=rx]')?.value;
          const offsetVal = row.querySelector('[data-field=offset]')?.value;
          const mode = row.querySelector('[data-field=mode]')?.value || 'Fm';
          const bandwidth = row.querySelector('[data-field=bandwidth]')?.value || 'Wide';
          const ctcssMode = row.querySelector('[data-field=ctcssMode]')?.value || 'None';
          const ctcssFreqVal = row.querySelector('[data-field=ctcssFrequency]')?.value;
          const remarks = row.querySelector('[data-field=remarks]')?.value || '';

          const rx = (rxVal !== undefined && rxVal !== '' && !isNaN(Number(rxVal))) ? Number(rxVal) : 0;
          const offset = (offsetVal !== undefined && offsetVal !== '' && !isNaN(Number(offsetVal))) ? Number(offsetVal) : 0;
          const ctcssFreq = (ctcssMode !== 'None' && ctcssFreqVal && !isNaN(Number(ctcssFreqVal))) ? Number(ctcssFreqVal) : null;

          const channelObj = {
            function: func,
            name: name,
            assignment: assignment,
            frequency: {
              rxFrequency: { mhz: rx },
              offset: { mhz: offset }
            },
            mode: mode,
            bandwidth: bandwidth,
            ctcss: {
              frequency: ctcssFreq,
              mode: ctcssMode
            },
            remarks: remarks,
            id: id
          };
          if (zoneGroup) channelObj.zoneGroup = zoneGroup;
          if (channelNumber) channelObj.channelNumber = channelNumber;
          return channelObj;
        });

        const obj = {
          formatVersion: '1.0',
          incidentName: incidentName,
          operationalPeriod: {},
          channels: channels,
          specialInstructions: specialInstructions,
          prepared: prepared
        };
        if (from) obj.operationalPeriod.from = from;
        if (to) obj.operationalPeriod.to = to;
        if (preparedByName || callsign) {
          obj.preparedBy = { name: preparedByName };
          if (callsign) obj.preparedBy.callsign = callsign;
        }
        return obj;
      }

      const exportBtn = document.getElementById('export-json');
      if (exportBtn) {
        exportBtn.addEventListener('click', () => {
          const plan = getEditorIcs205();
          const prettyJson = JSON.stringify(plan, null, 2);
          const blob = new Blob([prettyJson], { type: 'application/json' });
          const url = URL.createObjectURL(blob);
          const a = document.createElement('a');
          a.href = url;
          const rawName = plan.incidentName ? plan.incidentName.trim() : '';
          const sanitized = rawName.replace(/[\\/:*?"<>|]/g, '_');
          a.download = (sanitized.length > 0 ? sanitized : 'ics205') + '.json';
          document.body.appendChild(a);
          a.click();
          document.body.removeChild(a);
          URL.revokeObjectURL(url);
        });
      }
    })();
  """

  private val remarksSizingScript = """
    (() => {
      const rows = document.getElementById('channel-rows');
      function fitRemarks() {
        rows.querySelectorAll('textarea[data-field=remarks]').forEach(textarea => {
          textarea.rows = 1;
          textarea.style.height = 'auto';
          const borderHeight = textarea.offsetHeight - textarea.clientHeight;
          textarea.style.height = (textarea.scrollHeight + borderHeight) + 'px';
        });
      }
      rows.addEventListener('input', event => {
        if (event.target.matches('textarea[data-field=remarks]')) fitRemarks();
      });
      new MutationObserver(fitRemarks).observe(rows, { childList: true });
      new ResizeObserver(fitRemarks).observe(rows.closest('table').parentElement);
      fitRemarks();
    })();
  """

  private val editorScript = """
    (() => {
      const form = document.getElementById('plan-form');
      const rows = document.getElementById('channel-rows');
      const status = document.getElementById('status');
      const paste = document.getElementById('paste-row');
      const clipboardStatus = document.getElementById('clipboard-status');
      const renumberBtns = document.querySelectorAll('.renumber-channels-btn, #renumber-channels');
      const renumberDialog = document.getElementById('channel-numbers-dialog');
      const renumberForm = document.getElementById('channel-numbers-form');
      const startingNumberInput = document.getElementById('starting-channel-number');
      const cancelBtn = document.getElementById('channel-numbers-cancel');
      let copiedChannel = null;
      let dirty = form.dataset.unsaved === 'true';
      function changed() { dirty = true; status.textContent = 'Unsaved changes'; }
      function refresh() {
        const list = [...rows.children];
        rows.closest('table').style.setProperty('--row-number-width', String(list.length).length + 'ch');
        list.forEach((row, index) => {
          row.querySelector('.row-number').textContent = index + 1;
          row.querySelectorAll('[data-field]').forEach(control => {
            control.name = 'row.' + index + '.' + control.dataset.field;
          });
          const isOther = row.querySelector('[data-field=mode]').value === 'Other';
          ['rx', 'offset'].forEach(field => {
            const control = row.querySelector('[data-field=' + field + ']');
            control.type = isOther ? 'text' : 'number';
            control.required = !isOther;
          });
          const frequency = row.querySelector('[data-field=ctcssFrequency]');
          const enabled = row.querySelector('[data-field=ctcssMode]').value !== 'None';
          frequency.hidden = !enabled;
          frequency.disabled = !enabled;
          frequency.required = enabled && !isOther;
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
      function applyChannelNumbers(startNum) {
        const list = [...rows.children];
        list.forEach((row, index) => {
          const control = row.querySelector('[data-field=channelNumber]');
          if (control) {
            control.value = String(startNum + index);
          }
        });
        changed();
      }
      if (renumberDialog) {
        renumberBtns.forEach(btn => {
          btn.addEventListener('click', () => {
            if (typeof renumberDialog.showModal === 'function') {
              renumberDialog.showModal();
              if (startingNumberInput) {
                startingNumberInput.focus();
                startingNumberInput.select();
              }
            } else {
              const val = prompt('Starting channel number:', startingNumberInput ? startingNumberInput.value : '1');
              if (val !== null) {
                const startNum = parseInt(val, 10);
                if (!isNaN(startNum)) {
                  applyChannelNumbers(startNum);
                }
              }
            }
          });
        });
      }
      if (renumberForm) {
        renumberForm.addEventListener('submit', event => {
          event.preventDefault();
          if (startingNumberInput) {
            const startNum = parseInt(startingNumberInput.value, 10);
            if (!isNaN(startNum)) {
              applyChannelNumbers(startNum);
            }
          }
          if (typeof renumberDialog.close === 'function') {
            renumberDialog.close();
          }
        });
      }
      if (cancelBtn && renumberDialog) {
        cancelBtn.addEventListener('click', () => {
          if (typeof renumberDialog.close === 'function') {
            renumberDialog.close();
          }
        });
      }
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
      const importBtn = document.getElementById('import-json');
      const fileInput = document.getElementById('import-json-file');
      const clientError = document.getElementById('client-error');

      function showError(msg) {
        if (clientError) {
          clientError.textContent = msg;
          clientError.style.display = 'block';
          clientError.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
        } else {
          alert(msg);
        }
        if (status) {
          status.textContent = 'Import failed: ' + msg;
        }
      }

      function hideError() {
        if (clientError) {
          clientError.textContent = '';
          clientError.style.display = 'none';
        }
      }

      if (importBtn && fileInput) {
        importBtn.addEventListener('click', () => {
          fileInput.click();
        });

        fileInput.addEventListener('change', event => {
          const file = event.target.files && event.target.files[0];
          if (!file) return;

          const reader = new FileReader();
          reader.onload = e => {
            try {
              let json;
              try {
                json = JSON.parse(e.target.result);
              } catch (parseErr) {
                showError('Failed to parse JSON file: ' + parseErr.message);
                return;
              }

              if (!json || typeof json !== 'object' || Array.isArray(json)) {
                showError('Invalid JSON format: expected an object representing an ICS 205 plan or event.');
                return;
              }

              const plan = (json.ics205 && typeof json.ics205 === 'object' && !Array.isArray(json.ics205)) ? json.ics205 : json;

              if (plan.channels !== undefined && !Array.isArray(plan.channels)) {
                showError('Invalid ICS 205 data: "channels" must be an array.');
                return;
              }

              if (plan.incidentName === undefined && plan.channels === undefined && !plan.formatVersion) {
                showError('Invalid ICS 205 data: file does not contain ICS 205 plan data.');
                return;
              }

              hideError();

              // Populate top-level fields
              const incidentNameInput = form.querySelector('[name=incidentName]');
              if (incidentNameInput && plan.incidentName !== undefined) {
                incidentNameInput.value = plan.incidentName || '';
              }

              const preparedInput = form.querySelector('[name=prepared]');
              if (preparedInput && plan.prepared !== undefined) {
                preparedInput.value = plan.prepared || '';
              }

              const fromInput = form.querySelector('[name=from]');
              if (fromInput) {
                const fromVal = plan.operationalPeriod?.from || plan.from || '';
                fromInput.value = fromVal;
              }

              const toInput = form.querySelector('[name=to]');
              if (toInput) {
                const toVal = plan.operationalPeriod?.to || plan.to || '';
                toInput.value = toVal;
              }

              const specialInstructionsTextarea = form.querySelector('[name=specialInstructions]');
              if (specialInstructionsTextarea && plan.specialInstructions !== undefined) {
                specialInstructionsTextarea.value = plan.specialInstructions || '';
              }

              const preparedByInput = form.querySelector('[name=preparedBy]');
              if (preparedByInput) {
                const prepName = plan.preparedBy?.name ?? (typeof plan.preparedBy === 'string' ? plan.preparedBy : '') ?? '';
                preparedByInput.value = prepName;
              }

              const callsignInput = form.querySelector('[name=callsign]');
              if (callsignInput) {
                const callsignVal = plan.preparedBy?.callsign ?? plan.callsign ?? '';
                callsignInput.value = callsignVal;
              }

              // Replace channel rows
              rows.replaceChildren();
              const rawChannels = Array.isArray(plan.channels) ? plan.channels : [];
              rawChannels.forEach(ch => {
                const rxFreq = ch.frequency?.rxFrequency?.mhz ?? ch.frequency?.rx?.mhz ?? ch.frequency?.rxFrequency ?? ch.frequency?.rx ?? ch.rx ?? '';
                const offsetFreq = ch.frequency?.offset?.mhz ?? ch.frequency?.offset ?? ch.offset ?? '0';
                const ctcssFreq = ch.ctcss?.frequency?.hz ?? ch.ctcss?.frequency ?? ch.ctcssFrequency ?? '';
                const ctcssMode = ch.ctcss?.mode ?? ch.ctcssMode ?? 'None';

                const rowValues = {
                  id: ch.id || crypto.randomUUID(),
                  zoneGroup: ch.zoneGroup || '',
                  channelNumber: ch.channelNumber || '',
                  function: ch.function || '',
                  name: ch.name || '',
                  assignment: ch.assignment || '',
                  rx: String(rxFreq),
                  offset: String(offsetFreq),
                  bandwidth: ch.bandwidth || 'Wide',
                  ctcssMode: ctcssMode,
                  ctcssFrequency: String(ctcssFreq),
                  mode: ch.mode || 'Fm',
                  remarks: ch.remarks || ''
                };

                const row = document.getElementById('channel-template').content.firstElementChild.cloneNode(true);
                row.querySelectorAll('[data-field]').forEach(control => {
                  const field = control.dataset.field;
                  if (Object.hasOwn(rowValues, field)) {
                    control.value = rowValues[field];
                  }
                });
                rows.append(row);
              });

              refresh();
              changed();
              status.textContent = 'Unsaved changes (imported ' + file.name + ')';
            } catch (err) {
              showError('Error processing ICS 205 import: ' + err.message);
            } finally {
              fileInput.value = '';
            }
          };
          reader.onerror = () => {
            showError('Failed to read file: ' + file.name);
            fileInput.value = '';
          };
          reader.readAsText(file);
        });
      }
      form.addEventListener('input', changed);
      form.addEventListener('change', () => { refresh(); changed(); });
      form.addEventListener('submit', () => {
        refresh();
        dirty = false;
      });
      window.addEventListener('beforeunload', event => {
        if (dirty) { event.preventDefault(); event.returnValue = ''; }
      });
      refresh();
    })();
  """
