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

class NavigationBarTests extends munit.FunSuite:

  val adminUser = AuthenticatedUser(
    username = "adminUser",
    role = RolePermissions.Admin,
    id = "u-admin"
  )

  val viewerUser = AuthenticatedUser(
    username = "viewerUser",
    role = RolePermissions.Viewer,
    id = "u-viewer"
  )

  test("NavigationBar renders brand, main links, and unauthenticated state"):
    val html = NavigationBar.render(activePage = NavigationBar.ActivePage.Plan, currentUser = None).render
    assert(html.contains("navbar"))
    assert(html.contains("navbar-brand"))
    assert(html.contains("ICS 205"))
    assert(html.contains("href=\"/\""))
    assert(html.contains("href=\"/radio\""))
    assert(html.contains("href=\"/export/radio\""))
    assert(html.contains("href=\"/export/pdf\""))
    assert(html.contains("href=\"/login\""))
    assert(html.contains("Log in"))
    assert(html.contains("Log out"))
    assert(!html.contains("User Management"))
    assert(!html.contains("navbarDropdownDebug"))
    assert(!html.contains("Reload Files"))
    assert(!html.contains("Logged in as:"))

  test("NavigationBar shows User Management when user has EditUsers permission"):
    val html = NavigationBar.render(activePage = NavigationBar.ActivePage.None, currentUser = Some(adminUser)).render
    assert(html.contains("User Management"))
    assert(html.contains("href=\"/admin/users\""))
    assert(html.contains("Logged in as:"))
    assert(html.contains("adminUser"))
    assert(html.contains("href=\"/logout\""))
    assert(!html.contains("href=\"/login\""))

  test("NavigationBar shows Debug menu and Reload Files when user has Debug permission"):
    val html = NavigationBar.render(activePage = NavigationBar.ActivePage.None, currentUser = Some(adminUser)).render
    assert(html.contains("navbarDropdownDebug"))
    assert(html.contains("Debug"))
    assert(html.contains("Reload Files"))
    assert(html.contains("href=\"/debug/reload-files\""))

  test("NavigationBar hides Debug menu when user lacks Debug permission"):
    val html = NavigationBar.render(activePage = NavigationBar.ActivePage.Radio, currentUser = Some(viewerUser)).render
    assert(!html.contains("navbarDropdownDebug"))
    assert(!html.contains("Reload Files"))
    assert(!html.contains("href=\"/debug/reload-files\""))

  test("NavigationBar hides User Management when user lacks EditUsers permission"):
    val html = NavigationBar.render(activePage = NavigationBar.ActivePage.Radio, currentUser = Some(viewerUser)).render
    assert(!html.contains("User Management"))
    assert(html.contains("Logged in as:"))
    assert(html.contains("viewerUser"))
    assert(html.contains("href=\"/logout\""))

  test("NavigationBar marks the active page with active class and aria-current"):
    val planHtml = NavigationBar.render(activePage = NavigationBar.ActivePage.Plan).render
    assert(planHtml.contains("nav-link active"))
    assert(planHtml.contains("aria-current=\"page\""))

    val radioHtml = NavigationBar.render(activePage = NavigationBar.ActivePage.Radio).render
    assert(radioHtml.contains("href=\"/radio\""))
    assert(radioHtml.contains("nav-link active"))

    val exportHtml = NavigationBar.render(activePage = NavigationBar.ActivePage.ExportRadio).render
    assert(exportHtml.contains("dropdown-item active"))

    val adminHtml = NavigationBar.render(activePage = NavigationBar.ActivePage.UserAdmin, currentUser = Some(adminUser)).render
    assert(adminHtml.contains("href=\"/admin/users\""))
    assert(adminHtml.contains("nav-link active"))

    val eventsHtml = NavigationBar.render(activePage = NavigationBar.ActivePage.Events, currentUser = Some(adminUser)).render
    assert(eventsHtml.contains("href=\"/events\""))
    assert(eventsHtml.contains("nav-link active"))

  test("NavigationBar renders event dropdown selector when available events are provided"):
    val html = NavigationBar.render(
      activePage = NavigationBar.ActivePage.Plan,
      currentUser = Some(adminUser),
      currentEventName = Some("Field Day"),
      availableEvents = Seq("Field Day", "Marathon 2026")
    ).render

    assert(html.contains("Event:"))
    assert(html.contains("Field Day"))
    assert(html.contains("href=\"/events/select?name=Field+Day\""))
    assert(html.contains("href=\"/events/select?name=Marathon+2026\""))
    assert(html.contains("href=\"/events\""))
    assert(html.contains("Manage Events"))
