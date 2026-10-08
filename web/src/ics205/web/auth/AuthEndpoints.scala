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
import com.typesafe.scalalogging.LazyLogging
import ics205.auth.{AuthConfig, AuthenticatedUser, AuthenticationService, PasswordService, ScalaPassPasswordService}
import ics205.store.{SessionStore, UserStore}
import ics205.web.ApiEndpoints
import ics205.web.util.RequestUtils
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

case class ChangePasswordRequest(
  currentPassword: String,
  newPassword: String,
  confirmPassword: Option[String] = None
)

object ChangePasswordRequest:
  private given Configuration = Configuration.default.withDefaults
  given Codec.AsObject[ChangePasswordRequest] = ConfiguredCodec.derived[ChangePasswordRequest]

case class ChangePasswordResponse(
  message: String
)

object ChangePasswordResponse:
  private given Configuration = Configuration.default.withDefaults
  given Codec.AsObject[ChangePasswordResponse] = ConfiguredCodec.derived[ChangePasswordResponse]

@Singleton
class AuthEndpoints @Inject()(
  authService: AuthenticationService,
  sessionStore: SessionStore,
  security: AuthSecurity,
  config: AuthConfig,
  userStore: UserStore,
  passwordService: PasswordService
) extends ApiEndpoints with LazyLogging:

  def this(
    authService: AuthenticationService,
    sessionStore: SessionStore,
    security: AuthSecurity,
    config: AuthConfig,
    userStore: UserStore
  ) = this(authService, sessionStore, security, config, userStore, new ScalaPassPasswordService())

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
      .out(statusCode.and(header[Option[String]]("Location")).and(htmlBodyUtf8))
      .serverLogicSuccess[IO] { (redirect, msg, err) =>
        IO.blocking {
          if userStore.all().isEmpty then
            val message = "An initial admin user must be created."
            logger.error(s"No users detected. Redirecting to user manager: $message")
            (StatusCode.SeeOther, Some(s"/admin/users?msg=${urlEncode(message)}"), "")
          else
            (StatusCode.Ok, None, LoginPage.render(message = msg, error = err, redirect = redirect))
        }
      }

  private val loginPostEndpoint: ServerEndpoint[Any, IO] =
    endpoint.post
      .in("login")
      .in(extractFromRequest(identity))
      .in(header[Option[String]]("Content-Type"))
      .in(stringBody)
      .errorOut(statusCode.and(header[Option[String]]("Location")).and(header[Option[String]]("Content-Type")).and(stringBody))
      .out(statusCode.and(header[Option[String]]("Location")).and(setCookies).and(header[Option[String]]("Content-Type")).and(stringBody))
      .serverLogic[IO] { (serverRequest, contentType, body) =>
        IO.blocking {
          val ip = RequestUtils.clientIp(serverRequest)
          val userAgent = serverRequest.header("User-Agent").getOrElse("unknown")
          val isJson = contentType.exists(_.toLowerCase.contains("application/json"))
          if isJson then
            io.circe.parser.decode[LoginRequest](body) match
              case Left(err) =>
                logger.info(s"Login failed from IP $ip (User-Agent: $userAgent): Invalid JSON body - ${err.getMessage}")
                Left((StatusCode.BadRequest, None, Some("text/plain; charset=utf-8"), "Invalid JSON body"))
              case Right(req) =>
                val username = req.username.trim
                userStore.findByUsername(username) match
                  case None =>
                    logger.info(s"Login failed for user '$username' from IP $ip (User-Agent: $userAgent): User does not exist")
                    Left((StatusCode.Unauthorized, None, Some("text/plain; charset=utf-8"), "Invalid username or password"))
                  case Some(user) if !user.enabled =>
                    logger.info(s"Login failed for user '$username' (id: '${user.id}') from IP $ip (User-Agent: $userAgent): User is disabled")
                    Left((StatusCode.Unauthorized, None, Some("text/plain; charset=utf-8"), "Invalid username or password"))
                  case Some(user) if !passwordService.verify(req.password, user.passwordHash) =>
                    logger.info(s"Login failed for user '$username' (id: '${user.id}') from IP $ip (User-Agent: $userAgent): Incorrect password")
                    Left((StatusCode.Unauthorized, None, Some("text/plain; charset=utf-8"), "Invalid username or password"))
                  case Some(user) =>
                    val session = sessionStore.create(user.id)
                    val cookieMeta = security.sessionCookieWithMeta(session.id)
                    authService.authenticateSession(session.id) match
                      case Right(authUser) =>
                        logger.info(s"Login successful for user '${authUser.user}' (id: '${authUser.id}', role: ${authUser.session}) from IP $ip (User-Agent: $userAgent)")
                        val respJson = LoginResponse("Login successful", authUser).asJson.noSpaces
                        Right((StatusCode.Ok, None, List(cookieMeta), Some("application/json"), respJson))
                      case Left(err) =>
                        logger.info(s"Login failed for user '$username' from IP $ip (User-Agent: $userAgent): ${err.message}")
                        Left((StatusCode(err.status), None, Some("text/plain; charset=utf-8"), err.message))
          else
            val formData = parseFormData(body)
            val username = formData.getOrElse("username", "").trim
            val password = formData.getOrElse("password", "")
            val redirect = formData.get("redirect").flatMap(RequestUtils.localRedirect)
            val redirectInfo = redirect.map(r => s", redirect: '$r'").getOrElse("")

            if username.isEmpty || password.isEmpty then
              logger.info(s"Login failed from IP $ip (User-Agent: $userAgent$redirectInfo): Empty username or password provided (username: '$username')")
              val redirectParam = redirect.map(r => s"&redirect=${urlEncode(r)}").getOrElse("")
              val target = s"/login?err=${urlEncode("Invalid username or password")}$redirectParam"
              Left((StatusCode.SeeOther, Some(target), None, ""))
            else
              userStore.findByUsername(username) match
                case None =>
                  logger.info(s"Login failed for user '$username' from IP $ip (User-Agent: $userAgent$redirectInfo): User does not exist")
                  val redirectParam = redirect.map(r => s"&redirect=${urlEncode(r)}").getOrElse("")
                  val target = s"/login?err=${urlEncode("Invalid username or password")}$redirectParam"
                  Left((StatusCode.SeeOther, Some(target), None, ""))
                case Some(user) if !user.enabled =>
                  logger.info(s"Login failed for user '$username' (id: '${user.id}') from IP $ip (User-Agent: $userAgent$redirectInfo): User is disabled")
                  val redirectParam = redirect.map(r => s"&redirect=${urlEncode(r)}").getOrElse("")
                  val target = s"/login?err=${urlEncode("Invalid username or password")}$redirectParam"
                  Left((StatusCode.SeeOther, Some(target), None, ""))
                case Some(user) if !passwordService.verify(password, user.passwordHash) =>
                  logger.info(s"Login failed for user '$username' (id: '${user.id}') from IP $ip (User-Agent: $userAgent$redirectInfo): Incorrect password")
                  val redirectParam = redirect.map(r => s"&redirect=${urlEncode(r)}").getOrElse("")
                  val target = s"/login?err=${urlEncode("Invalid username or password")}$redirectParam"
                  Left((StatusCode.SeeOther, Some(target), None, ""))
                case Some(user) =>
                  val session = sessionStore.create(user.id)
                  val cookieMeta = security.sessionCookieWithMeta(session.id)
                  val target = redirect.getOrElse("/")
                  logger.info(s"Login successful for user '${user.username}' (id: '${user.id}', role: ${user.role}) from IP $ip (User-Agent: $userAgent$redirectInfo)")
                  Right((StatusCode.SeeOther, Some(target), List(cookieMeta), None, ""))
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

  private val changePasswordGetEndpoint: ServerEndpoint[Any, IO] =
    endpoint.get
      .in("change-password")
      .in(cookie[Option[String]](config.cookieName))
      .in(query[Option[String]]("msg"))
      .in(query[Option[String]]("err"))
      .out(statusCode.and(header[Option[String]]("Location")).and(htmlBodyUtf8))
      .serverLogicSuccess[IO] { (sessionIdOpt, msg, err) =>
        IO.blocking {
          sessionIdOpt match
            case Some(sessionId) =>
              authService.authenticateSession(sessionId) match
                case Right(user) =>
                  (StatusCode.Ok, None, ChangePasswordPage.render(currentUser = Some(user), message = msg, error = err))
                case Left(_) =>
                  (StatusCode.SeeOther, Some(s"/login?redirect=${urlEncode("/change-password")}"), "")
            case None =>
              (StatusCode.SeeOther, Some(s"/login?redirect=${urlEncode("/change-password")}"), "")
        }
      }

  private val changePasswordPostEndpoint: ServerEndpoint[Any, IO] =
    endpoint.post
      .in("change-password")
      .in(extractFromRequest(identity))
      .in(cookie[Option[String]](config.cookieName))
      .in(header[Option[String]]("Content-Type"))
      .in(stringBody)
      .errorOut(statusCode.and(header[Option[String]]("Location")).and(header[Option[String]]("Content-Type")).and(stringBody))
      .out(statusCode.and(header[Option[String]]("Location")).and(header[Option[String]]("Content-Type")).and(stringBody))
      .serverLogic[IO] { (serverRequest, sessionIdOpt, contentType, body) =>
        IO.blocking {
          val ip = RequestUtils.clientIp(serverRequest)
          val isJson = contentType.exists(_.toLowerCase.contains("application/json"))
          sessionIdOpt match
            case Some(sessionId) =>
              authService.authenticateSession(sessionId) match
                case Right(authUser) =>
                  userStore.findById(authUser.id) match
                    case Some(storedUser) =>
                      if isJson then
                        io.circe.parser.decode[ChangePasswordRequest](body) match
                          case Left(_) =>
                            Left((StatusCode.BadRequest, None, Some("text/plain; charset=utf-8"), "Invalid JSON body"))
                          case Right(req) =>
                            val currentPassword = req.currentPassword
                            val newPassword = req.newPassword
                            val confirmPassword = req.confirmPassword.getOrElse(newPassword)
                            if currentPassword.isEmpty then
                              Left((StatusCode.BadRequest, None, Some("text/plain; charset=utf-8"), "Current password cannot be empty."))
                            else if newPassword.isEmpty then
                              Left((StatusCode.BadRequest, None, Some("text/plain; charset=utf-8"), "Password cannot be empty."))
                            else if newPassword.length < 8 then
                              Left((StatusCode.BadRequest, None, Some("text/plain; charset=utf-8"), "Password must be at least 8 characters."))
                            else if req.confirmPassword.isDefined && newPassword != confirmPassword then
                              Left((StatusCode.BadRequest, None, Some("text/plain; charset=utf-8"), "Passwords do not match."))
                            else if !passwordService.verify(currentPassword, storedUser.passwordHash) then
                              Left((StatusCode.BadRequest, None, Some("text/plain; charset=utf-8"), "Current password is incorrect."))
                            else
                              val newHash = passwordService.hash(newPassword)
                              userStore.save(storedUser.copy(passwordHash = newHash))
                              logger.info(s"User '${authUser.user}' (id: '${authUser.id}') changed their password from IP $ip")
                              val respJson = ChangePasswordResponse("Password changed successfully.").asJson.noSpaces
                              Right((StatusCode.Ok, None, Some("application/json"), respJson))
                      else
                        val formData = parseFormData(body)
                        val currentPassword = formData.getOrElse("currentPassword", formData.getOrElse("current_password", formData.getOrElse("oldPassword", "")))
                        val newPassword = formData.getOrElse("newPassword", formData.getOrElse("new_password", formData.getOrElse("password", "")))
                        val confirmPassword = formData.getOrElse("confirmPassword", formData.getOrElse("confirm_password", ""))

                        if currentPassword.isEmpty then
                          Left((StatusCode.SeeOther, Some(s"/change-password?err=${urlEncode("Current password cannot be empty.")}"), None, ""))
                        else if newPassword.isEmpty then
                          Left((StatusCode.SeeOther, Some(s"/change-password?err=${urlEncode("Password cannot be empty.")}"), None, ""))
                        else if newPassword.length < 8 then
                          Left((StatusCode.SeeOther, Some(s"/change-password?err=${urlEncode("Password must be at least 8 characters.")}"), None, ""))
                        else if newPassword != confirmPassword then
                          Left((StatusCode.SeeOther, Some(s"/change-password?err=${urlEncode("Passwords do not match.")}"), None, ""))
                        else if !passwordService.verify(currentPassword, storedUser.passwordHash) then
                          Left((StatusCode.SeeOther, Some(s"/change-password?err=${urlEncode("Current password is incorrect.")}"), None, ""))
                        else
                          val newHash = passwordService.hash(newPassword)
                          userStore.save(storedUser.copy(passwordHash = newHash))
                          logger.info(s"User '${authUser.user}' (id: '${authUser.id}') changed their password from IP $ip")
                          Right((StatusCode.SeeOther, Some(s"/change-password?msg=${urlEncode("Password changed successfully.")}"), None, ""))
                    case None =>
                      if isJson then
                        Left((StatusCode.Unauthorized, None, Some("text/plain; charset=utf-8"), "User not found or disabled"))
                      else
                        Left((StatusCode.SeeOther, Some(s"/login?redirect=${urlEncode("/change-password")}"), None, ""))
                case Left(err) =>
                  if isJson then
                    Left((StatusCode(err.status), None, Some("text/plain; charset=utf-8"), err.message))
                  else
                    Left((StatusCode.SeeOther, Some(s"/login?redirect=${urlEncode("/change-password")}"), None, ""))
            case None =>
              if isJson then
                Left((StatusCode.Unauthorized, None, Some("text/plain; charset=utf-8"), "Authentication required: missing session cookie"))
              else
                Left((StatusCode.SeeOther, Some(s"/login?redirect=${urlEncode("/change-password")}"), None, ""))
        }
      }

  private val passwordAliasGetEndpoint: ServerEndpoint[Any, IO] =
    endpoint.get
      .in("password")
      .in(query[Option[String]]("msg"))
      .in(query[Option[String]]("err"))
      .out(statusCode.and(header[String]("Location")))
      .serverLogicSuccess[IO] { (msg, err) =>
        IO.pure {
          val params = List(
            msg.map(m => s"msg=${urlEncode(m)}"),
            err.map(e => s"err=${urlEncode(e)}")
          ).flatten.mkString("&")
          val query = if params.nonEmpty then s"?$params" else ""
          (StatusCode.SeeOther, s"/change-password$query")
        }
      }

  override val endpoints: List[ServerEndpoint[Any, IO]] = List(
    loginPageEndpoint,
    loginPostEndpoint,
    logoutGetEndpoint,
    logoutPostEndpoint,
    changePasswordGetEndpoint,
    changePasswordPostEndpoint,
    passwordAliasGetEndpoint
  )
