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

import ics205.auth.{AuthenticatedUser, Role}
import ics205.model.{Ics205Event, PlanAccess}
import scalatags.Text.all.*

import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.{Instant, ZoneOffset}
import java.time.format.DateTimeFormatter

object EventsPage:
  private def encode(s: String): String = URLEncoder.encode(s, StandardCharsets.UTF_8.toString)
  private val fileDateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss 'UTC'").withZone(ZoneOffset.UTC)

  def render(
    currentUser: AuthenticatedUser,
    events: Seq[Ics205Event],
    currentEventName: Option[String] = None,
    message: Option[String] = None,
    error: Option[String] = None,
    fileModifiedAt: Map[String, Instant] = Map.empty,
    knownGroups: Set[String] = Set("Default")
  ): String =
    val sortedEvents = events.sortBy(ev => (ev.eventName.toLowerCase(java.util.Locale.ROOT), ev.eventName))
    val availableNames = sortedEvents.map(_.eventName)
    val isAdmin = currentUser.role == Role.Admin
    val groups = (knownGroups + "Default").toSeq.sorted

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
              table(cls := "users-table events-table")(
                thead(
                  tr(
                    th("Event Name"),
                    th("Incident Name"),
                    th("Group"),
                    th("Channels"),
                    th("File Modified"),
                    th("Your Access"),
                    th("Actions")
                  )
                ),
                tbody(
                  if events.isEmpty then
                    tr(td(colspan := 7, style := "text-align: center; color: #6b778c; padding: 20px;")("No events found. Create an event below to get started."))
                  else
                    sortedEvents.map { ev =>
                      val isSelected = currentEventName.contains(ev.eventName) || (currentEventName.isEmpty && events.headOption.contains(ev))
                      val canEdit = ev.canEdit(currentUser)
                      val accessLabel = ev.accessFor(currentUser) match
                        case Some(PlanAccess.Edit) => "Edit"
                        case Some(PlanAccess.ReadOnly) => "View Only"
                        case None if isAdmin => "Admin (Edit)"
                        case None => "None"

                      val baseName = ev.eventName.replaceFirst("\\s*\\(\\d+\\)$", "")
                      val duplicateName = Iterator.from(1).map(n => s"$baseName ($n)")
                        .find(candidate => !availableNames.exists(_.equalsIgnoreCase(candidate))).get

                      tr(
                        td(
                          a(href := s"/?event=${encode(ev.id)}")(strong(ev.eventName)),
                          if isSelected then
                            span(cls := "badge badge-active", style := "margin-left: 8px;")("Current")
                          else
                            span()
                        ),
                        td(if ev.ics205.incidentName.nonEmpty then ev.ics205.incidentName else "—"),
                        td(span(cls := "badge badge-role")(ev.group)),
                        td(ev.ics205.channels.size.toString),
                        td(
                          fileModifiedAt.get(ev.id).map { modified =>
                            scalatags.Text.tags2.time(attr("datetime") := modified.toString)(fileDateFormatter.format(modified))
                          }.getOrElse[Modifier](span("—"))
                        ),
                        td(
                          if canEdit || isAdmin then
                            span(cls := "badge badge-role")(accessLabel)
                          else
                            span(cls := "badge")(accessLabel)
                        ),
                        td(cls := "event-actions")(
                          select(cls := "event-action-select", attr("aria-label") := s"Actions for ${ev.eventName}")(
                            option(value := "")("Actions…"),
                            option(value := s"/events/export?name=${encode(ev.id)}")("Export"),
                            if canEdit || isAdmin then
                              Seq[Modifier](
                                option(value := s"/events/metadata?name=${encode(ev.id)}")("Edit Event"),
                                option(value := "duplicate")("Duplicate")
                              )
                            else Seq.empty[Modifier],
                            if isAdmin then Seq[Modifier](option(value := "delete")("Delete")) else Seq.empty[Modifier]
                          ),
                          if canEdit || isAdmin then
                            form(method := "post", action := "/events/duplicate", hidden,
                              attr("data-duplicate-name") := duplicateName)(
                              input(tpe := "hidden", name := "eventId", value := ev.id),
                              input(tpe := "hidden", name := "eventName", value := ev.eventName),
                              input(tpe := "hidden", name := "newEventName")
                            )
                          else span(),
                          if isAdmin then
                            form(method := "post", action := "/events/delete", hidden,
                              attr("data-confirm") := s"Are you sure you want to delete event '${ev.eventName}'?")(
                              input(tpe := "hidden", name := "eventId", value := ev.id),
                              input(tpe := "hidden", name := "eventName", value := ev.eventName)
                            )
                          else span()
                        )
                      )
                    }
                )
              )
            ),

            if isAdmin || currentUser.hasPermission(ics205.auth.Permission.EditPlans) then
              div(
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
                    div(cls := "form-group")(
                      label(attr("for") := "group")("Group"),
                      select(
                        id := "group",
                        name := "group"
                      )(
                        groups.map { g =>
                          option(
                            value := g,
                            if g == "Default" then selected else cls := ""
                          )(g)
                        }
                      ),
                      p(cls := "form-help")("Assign this event to a group.")
                    ),
                    div(cls := "form-group", style := "margin-top: 6px;")(
                      label(attr("for") := "newGroupName", style := "font-weight: normal; font-size: 9pt;")("Or create new group:"),
                      input(
                        tpe := "text",
                        id := "newGroupName",
                        name := "newGroupName",
                        placeholder := "e.g. Field Operations"
                      ),
                      p(cls := "form-help")(
                        "New group names are automatically formatted to Capitalized Words (e.g. 'hello world' -> 'Hello World')."
                      )
                    ),
                    div(style := "margin-top: 20px;")(
                      button(tpe := "submit", cls := "btn btn-primary")("Create Event")
                    )
                  )
                ),
                div(cls := "card", id := "import-event-form")(
                  h2("Import Event"),
                  form(method := "post", action := "/events/import", enctype := "multipart/form-data")(
                    div(cls := "form-group")(
                      label(attr("for") := "file")("Event JSON File"),
                      input(
                        tpe := "file",
                        id := "file",
                        name := "file",
                        accept := ".json,application/json",
                        required
                      ),
                      p(cls := "form-help")("Upload an ICS 205 event JSON file. If an event with the same name already exists, a suffix will be added automatically to differentiate it.")
                    ),
                    div(style := "margin-top: 20px;")(
                      button(tpe := "submit", cls := "btn btn-primary")("Import Event")
                    )
                  )
                )
              )
            else
              span()
          ),
          script(raw(
            """document.addEventListener('submit', function(e) {
              |  var form = e.target;
              |  if (form && form.hasAttribute('data-confirm')) {
              |    if (!confirm(form.getAttribute('data-confirm'))) {
              |      e.preventDefault();
              |    }
              |  }
              |});
              |document.addEventListener('change', function(e) {
              |  var menu = e.target;
              |  if (!menu.classList.contains('event-action-select')) return;
              |  var action = menu.value;
              |  menu.value = '';
              |  if (action === 'duplicate') {
              |    var form = menu.parentElement.querySelector('form[data-duplicate-name]');
              |    var name = prompt('Name of the duplicate event:', form.getAttribute('data-duplicate-name'));
              |    if (name === null) return;
              |    if (!name.trim()) { alert('Event name cannot be empty.'); return; }
              |    form.elements.newEventName.value = name.trim();
              |    form.requestSubmit();
              |  } else if (action === 'delete') {
              |    menu.parentElement.querySelector('form[data-confirm]').requestSubmit();
              |  } else if (action) {
              |    window.location.assign(action);
              |  }
              |});""".stripMargin
          ))
        )
      )
    ).render
