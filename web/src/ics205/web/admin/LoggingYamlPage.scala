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

package ics205.web.admin

import ics205.auth.AuthenticatedUser
import ics205.web.NavigationBar
import scalatags.Text.all.*

object LoggingYamlPage:

  def render(
    currentUser: Option[AuthenticatedUser],
    yamlContent: String
  ): String =
    doctype("html")(
      html(lang := "en")(
        head(
          meta(charset := "utf-8"),
          meta(name := "viewport", content := "width=device-width, initial-scale=1"),
          scalatags.Text.tags2.title("ICS 205 — Log4j2 YAML Configuration"),
          link(rel := "stylesheet", href := "/css/navbar.css"),
          link(rel := "stylesheet", href := "/css/admin.css"),
          scalatags.Text.tags2.style(
            """
            .yaml-card {
              position: relative;
            }
            .yaml-header {
              display: flex;
              justify-content: space-between;
              align-items: center;
              margin-bottom: 16px;
            }
            .yaml-actions {
              display: flex;
              gap: 8px;
            }
            .yaml-pre {
              background: #1e1e1e;
              color: #d4d4d4;
              padding: 16px;
              border-radius: 6px;
              overflow-x: auto;
              font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, "Liberation Mono", "Courier New", monospace;
              font-size: 13px;
              line-height: 1.5;
              margin: 0;
              tab-size: 2;
            }
            """
          )
        ),
        body(
          NavigationBar.render(NavigationBar.ActivePage.LoggingYaml, currentUser),
          div(cls := "admin-container")(
            div(cls := "admin-header")(
              div(
                h1("Log4j2 YAML Configuration"),
                p(style := "color: #6b778c; margin-top: 4px;")(
                  "Active Log4j2 configuration rendered in YAML format."
                )
              )
            ),
            div(cls := "card yaml-card")(
              div(cls := "yaml-header")(
                h2(style := "margin: 0;")("Configuration YAML"),
                div(cls := "yaml-actions")(
                  button(
                    id := "copy-yaml-btn",
                    cls := "btn btn-secondary btn-sm",
                    onclick := "navigator.clipboard.writeText(document.getElementById('yaml-code').innerText).then(function() { var btn = document.getElementById('copy-yaml-btn'); btn.innerText = 'Copied!'; setTimeout(function() { btn.innerText = 'Copy YAML'; }, 2000); });"
                  )("Copy YAML"),
                  a(
                    href := "/debug/logging.yaml",
                    cls := "btn btn-secondary btn-sm",
                    target := "_blank"
                  )("Raw YAML"),
                  a(
                    href := "/debug/logging",
                    cls := "btn btn-primary btn-sm"
                  )("Logging UI")
                )
              ),
              pre(cls := "yaml-pre")(
                code(id := "yaml-code")(yamlContent)
              )
            )
          )
        )
      )
    ).render
