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
                h1("Groups"),
                p(style := "color: #5e6c84; margin-top: 4px; margin-bottom: 0;")(
                  "All known groups with their associated users and ICS 205 events."
                )
              )
            ),

            if groups.isEmpty then
              div(cls := "card")(
                p(style := "color: #6b778c; text-align: center; padding: 24px 0; margin: 0;")(
                  "No groups found."
                )
              )
            else
              div(cls := "groups-list", style := "display: flex; flex-direction: column; gap: 20px; margin-top: 16px;")(
                groups.map { group =>
                  div(cls := "card")(
                    div(style := "display: flex; justify-content: space-between; align-items: center; border-bottom: 1px solid #ebecf0; padding-bottom: 10px; margin-bottom: 14px;")(
                      h2(style := "margin: 0; font-size: 15pt; display: flex; align-items: center; gap: 10px;")(
                        group.name,
                        span(cls := "badge badge-role", style := "font-size: 9pt; font-weight: normal;")(
                          s"${group.users.size} user${if group.users.size == 1 then "" else "s"}, ${group.events.size} event${if group.events.size == 1 then "" else "s"}"
                        )
                      )
                    ),

                    // Associated Users Section
                    div(style := "margin-bottom: 16px;")(
                      h3(style := "font-size: 11pt; color: #172b4d; margin-top: 0; margin-bottom: 8px;")("Associated Users"),
                      if group.users.isEmpty then
                        p(style := "color: #6b778c; font-style: italic; margin: 0; font-size: 9.5pt;")("No users assigned to this group.")
                      else
                        table(cls := "users-table", style := "margin-bottom: 0;")(
                          thead(
                            tr(
                              th("Username"),
                              th("Role"),
                              th("Status"),
                              th("Actions")
                            )
                          ),
                          tbody(
                            group.users.map { user =>
                              tr(
                                td(strong(user.username)),
                                td(span(cls := "badge badge-role")(user.role.toString)),
                                td(
                                  if user.enabled then
                                    span(cls := "badge badge-active")("Active")
                                  else
                                    span(cls := "badge badge-disabled")("Disabled")
                                ),
                                td(cls := "actions-cell")(
                                  if canEditUsers then
                                    a(href := s"/admin/users?edit=${user.id}#user-form", cls := "btn btn-secondary btn-sm")("Edit User")
                                  else
                                    span()
                                )
                              )
                            }
                          )
                        )
                    ),

                    // Associated ICS 205 Events Section
                    div(style := "margin-top: 14px;")(
                      h3(style := "font-size: 11pt; color: #172b4d; margin-top: 0; margin-bottom: 8px;")("Associated ICS 205 Events"),
                      if group.events.isEmpty then
                        p(style := "color: #6b778c; font-style: italic; margin: 0; font-size: 9.5pt;")("No events assigned to this group.")
                      else
                        table(cls := "users-table", style := "margin-bottom: 0;")(
                          thead(
                            tr(
                              th("Event Name"),
                              th("Incident Name"),
                              th("Channels"),
                              th("Actions")
                            )
                          ),
                          tbody(
                            group.events.map { event =>
                              tr(
                                td(strong(event.eventName)),
                                td(if event.ics205.incidentName.nonEmpty then event.ics205.incidentName else "—"),
                                td(s"${event.ics205.channels.size} channel${if event.ics205.channels.size == 1 then "" else "s"}"),
                                td(cls := "actions-cell")(
                                  a(href := s"/?event=${encode(event.id)}", cls := "btn btn-primary btn-sm", style := "margin-right: 6px;")("View Plan"),
                                  if canEditPlans then
                                    a(href := s"/events/metadata?name=${encode(event.id)}", cls := "btn btn-secondary btn-sm")("Edit Event")
                                  else
                                    span()
                                )
                              )
                            }
                          )
                        )
                    )
                  )
                }
              )
          )
        )
      )
    ).render
