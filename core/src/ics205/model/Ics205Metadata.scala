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

import ics205.auth.*
import io.circe.*
import io.circe.syntax.*

import java.time.Instant

enum PlanAccess derives Codec.AsObject:
  case Edit, ReadOnly

/**
 * A user with this group can access the associated [[Ics205Event]].
 * @param group a user defined group name.
 */
case class Ics205Metadata(
  group: Option[String] = None,
  permissions: Map[String, Permission] = Map.empty,
  lastEditedBy: Option[String] = None,
  savedAt: Instant = Instant.now()
) derives Codec.AsObject:

  def withUserPermission(userId: String, permission: Permission): Ics205Metadata =
    copy(permissions = permissions + (userId -> permission))

  def withUserPermission(userId: String, permissionOpt: Option[Permission]): Ics205Metadata =
    permissionOpt match
      case Some(perm) => withUserPermission(userId, perm)
      case None => withoutUserPermission(userId)

  def withoutUserPermission(userId: String): Ics205Metadata =
    copy(permissions = permissions - userId)

  def withLastEditedBy(userId: String): Ics205Metadata =
    copy(lastEditedBy = Some(userId))

  def withSavedAt(instant: Instant): Ics205Metadata =
    copy(savedAt = instant)

  def accessFor(user: User): Option[PlanAccess] =
    if user.role == Role.Admin then Some(PlanAccess.Edit)
    else permissions.get(user.id) match
      case Some(Permission.EditPlans) => Some(PlanAccess.Edit)
      case Some(Permission.ViewPlans) => Some(PlanAccess.ReadOnly)
      case Some(_) => None
      case None =>
        if permissions.isEmpty then
          if user.role.hasPermission(Permission.EditPlans) then Some(PlanAccess.Edit)
          else if user.role.hasPermission(Permission.ViewPlans) then Some(PlanAccess.ReadOnly)
          else None
        else None

  def accessFor(authUser: AuthenticatedUser): Option[PlanAccess] =
    accessFor(authUser.user)

  def canEdit(user: User): Boolean =
    accessFor(user).contains(PlanAccess.Edit)

  def canEdit(authUser: AuthenticatedUser): Boolean =
    canEdit(authUser.user)

  def canView(user: User): Boolean =
    accessFor(user).isDefined

  def canView(authUser: AuthenticatedUser): Boolean =
    canView(authUser.user)

  def isReadOnly(user: User): Boolean =
    accessFor(user).contains(PlanAccess.ReadOnly)

  def isReadOnly(authUser: AuthenticatedUser): Boolean =
    isReadOnly(authUser.user)



