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
import ics205.util.LoggingStore
import ics205.web.NavigationBar
import scalatags.Text.all.*

import java.net.URLEncoder

object LoggingPage:

  def render(
    currentUser: Option[AuthenticatedUser],
    discoveredLoggers: Seq[String],
    configuredLoggers: Map[String, String],
    effectiveLevels: Map[String, String] = Map.empty,
    selectedLogger: Option[String] = None,
    message: Option[String] = None,
    error: Option[String] = None
  ): String =
    val activeLogger = selectedLogger.filter(_.nonEmpty).orElse(discoveredLoggers.headOption)
    val activeLevel = activeLogger.flatMap(configuredLoggers.get).orElse(activeLogger.flatMap(effectiveLevels.get)).getOrElse("INFO")

    doctype("html")(
      html(lang := "en")(
        head(
          meta(charset := "utf-8"),
          meta(name := "viewport", content := "width=device-width, initial-scale=1"),
          scalatags.Text.tags2.title("ICS 205 — Logging Configuration"),
          link(rel := "stylesheet", href := "/css/navbar.css"),
          link(rel := "stylesheet", href := "/css/admin.css")
        ),
        body(
          NavigationBar.render(NavigationBar.ActivePage.Logging, currentUser),
          div(cls := "admin-container")(
            div(cls := "admin-header")(
              div(
                h1("Logging Configuration"),
                p(style := "color: #6b778c; margin-top: 4px;")(
                  "Select any class implementing LazyLogging, choose a log level, and persist changes to Locus.config."
                )
              )
            ),

            message.filter(_.nonEmpty).map(msg =>
              div(cls := "alert alert-success", attr("role") := "status")(msg)
            ),

            error.filter(_.nonEmpty).map(err =>
              div(cls := "alert alert-error", attr("role") := "alert")(err)
            ),

            div(cls := "admin-grid")(
              div(cls := "card", id := "config-form")(
                h2("Set Log Level"),
                form(
                  method := "post",
                  action := "/debug/logging"
                )(
                  div(cls := "form-group")(
                    label(attr("for") := "logger")("Logger (LazyLogging class)"),
                    select(
                      id := "logger",
                      name := "logger",
                      cls := "form-select",
                      required
                    )(
                      discoveredLoggers.map { loggerName =>
                        val isSelected = activeLogger.contains(loggerName)
                        val configBadge = configuredLoggers.get(loggerName).map(lvl => s" [configured: $lvl]").getOrElse("")
                        option(
                          value := loggerName,
                          if isSelected then selected else cls := ""
                        )(s"$loggerName$configBadge")
                      }
                    ),
                    p(cls := "form-help")(
                      s"Discovered ${discoveredLoggers.size} classes/objects implementing LazyLogging via ClassGraph."
                    )
                  ),

                  div(cls := "form-group")(
                    label(attr("for") := "level")("Log Level"),
                    select(
                      id := "level",
                      name := "level",
                      cls := "form-select",
                      required
                    )(
                      LoggingStore.AvailableLevels.map { lvl =>
                        val isSelected = lvl.equalsIgnoreCase(activeLevel)
                        option(
                          value := lvl,
                          if isSelected then selected else cls := ""
                        )(lvl)
                      }
                    ),
                    p(cls := "form-help")(
                      "Select the log level to apply dynamically and persist to Locus.config."
                    )
                  ),

                  div(cls := "form-actions")(
                    button(tpe := "submit", cls := "btn btn-primary")("Set Log Level")
                  )
                )
              ),

              div(cls := "card")(
                h2("Persisted Loggers (Locus.config)"),
                if configuredLoggers.isEmpty then
                  p(style := "color: #6b778c; padding: 12px 0;")("No custom logger levels persisted in Locus.config.")
                else
                  table(cls := "users-table")(
                    thead(
                      tr(
                        th("Logger"),
                        th("Persisted Level"),
                        th("Effective Level"),
                        th("Actions")
                      )
                    ),
                    tbody(
                      configuredLoggers.toSeq.sortBy(_._1).map { case (loggerName, lvl) =>
                        val eff = effectiveLevels.getOrElse(loggerName, lvl)
                        tr(
                          td(code(loggerName)),
                          td(span(cls := "badge badge-role")(lvl)),
                          td(span(cls := "badge badge-active")(eff)),
                          td(cls := "actions-cell")(
                            a(
                              href := s"/debug/logging?logger=${URLEncoder.encode(loggerName, "UTF-8")}#config-form",
                              cls := "btn btn-secondary btn-sm"
                            )("Edit"),
                            form(
                              method := "post",
                              action := "/debug/logging",
                              style := "display: inline;"
                            )(
                              input(tpe := "hidden", name := "logger", value := loggerName),
                              input(tpe := "hidden", name := "action", value := "reset"),
                              input(tpe := "hidden", name := "level", value := lvl),
                              button(tpe := "submit", cls := "btn btn-danger btn-sm")("Reset")
                            )
                          )
                        )
                      }
                    )
                  )
              )
            ),

            div(cls := "card", style := "margin-top: 24px;")(
              h2(s"All Discovered LazyLogging Classes (${discoveredLoggers.size})"),
              table(cls := "users-table")(
                thead(
                  tr(
                    th("Class / Object Name"),
                    th("Effective Level"),
                    th("Configured"),
                    th("Action")
                  )
                ),
                tbody(
                  if discoveredLoggers.isEmpty then
                    tr(td(colspan := 4, style := "text-align: center; color: #6b778c; padding: 16px;")("No LazyLogging classes found."))
                  else
                    discoveredLoggers.map { loggerName =>
                      val eff = effectiveLevels.getOrElse(loggerName, "INFO")
                      val conf = configuredLoggers.get(loggerName)
                      tr(
                        td(code(loggerName)),
                        td(span(cls := "badge badge-active")(eff)),
                        td(
                          conf match
                            case Some(lvl) => span(cls := "badge badge-role")(lvl)
                            case None => span(style := "color: #6b778c;")("Default")
                        ),
                        td(cls := "actions-cell")(
                          a(
                            href := s"/debug/logging?logger=${URLEncoder.encode(loggerName, "UTF-8")}#config-form",
                            cls := "btn btn-secondary btn-sm"
                          )("Configure")
                        )
                      )
                    }
                )
              )
            )
          )
        )
      )
    ).render
