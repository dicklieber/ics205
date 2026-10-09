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

package ics205.web.admin

import cats.effect.IO
import com.typesafe.scalalogging.LazyLogging
import ics205.auth.{AuthenticatedUser, AuthenticationService, PasswordService, Permission, Role, User}
import ics205.model.GroupHelper
import ics205.store.{Ics205Store, SessionStore, UserStore}
import ics205.web.ApiEndpoints
import ics205.web.auth.AuthSecurity
import ics205.web.util.RequestUtils
import jakarta.inject.{Inject, Singleton}
import sttp.model.StatusCode
import sttp.tapir.*
import sttp.tapir.server.ServerEndpoint

import java.net.URLEncoder
import java.nio.charset.StandardCharsets

@Singleton
class UserAdminEndpoints @Inject()(
  userStore: UserStore,
  sessionStore: SessionStore,
  passwordService: PasswordService,
  security: AuthSecurity,
  store: Ics205Store
) extends ApiEndpoints with LazyLogging:

  private def urlEncode(s: String): String =
    URLEncoder.encode(s, StandardCharsets.UTF_8)

  private def parseUserGroups(formData: Map[String, String], role: Role): Set[String] =
    if role == Role.Admin then Set.empty
    else
      val selectedGroups = formData.collect {
        case (k, v) if k.startsWith("group_") && (v == "true" || v == "1" || v == "on") =>
          k.stripPrefix("group_")
        case (k, v) if k == "groups" && v.nonEmpty =>
          v
      }.toSet
      val newGroupOpt = formData.get("newGroup").map(GroupHelper.formatGroupName).filter(_.nonEmpty)
      (selectedGroups ++ newGroupOpt).map(GroupHelper.formatGroupName).filter(_.nonEmpty)

  private val viewUsersEndpoint: ServerEndpoint[Any, IO] =
    endpoint.get
      .in("admin" / "users")
      .in(cookie[Option[String]](security.config.cookieName))
      .in(query[Option[String]]("edit"))
      .in(query[Option[String]]("msg"))
      .in(query[Option[String]]("err"))
      .errorOut(statusCode.and(stringBody))
      .out(htmlBodyUtf8)
      .serverLogic[IO] { (sessionIdOpt, editId, msg, err) =>
        IO.blocking {
          val users = userStore.all()
          val allEvents = store.listEvents()
          val knownGroups = GroupHelper.knownGroups(allEvents, users)
          if users.isEmpty then
            Right(
              UserAdminPage.render(
                currentUser = None,
                users = users,
                editingUserId = editId,
                message = msg,
                error = err,
                knownGroups = knownGroups
              )
            )
          else
            sessionIdOpt match
              case Some(sessionId) =>
                val authResult = for
                  user <- security.authService.authenticateSession(sessionId)
                  _ <- ics205.auth.AuthorizationService.authorize(user, Permission.EditUsers)
                yield user
                authResult match
                  case Right(user) =>
                    Right(
                      UserAdminPage.render(
                        currentUser = Some(user),
                        users = users,
                        editingUserId = editId,
                        message = msg,
                        error = err,
                        knownGroups = knownGroups
                      )
                    )
                  case Left(e) =>
                    Left((StatusCode(e.status), e.message))
              case None =>
                Left((StatusCode.Unauthorized, "Authentication required: missing session cookie"))
        }
      }

  private val createUserEndpoint: ServerEndpoint[Any, IO] =
    endpoint.post
      .in("admin" / "users" / "create")
      .in(extractFromRequest(identity))
      .in(cookie[Option[String]](security.config.cookieName))
      .in(formBody[Map[String, String]])
      .errorOut(statusCode.and(stringBody))
      .out(statusCode.and(header[String]("Location")))
      .serverLogic[IO] { (serverRequest, sessionIdOpt, formData) =>
        IO.blocking {
          val ip = RequestUtils.clientIp(serverRequest)
          val users = userStore.all()
          val isInitial = users.isEmpty

          val authorized: Either[(StatusCode, String), Option[AuthenticatedUser]] =
            if isInitial then Right(None)
            else
              sessionIdOpt match
                case Some(sessionId) =>
                  val authResult = for
                    user <- security.authService.authenticateSession(sessionId)
                    _ <- ics205.auth.AuthorizationService.authorize(user, Permission.EditUsers)
                  yield user
                  authResult.left.map(e => (StatusCode(e.status), e.message)).map(Some(_))
                case None =>
                  Left((StatusCode.Unauthorized, "Authentication required: missing session cookie"))

          authorized match
            case Left(err) => Left(err)
            case Right(adminOpt) =>
              val username = formData.getOrElse("username", "").trim
              val password = formData.getOrElse("password", "")
              val confirmPassword = formData.getOrElse("confirmPassword", formData.getOrElse("confirm_password", ""))
              val defaultRole = if isInitial then "admin" else "user"
              val roleInput = formData.getOrElse("role", formData.getOrElse("roles", defaultRole)).trim
              val role = Role.fromString(roleInput).getOrElse(if isInitial then Role.Admin else Role.User)
              val enabled = formData.get("enabled").contains("true") || isInitial
              val userGroups = parseUserGroups(formData, role)

              if username.isEmpty then
                Right((StatusCode.SeeOther, s"/admin/users?err=${urlEncode("Username cannot be empty.")}"))
              else if password.isEmpty then
                Right((StatusCode.SeeOther, s"/admin/users?err=${urlEncode("Password cannot be empty.")}"))
              else if password.length < 8 then
                Right((StatusCode.SeeOther, s"/admin/users?err=${urlEncode("Password must be at least 8 characters.")}"))
              else if password != confirmPassword then
                Right((StatusCode.SeeOther, s"/admin/users?err=${urlEncode("Passwords do not match.")}"))
              else
                val passwordHash = passwordService.hash(password)
                val newUser = User(
                  username = username,
                  passwordHash = passwordHash,
                  role = role,
                  enabled = enabled,
                  groups = userGroups
                )
                userStore.add(newUser) match
                  case Right(_) =>
                    if isInitial then
                      logger.info(s"Initial admin user '$username' (id: '${newUser.id}', role: ${newUser.role}, enabled: ${newUser.enabled}) created from IP $ip")
                      Right((StatusCode.SeeOther, s"/login?msg=${urlEncode(s"User '$username' created successfully. Please log in.")}"))
                    else
                      val adminName = adminOpt.map(_.user).getOrElse("unknown")
                      logger.info(s"Admin '$adminName' created user '$username' (id: '${newUser.id}', role: ${newUser.role}, groups: [${userGroups.mkString(",")}], enabled: ${newUser.enabled}) from IP $ip")
                      Right((StatusCode.SeeOther, s"/admin/users?msg=${urlEncode(s"User '$username' created successfully.")}"))
                  case Left(err) =>
                    Right((StatusCode.SeeOther, s"/admin/users?err=${urlEncode(err)}"))
        }
      }

  private val editUserEndpoint: ServerEndpoint[Any, IO] =
    security.authorizedEndpoint(Permission.EditUsers)
      .post
      .in("admin" / "users" / "edit")
      .in(extractFromRequest(identity))
      .in(formBody[Map[String, String]])
      .out(statusCode.and(header[String]("Location")))
      .serverLogicSuccess { adminUser => (serverRequest, formData) =>
        IO.blocking {
          val ip = RequestUtils.clientIp(serverRequest)
          val id = formData.getOrElse("id", "")
          val username = formData.getOrElse("username", "").trim
          val password = formData.getOrElse("password", "")
          val confirmPassword = formData.getOrElse("confirmPassword", formData.getOrElse("confirm_password", ""))
          val roleInput = formData.getOrElse("role", formData.getOrElse("roles", "user")).trim
          val role = Role.fromString(roleInput).getOrElse(Role.User)
          val enabled = formData.get("enabled").contains("true")
          val userGroups = parseUserGroups(formData, role)

          if id.isEmpty then
            (StatusCode.SeeOther, s"/admin/users?err=${urlEncode("User ID is missing.")}")
          else if username.isEmpty then
            (StatusCode.SeeOther, s"/admin/users?edit=${urlEncode(id)}&err=${urlEncode("Username cannot be empty.")}")
          else if password.nonEmpty && password.length < 8 then
            (StatusCode.SeeOther, s"/admin/users?edit=${urlEncode(id)}&err=${urlEncode("Password must be at least 8 characters.")}")
          else if (password.nonEmpty || confirmPassword.nonEmpty) && password != confirmPassword then
            (StatusCode.SeeOther, s"/admin/users?edit=${urlEncode(id)}&err=${urlEncode("Passwords do not match.")}")
          else
            userStore.findById(id) match
              case None =>
                (StatusCode.SeeOther, s"/admin/users?err=${urlEncode("User not found.")}")
              case Some(existing) =>
                val passwordChanged = password.nonEmpty
                val passwordHash = if passwordChanged then passwordService.hash(password) else existing.passwordHash
                val updated = existing.copy(
                  username = username,
                  passwordHash = passwordHash,
                  role = role,
                  enabled = enabled,
                  groups = userGroups
                )
                val changedFields = List(
                  if existing.username != username then Some(s"username: '${existing.username}' -> '$username'") else None,
                  if existing.role != role then Some(s"role: ${existing.role} -> $role") else None,
                  if existing.enabled != enabled then Some(s"enabled: ${existing.enabled} -> $enabled") else None,
                  if existing.groups != userGroups then Some(s"groups: [${existing.groups.mkString(",")}] -> [${userGroups.mkString(",")}]") else None,
                  if passwordChanged then Some("password: changed") else None
                ).flatten

                userStore.save(updated) match
                  case Right(_) =>
                    val changesSummary = if changedFields.nonEmpty then changedFields.mkString(", ") else "none"
                    logger.info(s"Admin '${adminUser.user}' updated user '${existing.username}' (id: '$id') from IP $ip - changed fields: [$changesSummary]")
                    if password.nonEmpty || existing.role != role || !enabled then
                      sessionStore.deleteAllForUser(existing.id)
                    (StatusCode.SeeOther, s"/admin/users?msg=${urlEncode(s"User '$username' updated successfully.")}")
                  case Left(err) =>
                    (StatusCode.SeeOther, s"/admin/users?edit=${urlEncode(id)}&err=${urlEncode(err)}")
        }
      }

  private val deleteUserEndpoint: ServerEndpoint[Any, IO] =
    security.authorizedEndpoint(Permission.EditUsers)
      .post
      .in("admin" / "users" / "delete")
      .in(extractFromRequest(identity))
      .in(formBody[Map[String, String]])
      .out(statusCode.and(header[String]("Location")))
      .serverLogicSuccess { adminUser => (serverRequest, formData) =>
        IO.blocking {
          val ip = RequestUtils.clientIp(serverRequest)
          val id = formData.getOrElse("id", "")
          if id.nonEmpty then
            val userOpt = userStore.findById(id)
            sessionStore.deleteAllForUser(id)
            userStore.delete(id)
            val username = userOpt.map(_.username).getOrElse(id)
            logger.info(s"Admin '${adminUser.user}' deleted user '$username' (id: '$id') from IP $ip")
            (StatusCode.SeeOther, s"/admin/users?msg=${urlEncode("User deleted successfully.")}")
          else
            (StatusCode.SeeOther, s"/admin/users?err=${urlEncode("User ID is missing.")}")
        }
      }

  override val endpoints: List[ServerEndpoint[Any, IO]] =
    List(
      viewUsersEndpoint,
      createUserEndpoint,
      editUserEndpoint,
      deleteUserEndpoint
    )
