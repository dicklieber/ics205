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
import ics205.store.{Ics205Store, SessionStore, UserStore}
import ics205.util.{FileHelper, UtcFormatter}
import ics205.web.auth.AuthSecurity
import jakarta.inject.{Inject, Singleton}
import sttp.model.StatusCode
import sttp.tapir.*
import sttp.tapir.server.ServerEndpoint

@Singleton
class DebugEndpoints @Inject()(
  store: Ics205Store,
  userStore: UserStore,
  sessionStore: SessionStore,
  security: AuthSecurity,
  fileHelper: FileHelper
) extends ApiEndpoints with LazyLogging:

  def this(
    store: Ics205Store,
    userStore: UserStore,
    sessionStore: SessionStore,
    security: AuthSecurity
  ) = this(store, userStore, sessionStore, security, new FileHelper())

  private val reloadFilesGetEndpoint: ServerEndpoint[Any, IO] =
    security.authorizedEndpoint(Permission.Debug)
      .get
      .in("debug" / "reload-files")
      .in(query[Option[String]]("returnUrl"))
      .in(header[Option[String]]("Referer"))
      .out(statusCode.and(header[Option[String]]("Location")).and(stringBody))
      .serverLogicSuccess { currentUser => (returnUrlOpt, refererOpt) =>
        IO.blocking {
          logger.info(s"User '${currentUser.user}' requested reloading all files.")
          store.reload()
          userStore.reload()
          sessionStore.reload()
          val target = returnUrlOpt.orElse(refererOpt).getOrElse("/")
          (StatusCode.SeeOther, Some(target), "Files reloaded successfully.")
        }
      }

  private val reloadFilesPostEndpoint: ServerEndpoint[Any, IO] =
    security.authorizedEndpoint(Permission.Debug)
      .post
      .in("debug" / "reload-files")
      .in(query[Option[String]]("returnUrl"))
      .in(header[Option[String]]("Referer"))
      .out(statusCode.and(header[Option[String]]("Location")).and(stringBody))
      .serverLogicSuccess { currentUser => (returnUrlOpt, refererOpt) =>
        IO.blocking {
          logger.info(s"User '${currentUser.user}' requested reloading all files via POST.")
          store.reload()
          userStore.reload()
          sessionStore.reload()
          val target = returnUrlOpt.orElse(refererOpt).getOrElse("/")
          (StatusCode.SeeOther, Some(target), "Files reloaded successfully.")
        }
      }

  private val downloadDirectoryZipEndpoint: ServerEndpoint[Any, IO] =
    security.authorizedEndpoint(Permission.Debug)
      .get
      .in("debug" / "download-directory")
      .out(statusCode
        .and(header[String]("Content-Type"))
        .and(header[Option[String]]("Content-Disposition"))
        .and(header[String]("Cache-Control"))
        .and(byteArrayBody))
      .serverLogicSuccess { currentUser => _ =>
        IO.blocking {
          logger.info(s"User '${currentUser.user}' requested downloading zip of FileHelper.directory (${fileHelper.directory}).")
          val zipBytes = fileHelper.zipDirectory()
          val zipFileName = s"ics205-${UtcFormatter.format()}.zip"
          (
            StatusCode.Ok,
            "application/zip",
            Some(s"""attachment; filename="$zipFileName""""),
            "no-store",
            zipBytes
          )
        }
      }

  private val downloadDataZipEndpoint: ServerEndpoint[Any, IO] =
    security.authorizedEndpoint(Permission.Debug)
      .get
      .in("debug" / "download-data")
      .out(statusCode
        .and(header[String]("Content-Type"))
        .and(header[Option[String]]("Content-Disposition"))
        .and(header[String]("Cache-Control"))
        .and(byteArrayBody))
      .serverLogicSuccess { currentUser => _ =>
        IO.blocking {
          logger.info(s"User '${currentUser.user}' requested downloading zip of FileHelper.directory (${fileHelper.directory}).")
          val zipBytes = fileHelper.zipDirectory()
          val zipFileName = s"ics205-${UtcFormatter.format()}.zip"
          (
            StatusCode.Ok,
            "application/zip",
            Some(s"""attachment; filename="$zipFileName""""),
            "no-store",
            zipBytes
          )
        }
      }

  override val endpoints: List[ServerEndpoint[Any, IO]] =
    List(
      reloadFilesGetEndpoint,
      reloadFilesPostEndpoint,
      downloadDirectoryZipEndpoint,
      downloadDataZipEndpoint
    )
