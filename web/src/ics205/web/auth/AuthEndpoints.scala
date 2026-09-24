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
import io.circe.syntax.*
import jakarta.inject.{Inject, Singleton}
import sttp.model.StatusCode
import sttp.tapir.*
import sttp.tapir.generic.auto.*
import sttp.tapir.json.circe.*
import sttp.tapir.server.ServerEndpoint

import java.net.{URLDecoder, URLEncoder}
import java.nio.charset.StandardCharsets

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

  private def urlEncode(s: String): String =
    URLEncoder.encode(s, StandardCharsets.UTF_8)

  private def parseFormData(body: String): Map[String, String] =
    if body.trim.isEmpty then Map.empty
    else
      body.split("&").flatMap { pair =>
        pair.split("=", 2) match
          case Array(k, v) =>
            Some(URLDecoder.decode(k, StandardCharsets.UTF_8) -> URLDecoder.decode(v, StandardCharsets.UTF_8))
          case Array(k) =>
            Some(URLDecoder.decode(k, StandardCharsets.UTF_8) -> "")
          case _ => None
      }.toMap

  private val loginPageEndpoint: ServerEndpoint[Any, IO] =
    endpoint.get
      .in("login")
      .in(query[Option[String]]("redirect"))
      .in(query[Option[String]]("msg"))
      .in(query[Option[String]]("err"))
      .out(htmlBodyUtf8)
      .serverLogicSuccess[IO] { (redirect, msg, err) =>
        IO(LoginPage.render(message = msg, error = err, redirect = redirect))
      }

  private val loginPostEndpoint: ServerEndpoint[Any, IO] =
    endpoint.post
      .in("login")
      .in(header[Option[String]]("Content-Type"))
      .in(stringBody)
      .errorOut(statusCode.and(header[Option[String]]("Location")).and(header[Option[String]]("Content-Type")).and(stringBody))
      .out(statusCode.and(header[Option[String]]("Location")).and(setCookies).and(header[Option[String]]("Content-Type")).and(stringBody))
      .serverLogic[IO] { (contentType, body) =>
        IO.blocking {
          val isJson = contentType.exists(_.toLowerCase.contains("application/json"))
          if isJson then
            io.circe.parser.decode[LoginRequest](body) match
              case Left(_) =>
                Left((StatusCode.BadRequest, None, Some("text/plain; charset=utf-8"), "Invalid JSON body"))
              case Right(req) =>
                authService.authenticate(req.username, req.password) match
                  case Some(session) =>
                    val cookieMeta = security.sessionCookieWithMeta(session.id)
                    authService.authenticateSession(session.id) match
                      case Right(user) =>
                        val respJson = LoginResponse("Login successful", user).asJson.noSpaces
                        Right((StatusCode.Ok, None, List(cookieMeta), Some("application/json"), respJson))
                      case Left(err) =>
                        Left((StatusCode(err.status), None, Some("text/plain; charset=utf-8"), err.message))
                  case None =>
                    Left((StatusCode.Unauthorized, None, Some("text/plain; charset=utf-8"), "Invalid username or password"))
          else
            val formData = parseFormData(body)
            val username = formData.getOrElse("username", "").trim
            val password = formData.getOrElse("password", "")
            val redirect = formData.get("redirect").filter(r => r.startsWith("/") && !r.startsWith("//"))

            authService.authenticate(username, password) match
              case Some(session) =>
                val cookieMeta = security.sessionCookieWithMeta(session.id)
                val target = redirect.getOrElse("/")
                Right((StatusCode.SeeOther, Some(target), List(cookieMeta), None, ""))
              case None =>
                val redirectParam = redirect.map(r => s"&redirect=${urlEncode(r)}").getOrElse("")
                val target = s"/login?err=${urlEncode("Invalid username or password")}$redirectParam"
                Left((StatusCode.SeeOther, Some(target), None, ""))
        }
      }

  private val logoutGetEndpoint: ServerEndpoint[Any, IO] =
    endpoint.get
      .in("logout")
      .in(cookie[Option[String]](config.cookieName))
      .out(statusCode.and(setCookie(config.cookieName)).and(header[String]("Location")))
      .serverLogicSuccess[IO] { maybeSessionId =>
        IO.blocking {
          maybeSessionId.foreach(sessionStore.delete)
          val cookieMeta = security.expiredCookieMeta()
          (StatusCode.SeeOther, cookieMeta, s"/login?msg=${urlEncode("Logged out successfully.")}")
        }
      }

  private val logoutPostEndpoint: ServerEndpoint[Any, IO] =
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

  override val endpoints: List[ServerEndpoint[Any, IO]] = List(
    loginPageEndpoint,
    loginPostEndpoint,
    logoutGetEndpoint,
    logoutPostEndpoint
  )
