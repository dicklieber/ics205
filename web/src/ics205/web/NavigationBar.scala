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
import scalatags.Text.all.*
import scalatags.Text.tags2.nav

object NavigationBar:

  enum ActivePage:
    case Plan, Radio, ExportRadio, Events, UserAdmin, Login, None

  def render(
    activePage: ActivePage = ActivePage.None,
    currentUser: Option[AuthenticatedUser] = None,
    currentEventName: Option[String] = None,
    availableEvents: Seq[String] = Seq.empty
  ): Frag =
    val showUserAdmin = currentUser.exists(_.hasPermission(Permission.EditUsers))
    val isExportActive = activePage == ActivePage.ExportRadio
    val isEventsActive = activePage == ActivePage.Events

    nav(
      cls := "navbar navbar-expand navbar-dark bg-dark",
      attr("aria-label") := "Main navigation"
    )(
      div(cls := "container-fluid")(
        a(cls := "navbar-brand", href := "/")("ICS 205"),
        ul(cls := "navbar-nav me-auto")(
          li(cls := "nav-item")(
            a(
              cls := s"nav-link ${if activePage == ActivePage.Plan then "active" else ""}".trim,
              href := "/",
              if activePage == ActivePage.Plan then attr("aria-current") := "page" else cls := ""
            )("Plan")
          ),
          li(cls := "nav-item")(
            a(
              cls := s"nav-link ${if activePage == ActivePage.Radio then "active" else ""}".trim,
              href := "/radio",
              if activePage == ActivePage.Radio then attr("aria-current") := "page" else cls := ""
            )("Radio")
          ),
          li(cls := "nav-item dropdown")(
            a(
              cls := s"nav-link dropdown-toggle ${if isExportActive then "active" else ""}".trim,
              href := "#",
              id := "navbarDropdownExport",
              attr("role") := "button",
              attr("data-bs-toggle") := "dropdown",
              attr("aria-expanded") := "false"
            )("Export"),
            ul(cls := "dropdown-menu", attr("aria-labelledby") := "navbarDropdownExport")(
              li(
                a(
                  cls := s"dropdown-item ${if activePage == ActivePage.ExportRadio then "active" else ""}".trim,
                  href := "/export/radio"
                )("Export Radio CSV")
              ),
              li(
                a(
                  cls := "dropdown-item",
                  href := "/export/pdf",
                  title := "Download the saved plan as a PDF; save edits first"
                )("Export PDF")
              )
            )
          ),
          li(cls := "nav-item")(
            a(
              cls := s"nav-link ${if activePage == ActivePage.Events then "active" else ""}".trim,
              href := "/events",
              if activePage == ActivePage.Events then attr("aria-current") := "page" else cls := ""
            )("Events")
          ),
          if availableEvents.nonEmpty then
            li(cls := "nav-item dropdown")(
              a(
                cls := "nav-link dropdown-toggle",
                href := "#",
                id := "navbarDropdownEvent",
                attr("role") := "button",
                attr("data-bs-toggle") := "dropdown",
                attr("aria-expanded") := "false"
              )(
                "Event: ",
                strong(currentEventName.filter(_.nonEmpty).getOrElse(availableEvents.head))
              ),
              ul(cls := "dropdown-menu", attr("aria-labelledby") := "navbarDropdownEvent")(
                availableEvents.map { evName =>
                  li(
                    a(
                      cls := s"dropdown-item ${if currentEventName.contains(evName) then "active" else ""}".trim,
                      href := s"/events/select?name=${java.net.URLEncoder.encode(evName, "UTF-8")}"
                    )(evName)
                  )
                },
                li(hr(cls := "dropdown-divider")),
                li(
                  a(
                    cls := s"dropdown-item ${if activePage == ActivePage.Events then "active" else ""}".trim,
                    href := "/events"
                  )("Manage Events")
                )
              )
            )
          else
            span(),
          if showUserAdmin then
            li(cls := "nav-item")(
              a(
                cls := s"nav-link ${if activePage == ActivePage.UserAdmin then "active" else ""}".trim,
                href := "/admin/users",
                if activePage == ActivePage.UserAdmin then attr("aria-current") := "page" else cls := ""
              )("User Management")
            )
          else
            span()
        ),
        ul(cls := "navbar-nav ms-auto align-items-center")(
          currentUser match
            case Some(user) =>
              frag(
                li(cls := "nav-item")(
                  span(cls := "navbar-text me-3")(
                    "Logged in as: ",
                    strong(user.username)
                  )
                ),
                li(cls := "nav-item")(
                  a(cls := "nav-link", href := "/logout")("Log out")
                )
              )
            case None =>
              frag(
                li(cls := "nav-item")(
                  a(
                    cls := s"nav-link ${if activePage == ActivePage.Login then "active" else ""}".trim,
                    href := "/login",
                    if activePage == ActivePage.Login then attr("aria-current") := "page" else cls := ""
                  )("Log in")
                ),
                li(cls := "nav-item")(
                  a(cls := "nav-link", href := "/logout")("Log out")
                )
              )
        )
      )
    )
