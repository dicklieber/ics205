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
import ics205.model.Ics205
import scalatags.Text.all.*

object RadioExportPage:

  def render(
    currentUser: AuthenticatedUser,
    plan: Ics205,
    definitions: Seq[String],
    selectedDefinition: Option[String] = None,
    includeHeader: Boolean = true,
    generatedCsv: Option[String] = None,
    message: Option[String] = None,
    error: Option[String] = None,
    currentEventName: Option[String] = None,
    availableEvents: Seq[String] = Seq.empty
  ): String =
    doctype("html")(
      html(lang := "en")(
        head(
          meta(charset := "utf-8"),
          meta(name := "viewport", content := "width=device-width, initial-scale=1"),
          scalatags.Text.tags2.title("ICS 205 — Export Radio CSV"),
          link(rel := "stylesheet", href := "/css/navbar.css"),
          link(rel := "stylesheet", href := "/css/admin.css")
        ),
        body(
          NavigationBar.render(
            activePage = NavigationBar.ActivePage.ExportRadio,
            currentUser = Some(currentUser),
            currentEventName = currentEventName,
            availableEvents = availableEvents
          ),
          div(cls := "admin-container")(
            div(cls := "admin-header")(
              div(
                h1("Export Radio CSV"),
                p(
                  s"Incident: ",
                  strong(if plan.incidentName.trim.nonEmpty then plan.incidentName else "Untitled Plan")
                )
              )
            ),

            message.filter(_.nonEmpty).map(msg =>
              div(cls := "alert alert-success", attr("role") := "status")(msg)
            ),

            error.filter(_.nonEmpty).map(err =>
              div(cls := "alert alert-error", attr("role") := "alert")(err)
            ),

            div(cls := "card")(
              h2("Export Configuration"),
              form(method := "post", action := "/export/radio", id := "export-form")(
                div(cls := "form-group")(
                  label(attr("for") := "definition")("Radio Export Format"),
                  if definitions.isEmpty then
                    div(style := "color: #bf2600; font-size: 10pt; margin-top: 6px;")(
                      "No radio export definitions found."
                    )
                  else
                    select(
                      id := "definition",
                      name := "definition",
                      style := "width: 100%; max-width: 400px; padding: 8px 10px; border: 1px solid #dfe1e6; border-radius: 4px; font-size: 11pt; background: #fff;"
                    )(
                      definitions.map { defName =>
                        option(
                          value := defName,
                          if selectedDefinition.contains(defName) || (selectedDefinition.isEmpty && defName == definitions.head) then
                            selected
                          else
                            cls := ""
                        )(defName)
                      }
                    ),
                  p(cls := "form-help")(
                    "Choose the target radio export definition to format the channels for your radio programming software."
                  )
                ),

                div(cls := "form-group", style := "margin-top: 16px;")(
                  label(
                    cls := "checkbox-group",
                    style := "display: flex; align-items: center; gap: 8px; cursor: pointer;"
                  )(
                    input(
                      tpe := "checkbox",
                      id := "includeHeader",
                      name := "includeHeader",
                      value := "true",
                      if includeHeader then checked else cls := ""
                    ),
                    span(strong("Include Header Row"), " (column headings in first line)")
                  )
                ),

                div(style := "margin-top: 24px; display: flex; gap: 8px; flex-wrap: wrap;")(
                  button(tpe := "submit", cls := "btn btn-primary", id := "generate-button")(
                    "Generate CSV"
                  ),
                  button(
                    tpe := "submit", cls := "btn btn-primary", id := "save-csv-button",
                    name := "download", value := "true"
                  )("Save CSV File")
                )
              )
            ),

            generatedCsv.map { csv =>
              div(cls := "card", id := "output-card")(
                div(style := "display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px; flex-wrap: wrap; gap: 8px;")(
                  div(
                    h2(style := "margin: 0;")("Generated CSV Output"),
                    selectedDefinition.map(name =>
                      p(style := "margin: 4px 0 0 0; color: #5e6c84; font-size: 9.5pt;")(
                        s"Format: ", strong(name), s" (${plan.channels.length} channels)"
                      )
                    ).getOrElse(span())
                  ),
                  button(
                    tpe := "button",
                    cls := "btn btn-secondary",
                    id := "copy-btn",
                    onclick := "copyCsvToClipboard()"
                  )("Copy to Clipboard")
                ),
                div(id := "copy-status", style := "min-height: 20px; color: #006644; font-weight: 600; font-size: 9.5pt; margin-bottom: 8px;")(),
                textarea(
                  id := "csv-output",
                  name := "csvOutput",
                  rows := 16,
                  readonly,
                  attr("aria-label") := "Generated CSV text",
                  style := "width: 100%; box-sizing: border-box; font-family: 'SFMono-Regular', Consolas, 'Liberation Mono', Menlo, monospace; font-size: 9.5pt; line-height: 1.45; padding: 12px; border: 1px solid #dfe1e6; border-radius: 4px; background: #fafbfc; white-space: pre; overflow-x: auto;"
                )(csv),
                p(style := "color: #5e6c84; font-size: 9.5pt; margin-top: 8px;")(
                  "Click \"Copy to Clipboard\" or select all text inside the box to copy and paste into your radio programming application."
                ),
                script(raw(
                  """function copyCsvToClipboard() {
                    |  const textarea = document.getElementById('csv-output');
                    |  if (!textarea) return;
                    |  textarea.select();
                    |  textarea.setSelectionRange(0, 999999);
                    |  const status = document.getElementById('copy-status');
                    |  if (navigator.clipboard && navigator.clipboard.writeText) {
                    |    navigator.clipboard.writeText(textarea.value).then(() => {
                    |      if (status) {
                    |        status.textContent = 'CSV copied to clipboard!';
                    |        setTimeout(() => { status.textContent = ''; }, 3000);
                    |      }
                    |    }).catch(() => {
                    |      fallbackCopy();
                    |    });
                    |  } else {
                    |    fallbackCopy();
                    |  }
                    |  function fallbackCopy() {
                    |    document.execCommand('copy');
                    |    if (status) {
                    |      status.textContent = 'CSV copied to clipboard!';
                    |      setTimeout(() => { status.textContent = ''; }, 3000);
                    |    }
                    |  }
                    |}
                    |""".stripMargin
                ))
              )
            }.getOrElse(span())
          )
        )
      )
    ).render
