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
import ics205.exporter.Ics205PdfExporter
import ics205.log.Ics205ActivityLogger
import ics205.store.Ics205Store
import ics205.web.auth.AuthSecurity
import jakarta.inject.{Inject, Singleton}
import sttp.model.StatusCode
import sttp.tapir.*
import sttp.tapir.server.ServerEndpoint

@Singleton
class PdfExportEndpoints @Inject()(
  exporter: Ics205PdfExporter,
  val store: Ics205Store,
  security: AuthSecurity
) extends ApiEndpoints with EventResolving:

  def this(exporter: Ics205PdfExporter, store: Ics205Store, authService: AuthenticationService, config: AuthConfig) =
    this(exporter, store, new AuthSecurity(authService, config))

  override val endpoints: List[ServerEndpoint[Any, IO]] = List(
    security.secureEndpoint
      .get
      .in("export" / "pdf")
      .in(query[Option[String]]("event"))
      .out(statusCode.and(header[Option[String]]("Location"))
        .and(header[String]("Content-Type"))
        .and(header[Option[String]]("Content-Disposition"))
        .and(header[String]("Cache-Control"))
        .and(byteArrayBody))
      .serverLogicSuccess { user => eventQuery =>
        IO.blocking {
          val (currentEventOpt, _) = resolveEvent(eventQuery, user)

          currentEventOpt match
            case None =>
              (StatusCode.SeeOther, Some("/events"), "text/plain", None, "no-store", Array.emptyByteArray)
            case Some(currentEvent) =>
              if !currentEvent.canView(user) && user.role != Role.Admin then
                (StatusCode.Forbidden, None, "text/plain", None, "no-store", Array.emptyByteArray)
              else
                Ics205ActivityLogger.logExport(
                  username = user.user.username,
                  eventName = currentEvent.eventName,
                  format = "pdf",
                  incidentName = Option(currentEvent.ics205.incidentName).filter(_.nonEmpty),
                  channelCount = Some(currentEvent.ics205.channels.size)
                )
                (StatusCode.Ok, None, "application/pdf", Some("attachment; filename=\"ics205.pdf\""),
                  "no-store", exporter.generatePdf(currentEvent.ics205))
        }
      }
  )
