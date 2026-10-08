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
import ics205.auth.Permission
import ics205.util.{LazyLoggingDiscoverer, LoggingConfig, LoggingStore}
import ics205.web.admin.{LoggingPage, LoggingYamlPage}
import ics205.web.auth.AuthSecurity
import io.circe.Codec
import jakarta.inject.{Inject, Singleton}
import sttp.model.StatusCode
import sttp.tapir.*
import sttp.tapir.generic.auto.*
import sttp.tapir.json.circe.*
import sttp.tapir.server.ServerEndpoint

import java.net.URLEncoder
import java.nio.charset.StandardCharsets

case class LoggingStatusResponse(
  discovered: Seq[String],
  configured: Map[String, String],
  availableLevels: Seq[String]
) derives Codec.AsObject

case class SetLevelRequest(
  logger: String,
  level: String
) derives Codec.AsObject

case class StatusMessageResponse(
  status: String,
  message: String
) derives Codec.AsObject

@Singleton
class LoggingEndpoints @Inject()(
  loggingStore: LoggingStore,
  security: AuthSecurity
) extends ApiEndpoints with LazyLogging:

  private def urlEncode(s: String): String =
    URLEncoder.encode(s, StandardCharsets.UTF_8)

  private def renderLoggingPage(
    user: ics205.auth.AuthenticatedUser,
    selectedLogger: Option[String],
    msg: Option[String],
    err: Option[String]
  ): String =
    val discovered = LazyLoggingDiscoverer.discoverLazyLoggers()
    val configured = loggingStore.getAllConfigured()
    val allKnown = (discovered ++ configured.keys).distinct.sorted
    val effectiveLevels = allKnown.map(name => name -> loggingStore.getEffectiveLevel(name)).toMap
    LoggingPage.render(
      currentUser = Some(user),
      discoveredLoggers = discovered,
      configuredLoggers = configured,
      effectiveLevels = effectiveLevels,
      selectedLogger = selectedLogger,
      message = msg,
      error = err
    )

  private val getLoggingEndpoint: ServerEndpoint[Any, IO] =
    security.authorizedEndpoint(Permission.Debug)
      .get
      .in("debug" / "logging")
      .in(query[Option[String]]("logger"))
      .in(query[Option[String]]("msg"))
      .in(query[Option[String]]("err"))
      .out(htmlBodyUtf8)
      .serverLogicSuccess { user => (loggerOpt, msgOpt, errOpt) =>
        IO.blocking {
          renderLoggingPage(user, loggerOpt, msgOpt, errOpt)
        }
      }

  private val postLoggingEndpoint: ServerEndpoint[Any, IO] =
    security.authorizedEndpoint(Permission.Debug)
      .post
      .in("debug" / "logging")
      .in(formBody[Map[String, String]])
      .out(statusCode.and(header[String]("Location")))
      .serverLogicSuccess { user => formData =>
        IO.blocking {
          val loggerName = formData.get("logger").map(_.trim).filter(_.nonEmpty)
          val level = formData.get("level").map(_.trim).filter(_.nonEmpty)
          val action = formData.get("action").map(_.trim.toLowerCase)

          loggerName match
            case None =>
              (StatusCode.SeeOther, s"/debug/logging?err=${urlEncode("Logger name is required")}")
            case Some(name) =>
              try
                if action.contains("reset") || action.contains("delete") then
                  loggingStore.resetLevel(name)
                  (StatusCode.SeeOther, s"/debug/logging?msg=${urlEncode(s"Reset log level for '$name'")}")
                else
                  level match
                    case Some(lvl) =>
                      loggingStore.setLevel(name, lvl)
                      (StatusCode.SeeOther, s"/debug/logging?msg=${urlEncode(s"Set log level for '$name' to $lvl")}")
                    case None =>
                      (StatusCode.SeeOther, s"/debug/logging?err=${urlEncode("Log level is required")}")
              catch
                case ex: Exception =>
                  logger.error(s"Failed to update log level for $name", ex)
                  (StatusCode.SeeOther, s"/debug/logging?err=${urlEncode(s"Failed to update: ${ex.getMessage}")}")
        }
      }

  private val apiGetLoggingEndpoint: ServerEndpoint[Any, IO] =
    security.authorizedEndpoint(Permission.Debug)
      .get
      .in("api" / "debug" / "logging")
      .out(jsonBody[LoggingStatusResponse])
      .serverLogicSuccess { _ => _ =>
        IO.blocking {
          val discovered = LazyLoggingDiscoverer.discoverLazyLoggers()
          val configured = loggingStore.getAllConfigured()
          LoggingStatusResponse(
            discovered = discovered,
            configured = configured,
            availableLevels = LoggingStore.AvailableLevels
          )
        }
      }

  private val apiSetLevelEndpoint: ServerEndpoint[Any, IO] =
    security.authorizedEndpoint(Permission.Debug)
      .post
      .in("api" / "debug" / "logging")
      .in(jsonBody[SetLevelRequest])
      .out(jsonBody[StatusMessageResponse])
      .serverLogicSuccess { _ => req =>
        IO.blocking {
          loggingStore.setLevel(req.logger, req.level)
          StatusMessageResponse("ok", s"Log level for '${req.logger}' set to '${req.level}'")
        }
      }

  private val getLoggingYamlPageEndpoint: ServerEndpoint[Any, IO] =
    security.authorizedEndpoint(Permission.Debug)
      .get
      .in("debug" / "logging" / "yaml")
      .out(htmlBodyUtf8)
      .serverLogicSuccess { user => _ =>
        IO.blocking {
          val configured = loggingStore.getAllConfigured()
          val yaml = LoggingConfig.toYaml(configured)
          LoggingYamlPage.render(Some(user), yaml)
        }
      }

  private val getLoggingYamlFileEndpoint: ServerEndpoint[Any, IO] =
    security.authorizedEndpoint(Permission.Debug)
      .get
      .in("debug" / "logging.yaml")
      .out(header[String]("Content-Type").and(stringBody))
      .serverLogicSuccess { _ => _ =>
        IO.blocking {
          val configured = loggingStore.getAllConfigured()
          val yaml = LoggingConfig.toYaml(configured)
          ("text/yaml; charset=utf-8", yaml)
        }
      }

  private val apiGetLoggingYamlEndpoint: ServerEndpoint[Any, IO] =
    security.authorizedEndpoint(Permission.Debug)
      .get
      .in("api" / "debug" / "logging" / "yaml")
      .out(header[String]("Content-Type").and(stringBody))
      .serverLogicSuccess { _ => _ =>
        IO.blocking {
          val configured = loggingStore.getAllConfigured()
          val yaml = LoggingConfig.toYaml(configured)
          ("text/yaml; charset=utf-8", yaml)
        }
      }

  override val endpoints: List[ServerEndpoint[Any, IO]] =
    List(
      getLoggingEndpoint,
      postLoggingEndpoint,
      getLoggingYamlPageEndpoint,
      getLoggingYamlFileEndpoint,
      apiGetLoggingEndpoint,
      apiSetLevelEndpoint,
      apiGetLoggingYamlEndpoint
    )
