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

import ics205.auth.{AuthenticatedUser, RolePermissions}
import ics205.model.{Ics205Event, PlanAccess}
import scalatags.Text.all.*

import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.format.DateTimeFormatter

object EventsPage:
  private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

  private def encode(s: String): String = URLEncoder.encode(s, StandardCharsets.UTF_8.toString)

  def render(
    currentUser: AuthenticatedUser,
    events: Seq[Ics205Event],
    currentEventName: Option[String] = None,
    message: Option[String] = None,
    error: Option[String] = None
  ): String =
    val availableNames = events.map(_.eventName)
    val isAdmin = currentUser.role == RolePermissions.Admin

    doctype("html")(
      html(lang := "en")(
        head(
          meta(charset := "utf-8"),
          meta(name := "viewport", content := "width=device-width, initial-scale=1"),
          scalatags.Text.tags2.title("ICS 205 — Events"),
          link(rel := "stylesheet", href := "/css/navbar.css"),
          link(rel := "stylesheet", href := "/css/admin.css")
        ),
        body(
          NavigationBar.render(
            activePage = NavigationBar.ActivePage.Events,
            currentUser = Some(currentUser),
            currentEventName = currentEventName,
            availableEvents = availableNames
          ),
          div(cls := "admin-container")(
            div(cls := "admin-header")(
              div(
                h1("Events")
              )
            ),

            message.filter(_.nonEmpty).map(msg =>
              div(cls := "alert alert-success", attr("role") := "status")(msg)
            ),

            error.filter(_.nonEmpty).map(err =>
              div(cls := "alert alert-error", attr("role") := "alert")(err)
            ),

            div(cls := "card")(
              h2("All Events"),
              table(cls := "users-table")(
                thead(
                  tr(
                    th("Event Name"),
                    th("Incident Name"),
                    th("Operational Period"),
                    th("Channels"),
                    th("Your Access"),
                    th("Actions")
                  )
                ),
                tbody(
                  if events.isEmpty then
                    tr(td(colspan := 6, style := "text-align: center; color: #6b778c; padding: 20px;")("No events found. Create an event below to get started."))
                  else
                    events.map { ev =>
                      val isSelected = currentEventName.contains(ev.eventName) || (currentEventName.isEmpty && events.headOption.contains(ev))
                      val canEdit = ev.canEdit(currentUser)
                      val accessLabel = ev.accessFor(currentUser) match
                        case Some(PlanAccess.Edit) => "Edit"
                        case Some(PlanAccess.ReadOnly) => "View Only"
                        case None if isAdmin => "Admin (Edit)"
                        case None => "None"

                      val periodText = (ev.ics205.operationalPeriod.from, ev.ics205.operationalPeriod.to) match
                        case (Some(f), Some(t)) => s"${f.format(dateFormatter)} to ${t.format(dateFormatter)}"
                        case (Some(f), None) => s"From ${f.format(dateFormatter)}"
                        case (None, Some(t)) => s"To ${t.format(dateFormatter)}"
                        case (None, None) => "—"

                      tr(
                        td(
                          strong(ev.eventName),
                          if isSelected then
                            span(cls := "badge badge-active", style := "margin-left: 8px;")("Current")
                          else
                            span()
                        ),
                        td(if ev.ics205.incidentName.nonEmpty then ev.ics205.incidentName else "—"),
                        td(periodText),
                        td(ev.ics205.channels.size.toString),
                        td(
                          if canEdit || isAdmin then
                            span(cls := "badge badge-role")(accessLabel)
                          else
                            span(cls := "badge")(accessLabel)
                        ),
                        td(cls := "actions-cell")(
                          a(
                            href := s"/?event=${encode(ev.eventName)}",
                            cls := "btn btn-primary btn-sm"
                          )("Plan"),
                          if canEdit || isAdmin then
                            a(
                              href := s"/events/metadata?name=${encode(ev.eventName)}",
                              cls := "btn btn-primary btn-sm"
                            )("Metadata")
                          else
                            span(),
                          if isAdmin then
                            form(
                              method := "post",
                              action := "/events/delete",
                              onsubmit := s"return confirm('Are you sure you want to delete event \\'${ev.eventName}\\'?');"
                            )(
                              input(tpe := "hidden", name := "eventName", value := ev.eventName),
                              button(tpe := "submit", cls := "btn btn-danger btn-sm")("Delete")
                            )
                          else
                            span()
                        )
                      )
                    }
                )
              )
            ),

            if isAdmin || currentUser.hasPermission(ics205.auth.Permission.EditPlans) then
              div(cls := "card", id := "new-event-form")(
                h2("Create New Event"),
                form(method := "post", action := "/events/create")(
                  div(cls := "form-group")(
                    label(attr("for") := "eventName")("Event Name"),
                    input(
                      tpe := "text",
                      id := "eventName",
                      name := "eventName",
                      required,
                      placeholder := "e.g. 2026 Winter Field Day"
                    ),
                    p(cls := "form-help")("Unique name to identify this event.")
                  ),
                  div(cls := "form-group")(
                    label(attr("for") := "incidentName")("Incident Name (Optional)"),
                    input(
                      tpe := "text",
                      id := "incidentName",
                      name := "incidentName",
                      placeholder := "Leave blank to use Event Name"
                    ),
                    p(cls := "form-help")("Incident name displayed on the ICS 205 form.")
                  ),
                  div(style := "margin-top: 20px;")(
                    button(tpe := "submit", cls := "btn btn-primary")("Create Event")
                  )
                )
              )
            else
              span()
          )
        )
      )
    ).render
