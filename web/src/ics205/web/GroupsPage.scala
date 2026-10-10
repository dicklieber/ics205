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

import ics205.auth.{AuthenticatedUser, Permission, Role}
import ics205.model.GroupInfo
import scalatags.Text.all.*

import java.net.URLEncoder
import java.nio.charset.StandardCharsets

object GroupsPage:

  private def encode(s: String): String =
    URLEncoder.encode(s, StandardCharsets.UTF_8)

  def render(
    currentUser: Option[AuthenticatedUser],
    groups: Seq[GroupInfo],
    currentEventName: Option[String] = None,
    availableEvents: Seq[String] = Seq.empty
  ): String =
    val canEditUsers = currentUser.exists(_.hasPermission(Permission.EditUsers))
    val canEditPlans = currentUser.exists(_.hasPermission(Permission.EditPlans))

    doctype("html")(
      html(lang := "en")(
        head(
          meta(charset := "utf-8"),
          meta(name := "viewport", content := "width=device-width, initial-scale=1"),
          scalatags.Text.tags2.title("ICS 205 — Groups"),
          link(rel := "stylesheet", href := "/css/navbar.css"),
          link(rel := "stylesheet", href := "/css/admin.css")
        ),
        body(
          NavigationBar.render(
            activePage = NavigationBar.ActivePage.Groups,
            currentUser = currentUser,
            currentEventName = currentEventName,
            availableEvents = availableEvents
          ),
          div(cls := "admin-container")(
            div(cls := "admin-header")(
              div(
                h1("Groups")
              )
            ),

            if groups.isEmpty then
              div(cls := "card")(
                p(style := "color: #6b778c; text-align: center; padding: 16px 0; margin: 0;")(
                  "No groups found."
                )
              )
            else
              div(cls := "card")(
                table(cls := "users-table groups-table")(
                  thead(
                    tr(
                      th(style := "width: 22%;")("Group"),
                      th(style := "width: 38%;")("Associated Users"),
                      th(style := "width: 40%;")("Associated ICS 205 Events")
                    )
                  ),
                  tbody(
                    groups.map { group =>
                      tr(
                        td(style := "vertical-align: top; white-space: nowrap;")(
                          strong(group.name),
                          span(cls := "badge badge-role", style := "margin-left: 8px; font-weight: normal; font-size: 8.5pt;")(
                            s"${group.users.size} u / ${group.events.size} ev"
                          )
                        ),
                        td(style := "vertical-align: top;")(
                          if group.users.isEmpty then
                            span(style := "color: #6b778c; font-style: italic; font-size: 9.5pt;")("None")
                          else
                            div(style := "display: flex; flex-direction: column; gap: 4px;")(
                              group.users.map { user =>
                                div(style := "display: flex; align-items: center; gap: 6px; flex-wrap: wrap;")(
                                  if canEditUsers then
                                    a(href := s"/admin/users?edit=${user.id}#user-form", style := "font-weight: 600; text-decoration: none; color: #0052cc;")(user.username)
                                  else
                                    strong(user.username),
                                  span(cls := "badge badge-role", style := "font-size: 8pt; padding: 1px 5px;")(user.role.toString),
                                  if !user.enabled then
                                    span(cls := "badge badge-disabled", style := "font-size: 8pt; padding: 1px 5px;")("Disabled")
                                  else
                                    span(),
                                  if canEditUsers then
                                    a(href := s"/admin/users?edit=${user.id}#user-form", cls := "btn btn-secondary btn-sm", style := "font-size: 9.5px; padding: 1px 5px; margin-left: 2px;")("Edit")
                                  else
                                    span()
                                )
                              }
                            )
                        ),
                        td(style := "vertical-align: top;")(
                          if group.events.isEmpty then
                            span(style := "color: #6b778c; font-style: italic; font-size: 9.5pt;")("None")
                          else
                            div(style := "display: flex; flex-direction: column; gap: 4px;")(
                              group.events.map { event =>
                                div(style := "display: flex; align-items: center; gap: 6px; flex-wrap: wrap;")(
                                  a(href := s"/?event=${encode(event.id)}", style := "font-weight: 600; text-decoration: none; color: #0052cc;")(event.eventName),
                                  if event.ics205.incidentName.nonEmpty then
                                    span(style := "color: #5e6c84; font-size: 9pt;")(s"(${event.ics205.incidentName})")
                                  else
                                    span(),
                                  span(style := "color: #6b778c; font-size: 8.5pt;")(s"${event.ics205.channels.size} ch"),
                                  if canEditPlans then
                                    a(href := s"/events/metadata?name=${encode(event.id)}", cls := "btn btn-secondary btn-sm", style := "font-size: 9.5px; padding: 1px 5px; margin-left: 2px;")("Edit")
                                  else
                                    span()
                                )
                              }
                            )
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
