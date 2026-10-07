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
import ics205.auth.{AuthConfig, AuthenticatedUser, AuthenticationService, Role}
import ics205.log.Ics205ActivityLogger
import ics205.store.Ics205Store
import ics205.web.auth.AuthSecurity
import io.circe.syntax.*
import jakarta.inject.{Inject, Singleton}
import sttp.model.StatusCode
import sttp.tapir.*
import sttp.tapir.server.ServerEndpoint

@Singleton
class JsonExportEndpoints @Inject()(
  val store: Ics205Store,
  security: AuthSecurity
) extends ApiEndpoints with EventResolving:

  def this(store: Ics205Store, authService: AuthenticationService, config: AuthConfig) =
    this(store, new AuthSecurity(authService, config))

  override val endpoints: List[ServerEndpoint[Any, IO]] = List(
    security.secureEndpoint
      .get
      .in("export" / "json")
      .in(query[Option[String]]("event"))
      .out(statusCode.and(header[Option[String]]("Location"))
        .and(header[String]("Content-Type"))
        .and(header[Option[String]]("Content-Disposition"))
        .and(header[String]("Cache-Control"))
        .and(stringBody))
      .serverLogicSuccess { user => eventQuery =>
        IO.blocking {
          val (currentEventOpt, _) = resolveEvent(eventQuery, user)

          currentEventOpt match
            case None =>
              (StatusCode.SeeOther, Some("/events"), "text/plain", None, "no-store", "")
            case Some(currentEvent) =>
              if !currentEvent.canView(user) && user.role != Role.Admin then
                (StatusCode.Forbidden, None, "text/plain", None, "no-store", "")
              else
                Ics205ActivityLogger.logExport(
                  username = user.user.username,
                  eventName = currentEvent.eventName,
                  format = "json",
                  incidentName = Option(currentEvent.ics205.incidentName).filter(_.nonEmpty),
                  channelCount = Some(currentEvent.ics205.channels.size)
                )
                val sanitizedName = if currentEvent.eventName.trim.nonEmpty then
                  currentEvent.eventName.trim.replaceAll("""[\\/:*?"<>|]""", "_")
                else "ics205"
                (StatusCode.Ok, None, "application/json; charset=utf-8",
                  Some(s"""attachment; filename="$sanitizedName.json""""),
                  "no-store", currentEvent.asJson.spaces2)
        }
      }
  )
