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
import ics205.auth.AuthorizationService.*
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
    val u1 = User("u1", "hash", Role.Viewer, enabled = true, id = "1", groups = Set("Ares Operations", "North Team"))
    val u2 = User("u2", "hash", Role.Viewer, enabled = true, id = "2", groups = Set("North Team", "South Team"))
    val ev1 = Ics205Event("ev1", Ics205(incidentName = "Ev1", operationalPeriod = OperationalPeriod(), channels = Seq.empty), group = "Ares Operations")
    val ev2 = Ics205Event("ev2", Ics205(incidentName = "Ev2", operationalPeriod = OperationalPeriod(), channels = Seq.empty), group = "Default")

    val groups = GroupHelper.knownGroups(Seq(ev1, ev2), Seq(u1, u2))
    assertEquals(groups, Set("Default", "Ares Operations", "North Team", "South Team"))

  test("groupInfos associates users and events with groups and sorts alphabetically"):
    val u1 = User("alice", "hash", Role.Viewer, enabled = true, id = "1", groups = Set("North Team"))
    val u2 = User("bob", "hash", Role.Viewer, enabled = true, id = "2", groups = Set("South Team"))
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

  test("User permissions are checked directly on the user and enforce group boundaries"):
    val admin = User("admin", "hash", Role.Admin)
    val editorWithGroup = User("editor", "hash", Role.Editor, groups = Set("Ops Group"))
    val editorWithoutGroup = User("editor-no-grp", "hash", Role.Editor, groups = Set.empty)
    val viewerWithGroup = User("viewer", "hash", Role.Viewer, groups = Set("Ops Group"))
    val viewerWithoutGroup = User("viewer-no-grp", "hash", Role.Viewer, groups = Set.empty)
    val regularWithGroup = User("user", "hash", Role.Viewer, groups = Set("Default"))

    val evOps = Ics205Event("ev-1", Ics205(incidentName = "Ops Event", operationalPeriod = OperationalPeriod(), channels = Seq.empty), group = "Ops Group")
    val evDefault = Ics205Event("ev-2", Ics205(incidentName = "Default Event", operationalPeriod = OperationalPeriod(), channels = Seq.empty), group = "Default")

    // Admin has AllGroup permission so can access all groups
    assertEquals(evOps.viewableBy(admin), true)
    assertEquals(evOps.editableBy(admin), true)
    assertEquals(evOps.accessBy(admin), Some(EventAccess.Edit))
    assertEquals(evDefault.viewableBy(admin), true)
    assertEquals(evDefault.editableBy(admin), true)

    // Editor with Ops Group can view/edit Ops Event, but not Default Event
    assertEquals(evOps.viewableBy(editorWithGroup), true)
    assertEquals(evOps.editableBy(editorWithGroup), true)
    assertEquals(evOps.accessBy(editorWithGroup), Some(EventAccess.Edit))
    assertEquals(evDefault.viewableBy(editorWithGroup), false)
    assertEquals(evDefault.editableBy(editorWithGroup), false)
    assertEquals(evDefault.accessBy(editorWithGroup), None)

    // Editor without group cannot access any event
    assertEquals(evOps.viewableBy(editorWithoutGroup), false)
    assertEquals(evOps.editableBy(editorWithoutGroup), false)

    // Viewer with Ops Group can view Ops Event as ReadOnly, but not edit
    assertEquals(evOps.viewableBy(viewerWithGroup), true)
    assertEquals(evOps.editableBy(viewerWithGroup), false)
    assertEquals(evOps.accessBy(viewerWithGroup), Some(EventAccess.ReadOnly))
    assertEquals(evDefault.viewableBy(viewerWithGroup), false)

    // Viewer without group cannot access any event
    assertEquals(evOps.viewableBy(viewerWithoutGroup), false)

    // Regular user with Default group can view Default Event as ReadOnly
    assertEquals(evDefault.viewableBy(regularWithGroup), true)
    assertEquals(evDefault.editableBy(regularWithGroup), false)
    assertEquals(evDefault.accessBy(regularWithGroup), Some(EventAccess.ReadOnly))
    assertEquals(evOps.viewableBy(regularWithGroup), false)

    // AuthenticatedUser also satisfies UserContext
    val authAdmin = AuthenticatedUser(admin)
    val authEditor = AuthenticatedUser(editorWithGroup)
    assertEquals(evOps.viewableBy(authAdmin), true)
    assertEquals(evOps.editableBy(authAdmin), true)
    assertEquals(evOps.accessBy(authAdmin), Some(EventAccess.Edit))
    assertEquals(evOps.viewableBy(authEditor), true)
    assertEquals(evOps.editableBy(authEditor), true)
    assertEquals(evDefault.viewableBy(authEditor), false)
