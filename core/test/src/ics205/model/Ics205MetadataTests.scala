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

import ics205.auth.{AuthenticatedUser, AuthorizationService, Permission, RolePermissions, User}
import io.circe.parser.decode
import io.circe.syntax.*

import java.time.Instant

class Ics205MetadataTests extends munit.FunSuite:
  private val adminUser = User("admin", "hash", RolePermissions.Admin, enabled = true, id = "u-admin")
  private val editorUser = User("editor", "hash", RolePermissions.Editor, enabled = true, id = "u-editor")
  private val regularUser = User("user", "hash", RolePermissions.User, enabled = true, id = "u-user")
  private val viewerUser = User("viewer", "hash", RolePermissions.Viewer, enabled = true, id = "u-viewer")

  private val authAdmin = AuthenticatedUser(adminUser.username, adminUser.role, id = adminUser.id)
  private val authEditor = AuthenticatedUser(editorUser.username, editorUser.role, id = editorUser.id)
  private val authUser = AuthenticatedUser(regularUser.username, regularUser.role, id = regularUser.id)
  private val authViewer = AuthenticatedUser(viewerUser.username, viewerUser.role, id = viewerUser.id)

  private val basePlan = Ics205(
    incidentName = "Test Drill",
    operationalPeriod = OperationalPeriod(),
    channels = Seq.empty
  )

  test("admin role can do everything even if not explicitly in metadata permissions"):
    val metaEmpty = Ics205Metadata()
    assertEquals(metaEmpty.accessFor(adminUser), Some(PlanAccess.Edit))
    assertEquals(metaEmpty.accessFor(authAdmin), Some(PlanAccess.Edit))
    assert(metaEmpty.canEdit(adminUser))
    assert(metaEmpty.canView(adminUser))
    assert(!metaEmpty.isReadOnly(adminUser))

    // Even if metadata has restrictive permissions for others, admin can still edit
    val metaRestricted = Ics205Metadata(permissions = Map("u-other" -> Permission.ViewPlans))
    assertEquals(metaRestricted.accessFor(adminUser), Some(PlanAccess.Edit))
    assertEquals(metaRestricted.accessFor(authAdmin), Some(PlanAccess.Edit))
    assert(metaRestricted.canEdit(adminUser))
    assert(metaRestricted.canView(adminUser))

  test("explicit user permissions in metadata determine access"):
    val meta = Ics205Metadata(permissions = Map(
      "u-user" -> Permission.EditPlans,
      "u-editor" -> Permission.ViewPlans
    ))

    // Regular user explicitly given EditPlans can edit
    assertEquals(meta.accessFor(regularUser), Some(PlanAccess.Edit))
    assertEquals(meta.accessFor(authUser), Some(PlanAccess.Edit))
    assert(meta.canEdit(regularUser))
    assert(meta.canView(regularUser))
    assert(!meta.isReadOnly(regularUser))

    // Editor explicitly restricted to ViewPlans is read-only
    assertEquals(meta.accessFor(editorUser), Some(PlanAccess.ReadOnly))
    assertEquals(meta.accessFor(authEditor), Some(PlanAccess.ReadOnly))
    assert(!meta.canEdit(editorUser))
    assert(meta.canView(editorUser))
    assert(meta.isReadOnly(editorUser))

    // Viewer not in permissions map has no access when permissions are explicitly configured
    assertEquals(meta.accessFor(viewerUser), None)
    assertEquals(meta.accessFor(authViewer), None)
    assert(!meta.canEdit(viewerUser))
    assert(!meta.canView(viewerUser))

  test("empty permissions map falls back to system role permissions"):
    val meta = Ics205Metadata()

    // Editor role gets Edit
    assertEquals(meta.accessFor(editorUser), Some(PlanAccess.Edit))
    assert(meta.canEdit(editorUser))
    assert(meta.canView(editorUser))

    // User / Viewer roles get ReadOnly
    assertEquals(meta.accessFor(regularUser), Some(PlanAccess.ReadOnly))
    assert(!meta.canEdit(regularUser))
    assert(meta.canView(regularUser))
    assert(meta.isReadOnly(regularUser))

    assertEquals(meta.accessFor(viewerUser), Some(PlanAccess.ReadOnly))
    assert(!meta.canEdit(viewerUser))
    assert(meta.canView(viewerUser))
    assert(meta.isReadOnly(viewerUser))

  test("metadata modifier methods update state correctly"):
    val meta = Ics205Metadata()
      .withUserPermission("u-1", Permission.ViewPlans)
      .withUserPermission("u-2", Some(Permission.EditPlans))
      .withLastEditedBy("u-2")
      .withSavedAt(Instant.ofEpochMilli(1000000L))

    assertEquals(meta.permissions.get("u-1"), Some(Permission.ViewPlans))
    assertEquals(meta.permissions.get("u-2"), Some(Permission.EditPlans))
    assertEquals(meta.lastEditedBy, Some("u-2"))
    assertEquals(meta.savedAt, Instant.ofEpochMilli(1000000L))

    val removed = meta.withoutUserPermission("u-1").withUserPermission("u-2", None)
    assertEquals(removed.permissions.get("u-1"), None)
    assertEquals(removed.permissions.get("u-2"), None)

  test("Ics205Event wrapper delegates correctly and encodes/decodes to JSON"):
    val savedInstant = Instant.parse("2026-09-29T12:00:00Z")
    val meta = Ics205Metadata(
      permissions = Map("u-1" -> Permission.EditPlans),
      lastEditedBy = Some("u-1"),
      savedAt = savedInstant
    )
    val event = Ics205Event(basePlan, meta)

    assertEquals(event.plan, basePlan)
    assertEquals(event.ics205, basePlan)
    assertEquals(event.metadata, meta)
    assert(event.canEdit(User("user1", "hash", RolePermissions.User, enabled = true, id = "u-1")))

    val json = event.asJson.noSpaces
    val decoded = decode[Ics205Event](json)
    assertEquals(decoded, Right(event))

  test("Ics205Event decodes legacy plain Ics205 JSON"):
    val legacyJson = basePlan.asJson.noSpaces
    val decoded = decode[Ics205Event](legacyJson)
    assert(decoded.isRight)
    val event = decoded.toOption.get
    assertEquals(event.ics205.incidentName, basePlan.incidentName)
    assertEquals(event.metadata.permissions, Map.empty)
    assertEquals(event.metadata.lastEditedBy, None)

  test("AuthorizationService.authorizeEvent checks event metadata permissions"):
    val meta = Ics205Metadata(permissions = Map(
      "u-editor" -> Permission.ViewPlans,
      "u-user" -> Permission.EditPlans
    ))

    // Admin authorized for edit and view
    assertEquals(AuthorizationService.authorizeEvent(authAdmin, meta, Permission.EditPlans), Right(authAdmin))
    assertEquals(AuthorizationService.authorizeEvent(authAdmin, meta, Permission.ViewPlans), Right(authAdmin))

    // Regular user given EditPlans is authorized for edit and view
    assertEquals(AuthorizationService.authorizeEvent(authUser, meta, Permission.EditPlans), Right(authUser))
    assertEquals(AuthorizationService.authorizeEvent(authUser, meta, Permission.ViewPlans), Right(authUser))

    // Editor restricted to ViewPlans is authorized for view, forbidden for edit
    assertEquals(AuthorizationService.authorizeEvent(authEditor, meta, Permission.ViewPlans), Right(authEditor))
    assert(AuthorizationService.authorizeEvent(authEditor, meta, Permission.EditPlans).isLeft)

    // Viewer with no entry is forbidden for both
    assert(AuthorizationService.authorizeEvent(authViewer, meta, Permission.ViewPlans).isLeft)
    assert(AuthorizationService.authorizeEvent(authViewer, meta, Permission.EditPlans).isLeft)
