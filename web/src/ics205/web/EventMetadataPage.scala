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

import ics205.auth.{AuthenticatedUser, Permission, RolePermissions, User}
import ics205.model.Ics205Event
import scalatags.Text.all.*

import java.time.ZoneId
import java.time.format.DateTimeFormatter

object EventMetadataPage:
  private val instantFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    .withZone(ZoneId.systemDefault())

  def render(
    currentUser: AuthenticatedUser,
    event: Ics205Event,
    users: Seq[User],
    availableEvents: Seq[String] = Seq.empty,
    message: Option[String] = None,
    error: Option[String] = None
  ): String =
    val displayName = event.eventName

    doctype("html")(
      html(lang := "en")(
        head(
          meta(charset := "utf-8"),
          meta(name := "viewport", content := "width=device-width, initial-scale=1"),
          scalatags.Text.tags2.title(s"ICS 205 — Metadata: $displayName"),
          link(rel := "stylesheet", href := "/css/navbar.css"),
          link(rel := "stylesheet", href := "/css/admin.css")
        ),
        body(
          NavigationBar.render(
            activePage = NavigationBar.ActivePage.Events,
            currentUser = Some(currentUser),
            currentEventName = Some(event.eventName),
            availableEvents = availableEvents
          ),
          div(cls := "admin-container")(
            div(cls := "admin-header")(
              div(
                h1(s"Event Metadata: $displayName")
              ),
              div(cls := "nav-links")(
                a(href := "/events")("← Back to Events")
              )
            ),

            message.filter(_.nonEmpty).map(msg =>
              div(cls := "alert alert-success", attr("role") := "status")(msg)
            ),

            error.filter(_.nonEmpty).map(err =>
              div(cls := "alert alert-error", attr("role") := "alert")(err)
            ),

            form(method := "post", action := "/events/metadata")(
              input(tpe := "hidden", name := "originalEventName", value := event.eventName),
              input(tpe := "hidden", name := "eventName", value := event.eventName),

              div(cls := "card")(
                h2("Event Information"),
                div(cls := "form-group")(
                  label(attr("for") := "newEventName")("Event Name"),
                  input(
                    tpe := "text",
                    id := "newEventName",
                    name := "newEventName",
                    value := event.eventName,
                    required
                  ),
                  p(cls := "form-help")("Unique name to identify this event.")
                ),
                div(cls := "form-group")(
                  label(attr("for") := "incidentName")("Incident Name"),
                  input(
                    tpe := "text",
                    id := "incidentName",
                    name := "incidentName",
                    value := event.ics205.incidentName,
                    placeholder := "Incident name displayed on the ICS 205 form"
                  ),
                  p(cls := "form-help")("Incident name displayed on the ICS 205 plan and exports.")
                ),
                div(style := "display: grid; grid-template-columns: repeat(auto-fit, minmax(200px, 1fr)); gap: 16px; margin-top: 16px; padding-top: 16px; border-top: 1px solid #ebecf0;")(
                  div(
                    strong("Last Edited By: "),
                    span(event.metadata.lastEditedBy.getOrElse("—"))
                  ),
                  div(
                    strong("Saved At: "),
                    span(instantFormatter.format(event.metadata.savedAt))
                  )
                )
              ),

              div(cls := "card")(
                h2("User Permissions for this Event"),
                p(cls := "form-help")(
                  "Assign custom permissions for specific users on this event. If 'Default / Inherit' is selected, the user's global role determines access."
                ),
                table(cls := "users-table")(
                  thead(
                    tr(
                      th("Username"),
                      th("Global Role"),
                      th("Event Access Permission")
                    )
                  ),
                  tbody(
                    if users.isEmpty then
                      tr(td(colspan := 3, style := "text-align: center; color: #6b778c; padding: 20px;")("No users found."))
                    else
                      users.map { user =>
                        val currentPerm = event.metadata.permissions.get(user.id)
                        val isAdminUser = user.role == RolePermissions.Admin

                        tr(
                          td(
                            strong(user.username),
                            if !user.enabled then span(cls := "badge badge-disabled", style := "margin-left: 6px;")("Disabled") else span()
                          ),
                          td(
                            span(cls := "badge badge-role")(user.role.toString)
                          ),
                          td(
                            if isAdminUser then
                              span(style := "color: #5e6c84; font-style: italic;")("Admin (Always has full Edit access)")
                            else
                              select(
                                name := s"perm_${user.id}",
                                cls := "form-select"
                              )(
                                option(
                                  value := "default",
                                  if currentPerm.isEmpty then selected else cls := ""
                                )("Default / Inherit from Role"),
                                option(
                                  value := "view",
                                  if currentPerm.contains(Permission.ViewPlans) then selected else cls := ""
                                )("View Only (ViewPlans)"),
                                option(
                                  value := "edit",
                                  if currentPerm.contains(Permission.EditPlans) then selected else cls := ""
                                )("Can Edit (EditPlans)")
                              )
                          )
                        )
                      }
                  )
                ),
                div(style := "margin-top: 24px; display: flex; gap: 12px; align-items: center;")(
                  button(tpe := "submit", cls := "btn btn-primary")("Save Changes"),
                  a(href := "/events", cls := "btn btn-secondary")("Cancel")
                )
              )
            )
          )
        )
      )
    ).render
