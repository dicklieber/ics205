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

import ics205.auth.{AuthenticatedUser, Role, User}
import ics205.model.Ics205Event
import scalatags.Text.all.*

object EventMetadataPage:

  def render(
    currentUser: AuthenticatedUser,
    event: Ics205Event,
    users: Seq[User] = Seq.empty,
    availableEvents: Seq[String] = Seq.empty,
    message: Option[String] = None,
    error: Option[String] = None,
    knownGroups: Set[String] = Set("Default")
  ): String =
    val displayName = event.eventName
    val groups = (knownGroups + event.group + "Default").toSeq.sorted

    doctype("html")(
      html(lang := "en")(
        head(
          meta(charset := "utf-8"),
          meta(name := "viewport", content := "width=device-width, initial-scale=1"),
          scalatags.Text.tags2.title(s"ICS 205 — Event Details: $displayName"),
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
                h1(s"Event Details: $displayName")
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
              input(tpe := "hidden", name := "eventId", value := event.id),
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
                div(cls := "form-group")(
                  label(attr("for") := "group")("Group"),
                  input(
                    tpe := "text",
                    id := "group",
                    name := "group",
                    value := event.group,
                    attr("list") := "group-list",
                    placeholder := "e.g. Default or Field Operations"
                  ),
                  tag("datalist")(id := "group-list")(
                    groups.map { g =>
                      option(value := g)
                    }
                  ),
                  p(cls := "form-help")(
                    "Assign this event to a group. Select an existing group from the list or type a new one."
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
