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

import ics205.auth.{AuthenticatedUser, Permission, Role, User, UserId}
import ics205.util.{Ids, UtcFormatter}
import ics205.util.Ids.Id
import io.circe.{Codec, Decoder, Encoder, HCursor, Json, JsonObject}
import io.circe.syntax.*
import Ics205Event.extension

import java.time.Instant
import scala.collection.immutable.TreeSeqMap.OrderBy

case class Ics205Event(id: EventId = Ids.generateId(),
                       ics205: Ics205,
                       group: String = "Default"):
  val fileName: String = s"$id.$extension"

  def bakFileName: String = s"$id-$UtcFormatter().$extension"

  def eventName: String = if ics205.incidentName.nonEmpty then ics205.incidentName else id

  def canView(user: AuthenticatedUser): Boolean = user.hasPermission(Permission.ViewPlans)

  def canView(user: User): Boolean = user.role.hasPermission(Permission.ViewPlans)

  def canEdit(user: AuthenticatedUser): Boolean = user.hasPermission(Permission.EditPlans)

  def canEdit(user: User): Boolean = user.role.hasPermission(Permission.EditPlans)

  def accessFor(user: AuthenticatedUser): Option[PlanAccess] =
    if user.role == Role.Admin || user.hasPermission(Permission.EditPlans) then Some(PlanAccess.Edit)
    else if user.hasPermission(Permission.ViewPlans) then Some(PlanAccess.ReadOnly)
    else None

  def accessFor(user: User): Option[PlanAccess] =
    if user.role == Role.Admin || user.role.hasPermission(Permission.EditPlans) then Some(PlanAccess.Edit)
    else if user.role.hasPermission(Permission.ViewPlans) then Some(PlanAccess.ReadOnly)
    else None

  def update(authenticatedUser: AuthenticatedUser): Ics205Event = this


object Ics205Event:
  val extension: String = "ics205"

  given Ordering[Ics205Event] = Ordering.by(_.ics205.incidentName)

  given Codec.AsObject[Ics205Event] = Codec.AsObject.from(
    (c: HCursor) => {
      for
        id <- c.downField("id").as[Option[EventId]].map(_.getOrElse(Ids.generateId()))
        ics205 <- c.downField("ics205").as[Ics205]
        group <- c.downField("group").as[Option[String]].flatMap {
          case Some(g) if g.nonEmpty => Right(g)
          case _ =>
            c.downField("metadata").downField("group").as[Option[String]].map {
              case Some(mg) if mg.nonEmpty => mg
              case _ => "Default"
            }
        }
      yield Ics205Event(id = id, ics205 = ics205, group = group)
    },
    (e: Ics205Event) => JsonObject(
      "id" -> Json.fromString(e.id),
      "ics205" -> e.ics205.asJson,
      "group" -> Json.fromString(e.group)
    )
  )

  def apply(id: EventId,
            ics205: Ics205,
            group: String): Ics205Event = new Ics205Event(id, ics205, group)

  def apply(id: EventId,
            ics205: Ics205): Ics205Event = new Ics205Event(id, ics205, "Default")

  def apply(ics205: Ics205,
            group: String): Ics205Event = new Ics205Event(Ids.generateId(), ics205, group)

  def apply(ics205: Ics205): Ics205Event = new Ics205Event(Ids.generateId(), ics205, "Default")
