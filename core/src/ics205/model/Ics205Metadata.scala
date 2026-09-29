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

import ics205.auth.{AuthenticatedUser, Permission, RolePermissions, User}
import ics205.util.Ids.UserId
import io.circe.{Codec, Decoder, Encoder, HCursor, Json, JsonObject}
import io.circe.syntax.*

import java.time.Instant

enum PlanAccess:
  case ReadOnly
  case Edit

type EventAccess = PlanAccess
val EventAccess: PlanAccess.type = PlanAccess

case class Ics205Metadata(
  permissions: Map[UserId, Permission] = Map.empty,
  lastEditedBy: Option[UserId] = None,
  savedAt: Instant = Instant.now()
):
  def userPermissions: Map[UserId, Permission] = permissions
  def lastEditedByUserId: Option[UserId] = lastEditedBy
  def savedInstant: Instant = savedAt

  def permissionFor(userId: UserId): Option[Permission] =
    permissions.get(userId)

  def permissionFor(user: User): Option[Permission] =
    permissionFor(user.id, user.role)

  def permissionFor(user: AuthenticatedUser): Option[Permission] =
    permissionFor(user.id, user.role)

  def permissionFor(userId: UserId, role: RolePermissions): Option[Permission] =
    if role == RolePermissions.Admin then
      Some(Permission.EditPlans)
    else
      permissions.get(userId).orElse {
        if permissions.isEmpty then
          if role.hasPermission(Permission.EditPlans) then Some(Permission.EditPlans)
          else if role.hasPermission(Permission.ViewPlans) then Some(Permission.ViewPlans)
          else None
        else
          None
      }

  def accessFor(user: User): Option[PlanAccess] =
    permissionFor(user).flatMap {
      case Permission.EditPlans => Some(PlanAccess.Edit)
      case Permission.ViewPlans => Some(PlanAccess.ReadOnly)
      case _ => None
    }

  def accessFor(user: AuthenticatedUser): Option[PlanAccess] =
    permissionFor(user).flatMap {
      case Permission.EditPlans => Some(PlanAccess.Edit)
      case Permission.ViewPlans => Some(PlanAccess.ReadOnly)
      case _ => None
    }

  def accessFor(userId: UserId, role: RolePermissions): Option[PlanAccess] =
    permissionFor(userId, role).flatMap {
      case Permission.EditPlans => Some(PlanAccess.Edit)
      case Permission.ViewPlans => Some(PlanAccess.ReadOnly)
      case _ => None
    }

  def canEdit(user: User): Boolean =
    permissionFor(user).contains(Permission.EditPlans)

  def canEdit(user: AuthenticatedUser): Boolean =
    permissionFor(user).contains(Permission.EditPlans)

  def canView(user: User): Boolean =
    permissionFor(user).isDefined

  def canView(user: AuthenticatedUser): Boolean =
    permissionFor(user).isDefined

  def isReadOnly(user: User): Boolean =
    permissionFor(user).contains(Permission.ViewPlans)

  def isReadOnly(user: AuthenticatedUser): Boolean =
    permissionFor(user).contains(Permission.ViewPlans)

  def isEdit(user: User): Boolean = canEdit(user)
  def isEdit(user: AuthenticatedUser): Boolean = canEdit(user)

  def withUserPermission(userId: UserId, permission: Option[Permission]): Ics205Metadata =
    permission match
      case Some(p) => copy(permissions = permissions + (userId -> p))
      case None    => copy(permissions = permissions - userId)

  def withUserPermission(userId: UserId, permission: Permission): Ics205Metadata =
    copy(permissions = permissions + (userId -> permission))

  def withoutUserPermission(userId: UserId): Ics205Metadata =
    copy(permissions = permissions - userId)

  def withLastEditedBy(userId: UserId): Ics205Metadata =
    copy(lastEditedBy = Some(userId))

  def withSavedAt(instant: Instant): Ics205Metadata =
    copy(savedAt = instant)

object Ics205Metadata:
  given Codec.AsObject[Ics205Metadata] = Codec.AsObject.from(
    (c: HCursor) => {
      for
        perms <- c.downField("permissions").as[Option[Map[UserId, Permission]]].flatMap {
          case Some(p) => Right(p)
          case None => c.downField("userPermissions").as[Option[Map[UserId, Permission]]].map(_.getOrElse(Map.empty))
        }
        lastEdited <- c.downField("lastEditedBy").as[Option[UserId]].flatMap {
          case Some(u) => Right(Some(u))
          case None => c.downField("lastEditedByUserId").as[Option[UserId]]
        }
        saved <- c.downField("savedAt").as[Option[Instant]].flatMap {
          case Some(s) => Right(s)
          case None => c.downField("saved").as[Option[Instant]].map(_.getOrElse(Instant.now()))
        }
      yield Ics205Metadata(
        permissions = perms,
        lastEditedBy = lastEdited,
        savedAt = saved
      )
    },
    (m: Ics205Metadata) => {
      val fields = scala.collection.mutable.ListBuffer[(String, Json)]()
      if m.permissions.nonEmpty then
        fields += ("permissions" -> m.permissions.asJson)
      m.lastEditedBy.foreach { uid =>
        fields += ("lastEditedBy" -> Json.fromString(uid))
      }
      fields += ("savedAt" -> m.savedAt.asJson)
      JsonObject.fromIterable(fields)
    }
  )
