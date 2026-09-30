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

import ics205.BuildInfo
import ics205.auth.{AuthenticatedUser, Permission}
import ics205.util.FileHelper
import scalatags.Text.all.*
import scalatags.Text.tags2.nav

object NavigationBar:

  enum ActivePage:
    case Plan, Radio, ExportRadio, Events, UserAdmin, Login, None

  def render(
    activePage: ActivePage = ActivePage.None,
    currentUser: Option[AuthenticatedUser] = None,
    currentEventName: Option[String] = None,
    availableEvents: Seq[String] = Seq.empty,
    fileHelper: FileHelper = new FileHelper()
  ): Frag =
    val showUserAdmin = currentUser.exists(_.hasPermission(Permission.EditUsers))
    val showDebug = currentUser.exists(_.hasPermission(Permission.Debug))
    val isExportActive = activePage == ActivePage.ExportRadio
    val isEventsActive = activePage == ActivePage.Events

    frag(
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
                ),
                li(
                  a(
                    cls := "dropdown-item",
                    href := "/export/json",
                    title := "Download the saved plan as a JSON file; save edits first"
                  )("Export JSON")
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
              span(),
            if showDebug then
              li(cls := "nav-item dropdown")(
                a(
                  cls := "nav-link dropdown-toggle",
                  href := "#",
                  id := "navbarDropdownDebug",
                  attr("role") := "button",
                  attr("data-bs-toggle") := "dropdown",
                  attr("aria-expanded") := "false"
                )(
                  "Debug"
                ),
                ul(cls := "dropdown-menu", attr("aria-labelledby") := "navbarDropdownDebug")(
                  li(
                    a(
                      cls := "dropdown-item",
                      href := "/debug/reload-files"
                    )("Reload Files")
                  ),
                  li(
                    a(
                      cls := "dropdown-item",
                      href := "/docs"
                    )("API Documentation")
                  )
                )
              )
            else
              span(),
            li(cls := "nav-item")(
              a(
                cls := "nav-link",
                href := "#",
                id := "navbarAbout",
                attr("role") := "button",
                attr("aria-haspopup") := "dialog",
                onclick := "const d = document.getElementById('about-dialog'); if (d && d.showModal) d.showModal(); return false;"
              )("About")
            )
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
      ),
      aboutDialog(fileHelper)
    )

  def aboutDialog(fileHelper: FileHelper = new FileHelper()): Frag =
    val buildInfoFields = Seq(
      "name" -> BuildInfo.name,
      "appName" -> BuildInfo.appName,
      "productName" -> BuildInfo.productName,
      "version" -> BuildInfo.version,
      "scalaVersion" -> BuildInfo.scalaVersion,
      "millVersion" -> BuildInfo.millVersion
    )

    val javaProperties = Seq(
      "java.version" -> System.getProperty("java.version", "Unknown"),
      "java.vendor" -> System.getProperty("java.vendor", "Unknown"),
      "java.vm.name" -> System.getProperty("java.vm.name", "Unknown"),
      "java.vm.version" -> System.getProperty("java.vm.version", "Unknown"),
      "java.runtime.name" -> System.getProperty("java.runtime.name", "Unknown"),
      "java.home" -> System.getProperty("java.home", "Unknown")
    )

    tag("dialog")(
      id := "about-dialog",
      cls := "about-dialog",
      onclick := "if (event.target === this && this.close) this.close();"
    )(
      form(method := "dialog", cls := "about-dialog-form")(
        div(cls := "about-dialog-header")(
          h3(s"About ${BuildInfo.name}"),
          button(
            tpe := "submit",
            id := "about-dialog-close-x",
            cls := "about-close-x",
            attr("aria-label") := "Close",
            value := "close"
          )("×")
        ),
        div(cls := "about-dialog-body")(
          div(cls := "about-section")(
            h4("Build Information"),
            dl(cls := "about-dl")(
              buildInfoFields.map { case (key, value) =>
                div(cls := "about-field")(
                  dt(key),
                  dd(value)
                )
              }
            )
          ),
          div(cls := "about-section")(
            h4("File Storage"),
            dl(cls := "about-dl")(
              div(cls := "about-field")(
                dt("ics205.util.FileHelper.directory"),
                dd(fileHelper.directory.toString)
              )
            )
          ),
          div(cls := "about-section")(
            h4("Java Version Information"),
            dl(cls := "about-dl")(
              javaProperties.map { case (key, value) =>
                div(cls := "about-field")(
                  dt(key),
                  dd(value)
                )
              }
            )
          )
        ),
        div(cls := "about-dialog-actions")(
          button(
            tpe := "submit",
            id := "about-dialog-close",
            cls := "btn btn-primary",
            value := "close"
          )("Close")
        )
      )
    )
