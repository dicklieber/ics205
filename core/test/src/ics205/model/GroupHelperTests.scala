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

package ics205.model

import ics205.auth.{AuthenticatedUser, Permission, Role, User}
import io.circe.Json
import io.circe.parser.decode
import io.circe.syntax.*

class GroupHelperTests extends munit.FunSuite:

  test("formatGroupName capitalizes words properly"):
    assertEquals(GroupHelper.formatGroupName("hello world"), "Hello World")
    assertEquals(GroupHelper.formatGroupName("HELLO WORLD"), "Hello World")
    assertEquals(GroupHelper.formatGroupName("hElLo wOrLd"), "Hello World")
    assertEquals(GroupHelper.formatGroupName("  ares   operations  "), "Ares Operations")
    assertEquals(GroupHelper.formatGroupName("field day"), "Field Day")
    assertEquals(GroupHelper.formatGroupName(""), "")
    assertEquals(GroupHelper.formatGroupName("   "), "")

  test("cleanGroupName defaults empty names to Default"):
    assertEquals(GroupHelper.cleanGroupName("hello world"), "Hello World")
    assertEquals(GroupHelper.cleanGroupName(""), "Default")
    assertEquals(GroupHelper.cleanGroupName("   "), "Default")

  test("knownGroups aggregates groups from events and users including Default"):
    val u1 = User("u1", "hash", Role.User, enabled = true, id = "1", groups = Set("Ares Operations", "North Team"))
    val u2 = User("u2", "hash", Role.User, enabled = true, id = "2", groups = Set("North Team", "South Team"))
    val ev1 = Ics205Event("ev1", Ics205(incidentName = "Ev1", operationalPeriod = OperationalPeriod(), channels = Seq.empty), group = "Ares Operations")
    val ev2 = Ics205Event("ev2", Ics205(incidentName = "Ev2", operationalPeriod = OperationalPeriod(), channels = Seq.empty), group = "Default")

    val groups = GroupHelper.knownGroups(Seq(ev1, ev2), Seq(u1, u2))
    assertEquals(groups, Set("Default", "Ares Operations", "North Team", "South Team"))

  test("groupInfos associates users and events with groups and sorts alphabetically"):
    val u1 = User("alice", "hash", Role.User, enabled = true, id = "1", groups = Set("North Team"))
    val u2 = User("bob", "hash", Role.User, enabled = true, id = "2", groups = Set("South Team"))
    val ev1 = Ics205Event("ev1", Ics205(incidentName = "North Drill", operationalPeriod = OperationalPeriod(), channels = Seq.empty), group = "North Team")
    val ev2 = Ics205Event("ev2", Ics205(incidentName = "General Incident", operationalPeriod = OperationalPeriod(), channels = Seq.empty), group = "Default")

    val infos = GroupHelper.groupInfos(Seq(ev1, ev2), Seq(u1, u2))
    assertEquals(infos.map(_.name), Seq("Default", "North Team", "South Team"))

    val defaultGroup = infos.find(_.name == "Default").get
    assertEquals(defaultGroup.users, Seq.empty)
    assertEquals(defaultGroup.events.map(_.eventName), Seq("General Incident"))

    val northGroup = infos.find(_.name == "North Team").get
    assertEquals(northGroup.users.map(_.username), Seq("alice"))
    assertEquals(northGroup.events.map(_.eventName), Seq("North Drill"))

    val southGroup = infos.find(_.name == "South Team").get
    assertEquals(southGroup.users.map(_.username), Seq("bob"))
    assertEquals(southGroup.events, Seq.empty)

  test("Ics205Event serializes and deserializes group and handles legacy metadata"):
    val ev = Ics205Event("ev-1", Ics205(incidentName = "Wildfire", operationalPeriod = OperationalPeriod(), channels = Seq.empty), group = "Ops Group")
    val json = ev.asJson.noSpaces
    val decoded = decode[Ics205Event](json).toOption.get
    assertEquals(decoded.id, "ev-1")
    assertEquals(decoded.group, "Ops Group")

    // Default group when omitted
    val defaultEv = Ics205Event("ev-2", Ics205(incidentName = "Storm", operationalPeriod = OperationalPeriod(), channels = Seq.empty))
    assertEquals(defaultEv.group, "Default")

    // Legacy JSON with metadata.group
    val legacyEvent = Ics205Event("ev-legacy", Ics205(incidentName = "Legacy Event", operationalPeriod = OperationalPeriod(), channels = Seq.empty))
    val legacyJson = io.circe.parser.parse(legacyEvent.asJson.noSpaces).toOption.get
      .mapObject(_.remove("group").add("metadata", Json.obj("group" -> Json.fromString("Legacy Group")))).noSpaces
    val legacyDecoded = decode[Ics205Event](legacyJson).toOption.get
    assertEquals(legacyDecoded.id, "ev-legacy")
    assertEquals(legacyDecoded.group, "Legacy Group")

  test("User permissions are checked directly on the user, not specific Ics205Events"):
    val admin = User("admin", "hash", Role.Admin)
    val editor = User("editor", "hash", Role.Editor)
    val viewer = User("viewer", "hash", Role.Viewer)
    val regular = User("user", "hash", Role.User)

    val ev = Ics205Event("ev-1", Ics205(incidentName = "Event 1", operationalPeriod = OperationalPeriod(), channels = Seq.empty))

    assertEquals(ev.canView(admin), true)
    assertEquals(ev.canEdit(admin), true)
    assertEquals(ev.accessFor(admin), Some(PlanAccess.Edit))

    assertEquals(ev.canView(editor), true)
    assertEquals(ev.canEdit(editor), true)
    assertEquals(ev.accessFor(editor), Some(PlanAccess.Edit))

    assertEquals(ev.canView(viewer), true)
    assertEquals(ev.canEdit(viewer), false)
    assertEquals(ev.accessFor(viewer), Some(PlanAccess.ReadOnly))

    assertEquals(ev.canView(regular), true)
    assertEquals(ev.canEdit(regular), false)
    assertEquals(ev.accessFor(regular), Some(PlanAccess.ReadOnly))
