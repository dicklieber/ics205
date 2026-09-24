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

package ics205.web.auth

import cats.effect.IO
import ics205.auth.{AuthConfig, AuthenticatedUser, AuthenticationService}
import ics205.store.SessionStore
import ics205.web.ApiEndpoints
import io.circe.Codec
import io.circe.derivation.{Configuration, ConfiguredCodec}
import jakarta.inject.{Inject, Singleton}
import sttp.model.StatusCode
import sttp.tapir.*
import sttp.tapir.generic.auto.*
import sttp.tapir.json.circe.*
import sttp.tapir.server.ServerEndpoint

case class LoginRequest(
  username: String,
  password: String
)

object LoginRequest:
  private given Configuration = Configuration.default.withDefaults
  given Codec.AsObject[LoginRequest] = ConfiguredCodec.derived[LoginRequest]

case class LoginResponse(
  message: String,
  user: AuthenticatedUser
)

object LoginResponse:
  private given Configuration = Configuration.default.withDefaults
  given Codec.AsObject[LoginResponse] = ConfiguredCodec.derived[LoginResponse]

case class LogoutResponse(
  message: String
)

object LogoutResponse:
  private given Configuration = Configuration.default.withDefaults
  given Codec.AsObject[LogoutResponse] = ConfiguredCodec.derived[LogoutResponse]

@Singleton
class AuthEndpoints @Inject()(
  authService: AuthenticationService,
  sessionStore: SessionStore,
  security: AuthSecurity,
  config: AuthConfig
) extends ApiEndpoints:

  private val loginEndpoint: ServerEndpoint[Any, IO] =
    endpoint.post
      .in("login")
      .in(jsonBody[LoginRequest])
      .errorOut(statusCode.and(stringBody))
      .out(setCookie(config.cookieName))
      .out(jsonBody[LoginResponse])
      .serverLogic[IO] { req =>
        IO {
          authService.authenticate(req.username, req.password) match
            case Some(session) =>
              val cookieMeta = security.sessionCookieMeta(session.id)
              authService.authenticateSession(session.id) match
                case Right(user) =>
                  Right((cookieMeta, LoginResponse("Login successful", user)))
                case Left(err) =>
                  Left((StatusCode(err.status), err.message))
            case None =>
              Left((StatusCode.Unauthorized, "Invalid username or password"))
        }
      }

  private val logoutEndpoint: ServerEndpoint[Any, IO] =
    endpoint.post
      .in("logout")
      .in(cookie[Option[String]](config.cookieName))
      .out(setCookie(config.cookieName))
      .out(jsonBody[LogoutResponse])
      .serverLogicSuccess[IO] { maybeSessionId =>
        IO {
          maybeSessionId.foreach(sessionStore.delete)
          val cookieMeta = security.expiredCookieMeta()
          (cookieMeta, LogoutResponse("Logged out successfully"))
        }
      }

  override val endpoints: List[ServerEndpoint[Any, IO]] = List(loginEndpoint, logoutEndpoint)
