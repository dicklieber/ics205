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

import cats.effect.IO
import ics205.auth.{AuthConfig, AuthenticationService}
import ics205.model.GroupHelper
import ics205.store.{Ics205Store, UserStore}
import ics205.web.auth.AuthSecurity
import jakarta.inject.{Inject, Singleton}
import sttp.model.StatusCode
import sttp.tapir.*
import sttp.tapir.server.ServerEndpoint

@Singleton
class GroupsEndpoints @Inject()(
  val store: Ics205Store,
  val userStore: UserStore,
  security: AuthSecurity
) extends ApiEndpoints with EventResolving:

  def this(store: Ics205Store, userStore: UserStore, authService: AuthenticationService, config: AuthConfig) =
    this(store, userStore, new AuthSecurity(authService, config))

  private val getGroupsEndpoint: ServerEndpoint[Any, IO] =
    security.secureEndpoint
      .get
      .in("groups")
      .in(query[Option[String]]("event"))
      .out(htmlBodyUtf8)
      .serverLogicSuccess { user => eventQuery =>
        IO.blocking {
          val (currentEventOpt, authorizedEvents) = resolveEvent(eventQuery, user)
          val allEvents = store.listEvents()
          val allUsers = userStore.all()
          val groupInfos = GroupHelper.groupInfos(allEvents, allUsers)
          GroupsPage.render(
            currentUser = Some(user),
            groups = groupInfos,
            currentEventName = currentEventOpt.map(_.eventName),
            availableEvents = authorizedEvents.map(_.eventName)
          )
        }
      }

  override val endpoints: List[ServerEndpoint[Any, IO]] = List(
    getGroupsEndpoint
  )
