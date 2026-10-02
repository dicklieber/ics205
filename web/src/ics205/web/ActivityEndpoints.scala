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
import com.typesafe.scalalogging.LazyLogging
import ics205.auth.{AuthConfig, AuthenticationService}
import ics205.log.Ics205ActivityLogger
import ics205.store.Ics205Store
import io.circe.Codec
import io.circe.derivation.{Configuration, ConfiguredCodec}
import jakarta.inject.{Inject, Singleton}
import sttp.model.StatusCode
import sttp.tapir.*
import sttp.tapir.generic.auto.*
import sttp.tapir.json.circe.*
import sttp.tapir.server.ServerEndpoint

case class ImportLogRequest(
  eventName: String,
  incidentName: Option[String] = None,
  channelCount: Option[Int] = None,
  fileName: Option[String] = None,
  format: Option[String] = Some("json")
)

object ImportLogRequest:
  private given Configuration = Configuration.default.withDefaults
  given Codec.AsObject[ImportLogRequest] = ConfiguredCodec.derived[ImportLogRequest]

case class ExportLogRequest(
  eventName: String,
  format: Option[String] = Some("json"),
  incidentName: Option[String] = None,
  channelCount: Option[Int] = None
)

object ExportLogRequest:
  private given Configuration = Configuration.default.withDefaults
  given Codec.AsObject[ExportLogRequest] = ConfiguredCodec.derived[ExportLogRequest]

case class ActivityLogResponse(
  status: String,
  message: String
)

object ActivityLogResponse:
  private given Configuration = Configuration.default.withDefaults
  given Codec.AsObject[ActivityLogResponse] = ConfiguredCodec.derived[ActivityLogResponse]

@Singleton
class ActivityEndpoints @Inject()(
  store: Ics205Store,
  authService: AuthenticationService,
  config: AuthConfig
) extends ApiEndpoints with LazyLogging:

  private val logImportEndpoint: ServerEndpoint[Any, IO] = endpoint.post
    .in("import" / "log")
    .in(cookie[Option[String]](config.cookieName))
    .in(jsonBody[ImportLogRequest])
    .out(statusCode.and(jsonBody[ActivityLogResponse]))
    .errorOut(statusCode.and(jsonBody[ActivityLogResponse]))
    .serverLogic { (sessionIdOpt, req) =>
      IO.blocking {
        sessionIdOpt.flatMap(id => authService.authenticateSession(id).toOption) match
          case None =>
            Left((StatusCode.Unauthorized, ActivityLogResponse("error", "Authentication required")))
          case Some(user) =>
            val evName = if req.eventName.trim.nonEmpty then req.eventName.trim else store.currentEventName.getOrElse("ics205")
            Ics205ActivityLogger.logImport(
              username = user.username,
              eventName = evName,
              incidentName = req.incidentName,
              channelCount = req.channelCount,
              fileName = req.fileName,
              format = req.format.orElse(Some("json"))
            )
            Right((StatusCode.Ok, ActivityLogResponse("ok", "Import activity logged")))
      }
    }

  private val logExportEndpoint: ServerEndpoint[Any, IO] = endpoint.post
    .in("export" / "log")
    .in(cookie[Option[String]](config.cookieName))
    .in(jsonBody[ExportLogRequest])
    .out(statusCode.and(jsonBody[ActivityLogResponse]))
    .errorOut(statusCode.and(jsonBody[ActivityLogResponse]))
    .serverLogic { (sessionIdOpt, req) =>
      IO.blocking {
        sessionIdOpt.flatMap(id => authService.authenticateSession(id).toOption) match
          case None =>
            Left((StatusCode.Unauthorized, ActivityLogResponse("error", "Authentication required")))
          case Some(user) =>
            val evName = if req.eventName.trim.nonEmpty then req.eventName.trim else store.currentEventName.getOrElse("ics205")
            Ics205ActivityLogger.logExport(
              username = user.username,
              eventName = evName,
              format = req.format.getOrElse("json"),
              incidentName = req.incidentName,
              channelCount = req.channelCount
            )
            Right((StatusCode.Ok, ActivityLogResponse("ok", "Export activity logged")))
      }
    }

  override val endpoints: List[ServerEndpoint[Any, IO]] = List(
    logImportEndpoint,
    logExportEndpoint
  )
