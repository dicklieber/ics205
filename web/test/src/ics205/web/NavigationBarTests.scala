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
import ics205.auth.{AuthenticatedUser, Role}
import ics205.util.FileHelper

import java.time.Instant

class NavigationBarTests extends munit.FunSuite:

  val adminUser = AuthenticatedUser(user = "adminUser", session = Role.Admin)

  val viewerUser = AuthenticatedUser(user = "viewerUser", session = Role.Viewer)

  test("NavigationBar renders brand, main links, and unauthenticated state"):
    val html = NavigationBar.render(activePage = NavigationBar.ActivePage.Plan, currentUser = None).render
    assert(html.contains("navbar"))
    assert(html.contains("navbar-brand"))
    assert(html.contains("ICS 205"))
    assert(html.contains("href=\"/\""))
    assert(html.contains("href=\"/radio\""))
    assert(html.contains("href=\"/export/radio\""))
    assert(html.contains("href=\"/export/pdf\""))
    assert(html.contains("href=\"/export/json\""))
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
    assert(html.contains("Change Password"))
    assert(html.contains("href=\"/change-password\""))
    assert(html.contains("href=\"/logout\""))
    assert(!html.contains("href=\"/login\""))

  test("NavigationBar shows Debug menu and Reload Files when user has Debug permission"):
    val html = NavigationBar.render(activePage = NavigationBar.ActivePage.None, currentUser = Some(adminUser)).render
    assert(html.contains("navbarDropdownDebug"))
    assert(html.contains("Debug"))
    assert(html.contains("Logging Configuration"))
    assert(html.contains("href=\"/debug/logging\""))
    assert(html.contains("Logging Configuration (YAML)"))
    assert(html.contains("href=\"/debug/logging/yaml\""))
    assert(html.contains("Reload Files"))
    assert(html.contains("href=\"/debug/reload-files\""))
    assert(html.contains("Download Data Directory"))
    assert(html.contains("href=\"/debug/download-directory.zip\""))
    assert(html.contains("href=\"/docs\""))
    assert(html.contains("API Documentation"))

  test("NavigationBar hides Debug menu when user lacks Debug permission"):
    val html = NavigationBar.render(activePage = NavigationBar.ActivePage.Radio, currentUser = Some(viewerUser)).render
    assert(!html.contains("navbarDropdownDebug"))
    assert(!html.contains("Logging Configuration (YAML)"))
    assert(!html.contains("href=\"/debug/logging/yaml\""))
    assert(!html.contains("Reload Files"))
    assert(!html.contains("href=\"/debug/reload-files\""))
    assert(!html.contains("Download Data Directory"))
    assert(!html.contains("href=\"/debug/download-directory.zip\""))
    assert(!html.contains("href=\"/docs\""))

  test("NavigationBar hides User Management when user lacks EditUsers permission"):
    val html = NavigationBar.render(activePage = NavigationBar.ActivePage.Radio, currentUser = Some(viewerUser)).render
    assert(!html.contains("User Management"))
    assert(html.contains("Logged in as:"))
    assert(html.contains("viewerUser"))
    assert(html.contains("Change Password"))
    assert(html.contains("href=\"/change-password\""))
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

    val changePassHtml = NavigationBar.render(activePage = NavigationBar.ActivePage.ChangePassword, currentUser = Some(adminUser)).render
    assert(changePassHtml.contains("href=\"/change-password\""))
    assert(changePassHtml.contains("nav-link active"))
    assert(changePassHtml.contains("aria-current=\"page\""))

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

  test("NavigationBar renders About menu item and dialog with BuildInfo, FileHelper directory, and Java info"):
    val tempDir = os.temp.dir(prefix = "nav-test-")
    try
      val helper = new FileHelper(tempDir)
      val html = NavigationBar.render(
        activePage = NavigationBar.ActivePage.Plan,
        currentUser = Some(adminUser),
        fileHelper = helper
      ).render

      // About menu item
      assert(html.contains("id=\"navbarAbout\""))
      assert(html.contains(">About</a>"))

      // About dialog presence and close trigger
      assert(html.contains("<dialog id=\"about-dialog\" class=\"about-dialog\""))
      assert(html.contains(s"About ${BuildInfo.name}"))
      assert(html.contains("id=\"about-dialog-close\""))
      assert(html.contains("id=\"about-dialog-close-x\""))

      // All fields from BuildInfo
      assert(html.contains("<dt>name</dt><dd>ICS-205</dd>"))
      assert(html.contains(s"<dt>appName</dt><dd>${BuildInfo.appName}</dd>"))
      assert(html.contains(s"<dt>productName</dt><dd>${BuildInfo.productName}</dd>"))
      assert(html.contains(s"<dt>version</dt><dd>${BuildInfo.version}</dd>"))
      assert(html.contains(s"<dt>scalaVersion</dt><dd>${BuildInfo.scalaVersion}</dd>"))
      assert(html.contains(s"<dt>millVersion</dt><dd>${BuildInfo.millVersion}</dd>"))
      assert(html.contains("<dt>running for</dt>"))

      // ics205.util.FileHelper.directory
      assert(html.contains("<dt>ics205.util.FileHelper.directory</dt>"))
      assert(html.contains(tempDir.toString))
      assert(html.contains("Download ZIP"))
      assert(html.contains("href=\"/debug/download-directory.zip\""))

      // Viewer user does not see Download ZIP in About dialog
      val viewerHtml = NavigationBar.render(
        activePage = NavigationBar.ActivePage.Plan,
        currentUser = Some(viewerUser),
        fileHelper = helper
      ).render
      assert(viewerHtml.contains(tempDir.toString))
      assert(!viewerHtml.contains("Download ZIP"))

      // Java version info
      assert(html.contains("<dt>java.version</dt>"))
      assert(html.contains(s"<dd>${System.getProperty("java.version")}</dd>"))
      assert(html.contains("<dt>java.vendor</dt>"))
      assert(html.contains(s"<dd>${System.getProperty("java.vendor")}</dd>"))
      assert(html.contains("<dt>java.vm.name</dt>"))
      assert(html.contains(s"<dd>${System.getProperty("java.vm.name")}</dd>"))
      assert(html.contains("<dt>java.home</dt>"))
      assert(html.contains(s"<dd>${System.getProperty("java.home")}</dd>"))
    finally
      os.remove.all(tempDir)

  test("aboutDialog formats running for using DurationFormat and provided startTime"):
    val startTime = Instant.now().minusSeconds(125)
    val html = NavigationBar.aboutDialog(startTime = startTime).render
    assert(html.contains("<dt>running for</dt>"))
    assert(html.contains("id=\"about-running-for\""))
    assert(html.contains("2 min 5 sec</dd>"))
