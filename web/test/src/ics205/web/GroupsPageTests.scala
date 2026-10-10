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
import ics205.model.{GroupInfo, Ics205, Ics205Event, OperationalPeriod}

class GroupsPageTests extends munit.FunSuite:

  val adminUser = AuthenticatedUser(user = "admin", session = Role.Admin)
  val normalUser = AuthenticatedUser(user = "alice", session = Role.User)

  test("GroupsPage renders empty state when no groups exist"):
    val html = GroupsPage.render(
      currentUser = Some(adminUser),
      groups = Seq.empty
    )
    assert(html.contains("No groups found."))

  test("GroupsPage renders compact single table layout for all groups"):
    val user1 = User("carol", "hash", Role.User, enabled = true, id = "u-carol", groups = Set("Logistics"))
    val user2 = User("dave", "hash", Role.User, enabled = false, id = "u-dave", groups = Set("Logistics"))
    val ev1 = Ics205Event("Logistics Comm", Ics205(incidentName = "Wildfire 2026", operationalPeriod = OperationalPeriod(), channels = Seq.empty), group = "Logistics")

    val groups = Seq(
      GroupInfo("Default", Seq.empty, Seq.empty),
      GroupInfo("Logistics", Seq(user1, user2), Seq(ev1))
    )

    val html = GroupsPage.render(
      currentUser = Some(adminUser),
      groups = groups
    )

    // Table structure
    assert(html.contains("table class=\"users-table groups-table\""))
    assert(html.contains("Group</th>"))
    assert(html.contains("Associated Users</th>"))
    assert(html.contains("Associated ICS 205 Events</th>"))

    // Groups
    assert(html.contains("Default"))
    assert(html.contains("Logistics"))

    // Users and status
    assert(html.contains("carol"))
    assert(html.contains("dave"))
    assert(html.contains("Disabled"))
    assert(html.contains(s"/admin/users?edit=${user1.id}#user-form"))
    assert(html.contains(s"/admin/users?edit=${user2.id}#user-form"))

    // Events
    assert(html.contains("Wildfire 2026"))
    assert(html.contains(s"/?event=${java.net.URLEncoder.encode(ev1.id, "UTF-8")}"))
    assert(html.contains(s"/events/metadata?name=${java.net.URLEncoder.encode(ev1.id, "UTF-8")}"))
