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
import ics205.auth.{AuthConfig, AuthenticatedUser, AuthenticationService, Permission}
import ics205.store.Ics205Store
import jakarta.inject.{Inject, Singleton}
import sttp.model.StatusCode
import sttp.tapir.*
import sttp.tapir.server.ServerEndpoint

@Singleton
class IndexEndpoints @Inject() (
  val store: Ics205Store,
  authService: AuthenticationService,
  config: AuthConfig
) extends ApiEndpoints:

  def this(store: Ics205Store) = this(
    store,
    new AuthenticationService(
      new ics205.store.UserStore(new ics205.util.FileHelper),
      new ics205.auth.ScalaPassPasswordService,
      new ics205.store.InMemJsonSessionStore(new ics205.util.FileHelper)
    ),
    ics205.auth.AuthConfig()
  )

  def index(): String = Ics205Page.render(store.ics205())

  private val indexEndpoint: ServerEndpoint[Any, IO] = endpoint.get
    .in("")
    .in(cookie[Option[String]](config.cookieName))
    .in(query[Option[String]]("saved"))
    .out(statusCode.and(header[Option[String]]("Location")).and(htmlBodyUtf8))
    .serverLogicSuccess[IO] { (sessionIdOpt, saved) =>
      IO.blocking {
        sessionIdOpt.flatMap(id => authService.authenticateSession(id).toOption) match
          case Some(user) =>
            (StatusCode.Ok, None, Ics205Editor.render(store.ics205(), saved = saved.contains("1"), currentUser = Some(user), metadata = Some(store.metadata())))
          case None =>
            (StatusCode.SeeOther, Some("/login"), "")
      }
    }

  private val saveEndpoint: ServerEndpoint[Any, IO] = endpoint.post
    .in("")
    .in(cookie[Option[String]](config.cookieName))
    .in(formBody[Map[String, String]])
    .errorOut(statusCode.and(header[Option[String]]("Location")).and(htmlBodyUtf8))
    .out(statusCode.and(header[String]("Location")))
    .serverLogic[IO] { (sessionIdOpt, data) =>
      IO.blocking {
        sessionIdOpt.flatMap(id => authService.authenticateSession(id).toOption) match
          case None =>
            Left((StatusCode.SeeOther, Some("/login"), ""))
          case Some(user) =>
            val currentMeta = store.metadata()
            if !currentMeta.canEdit(user) then
              val current = store.ics205()
              Left((StatusCode.Forbidden, None, Ics205Editor.render(current, error = Some("You do not have permission to edit plans."), currentUser = Some(user), metadata = Some(currentMeta))))
            else
              val current = store.ics205()
              Ics205Form.decode(data, current) match
                case Left(message) =>
                  Left((StatusCode.UnprocessableEntity, None, Ics205Editor.render(current, Some(data), Some(message), currentUser = Some(user), metadata = Some(currentMeta))))
                case Right(plan) =>
                  try
                    store.save(plan, Some(user.id), refreshPrepared = false)
                    Right((StatusCode.SeeOther, "/?saved=1"))
                  catch
                    case _: java.io.IOException =>
                      Left((StatusCode.InternalServerError, None, Ics205Editor.render(current, Some(data),
                        Some("The plan could not be saved. Check that the data directory is writable and try again."), currentUser = Some(user), metadata = Some(currentMeta))))
      }
    }

  private val previewEndpoint: ServerEndpoint[Any, IO] = endpoint.post
    .in("preview")
    .in(cookie[Option[String]](config.cookieName))
    .in(formBody[Map[String, String]])
    .out(statusCode.and(header[Option[String]]("Location")).and(htmlBodyUtf8))
    .serverLogicSuccess[IO] { (sessionIdOpt, data) =>
      IO.blocking {
        sessionIdOpt.flatMap(id => authService.authenticateSession(id).toOption) match
          case None =>
            (StatusCode.SeeOther, Some("/login"), "")
          case Some(user) =>
            val current = store.ics205()
            Ics205Form.decode(data, current) match
              case Left(message) =>
                (StatusCode.UnprocessableEntity, None, Ics205Editor.render(current, Some(data), Some(message), currentUser = Some(user), metadata = Some(store.metadata())))
              case Right(plan) =>
                (StatusCode.Ok, None, Ics205Page.renderPrintable(plan))
      }
    }

  private val radioEndpoint: ServerEndpoint[Any, IO] = endpoint.get
    .in("radio")
    .in(cookie[Option[String]](config.cookieName))
    .out(statusCode.and(header[Option[String]]("Location")).and(htmlBodyUtf8))
    .serverLogicSuccess[IO] { session =>
      IO.blocking {
        session.flatMap(id => authService.authenticateSession(id).toOption) match
          case Some(_) => (StatusCode.Ok, None, RadioPage.render(store.ics205()))
          case None => (StatusCode.SeeOther, Some("/login"), "")
      }
    }

  override val endpoints: List[ServerEndpoint[Any, IO]] = List(indexEndpoint, saveEndpoint, previewEndpoint, radioEndpoint)
