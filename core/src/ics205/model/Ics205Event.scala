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

case class Ics205Event(
  ics205: Ics205,
  metadata: Ics205Metadata = Ics205Metadata()
):
  def plan: Ics205 = ics205

  def permissionFor(user: User): Option[Permission] =
    metadata.permissionFor(user)

  def permissionFor(user: AuthenticatedUser): Option[Permission] =
    metadata.permissionFor(user)

  def permissionFor(userId: UserId): Option[Permission] =
    metadata.permissionFor(userId)

  def accessFor(user: User): Option[PlanAccess] =
    metadata.accessFor(user)

  def accessFor(user: AuthenticatedUser): Option[PlanAccess] =
    metadata.accessFor(user)

  def accessFor(userId: UserId, role: RolePermissions): Option[PlanAccess] =
    metadata.accessFor(userId, role)

  def canEdit(user: User): Boolean =
    metadata.canEdit(user)

  def canEdit(user: AuthenticatedUser): Boolean =
    metadata.canEdit(user)

  def canView(user: User): Boolean =
    metadata.canView(user)

  def canView(user: AuthenticatedUser): Boolean =
    metadata.canView(user)

  def isReadOnly(user: User): Boolean =
    metadata.isReadOnly(user)

  def isReadOnly(user: AuthenticatedUser): Boolean =
    metadata.isReadOnly(user)

  def isEdit(user: User): Boolean =
    metadata.isEdit(user)

  def isEdit(user: AuthenticatedUser): Boolean =
    metadata.isEdit(user)

object Ics205Event:
  given Codec.AsObject[Ics205Event] = Codec.AsObject.from(
    (c: HCursor) => {
      if c.downField("ics205").succeeded then
        for
          plan <- c.downField("ics205").as[Ics205]
          meta <- c.downField("metadata").as[Option[Ics205Metadata]].map(_.getOrElse(Ics205Metadata()))
        yield Ics205Event(plan, meta)
      else if c.downField("plan").succeeded then
        for
          plan <- c.downField("plan").as[Ics205]
          meta <- c.downField("metadata").as[Option[Ics205Metadata]].map(_.getOrElse(Ics205Metadata()))
        yield Ics205Event(plan, meta)
      else
        c.as[Ics205].map(plan => Ics205Event(plan, Ics205Metadata()))
    },
    (e: Ics205Event) => JsonObject(
      "ics205" -> e.ics205.asJson,
      "metadata" -> e.metadata.asJson
    )
  )

type Ics205Wrapper = Ics205Event
val Ics205Wrapper: Ics205Event.type = Ics205Event
