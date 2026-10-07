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

import ics205.auth.{AuthenticatedUser, Role}
import ics205.model.Ics205Event
import ics205.store.Ics205Store

trait EventResolving:
  def store: Ics205Store

  def resolveEvent(
    user: AuthenticatedUser,
    eventQuery: Option[String] = None
  ): (Option[Ics205Event], Seq[Ics205Event]) =
    val allEvents = store.listEvents()
    val authorizedEvents = if user.role == Role.Admin then allEvents else allEvents.filter(_.canView(user))
    val chosenEvent = eventQuery.filter(_.nonEmpty).flatMap(store.getEvent)
      .orElse(user.session.currentIcs205.flatMap(store.getEvent))
      .orElse(authorizedEvents.headOption)
    (chosenEvent, authorizedEvents)

  def resolveEvent(
    eventQuery: Option[String],
    user: AuthenticatedUser
  ): (Option[Ics205Event], Seq[Ics205Event]) =
    resolveEvent(user, eventQuery)
