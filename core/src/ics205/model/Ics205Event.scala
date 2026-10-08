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
                       metadata: Ics205Metadata = Ics205Metadata()) derives Codec.AsObject:
  val fileName: String = s"$id.$extension"

  def bakFileName: String = s"$id-$UtcFormatter().$extension"

  def eventName: String = if ics205.incidentName.nonEmpty then ics205.incidentName else id

  def canView(user: AuthenticatedUser): Boolean = metadata.canView(user)

  def canView(user: User): Boolean = metadata.canView(user)

  def canEdit(user: AuthenticatedUser): Boolean = metadata.canEdit(user)

  def canEdit(user: User): Boolean = metadata.canEdit(user)

  def accessFor(user: AuthenticatedUser): Option[PlanAccess] = metadata.accessFor(user)

  def accessFor(user: User): Option[PlanAccess] = metadata.accessFor(user)

  def update(authenticatedUser: AuthenticatedUser): Ics205Event =
    copy(metadata = metadata.withLastEditedBy(authenticatedUser.user.id).withSavedAt(Instant.now()))


object Ics205Event:
  val extension: String = "ics205"

  given Ordering[Ics205Event] = Ordering.by(_.ics205.incidentName)

  def apply(id: EventId,
            ics205: Ics205,
            metadata: Ics205Metadata): Ics205Event = new Ics205Event(id, ics205, metadata)

  def apply(id: EventId,
            ics205: Ics205): Ics205Event = new Ics205Event(id, ics205, Ics205Metadata())

  def apply(ics205: Ics205,
            metadata: Ics205Metadata): Ics205Event = new Ics205Event(Ids.generateId(), ics205, metadata)

  def apply(ics205: Ics205): Ics205Event = new Ics205Event(Ids.generateId(), ics205, Ics205Metadata())
