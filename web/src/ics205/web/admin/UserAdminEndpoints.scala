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
import ics205.auth.{PasswordService, Permission, RolePermissions, User}
import ics205.store.{SessionStore, UserStore}
import ics205.web.ApiEndpoints
import ics205.web.auth.AuthSecurity
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
  security: AuthSecurity
) extends ApiEndpoints:

  private def urlEncode(s: String): String =
    URLEncoder.encode(s, StandardCharsets.UTF_8)

  private val viewUsersEndpoint: ServerEndpoint[Any, IO] =
    security.authorizedEndpoint(Permission.EditUsers)
      .get
      .in("admin" / "users")
      .in(query[Option[String]]("edit"))
      .in(query[Option[String]]("msg"))
      .in(query[Option[String]]("err"))
      .out(htmlBodyUtf8)
      .serverLogicSuccess { currentUser => (editId, msg, err) =>
        IO {
          val users = userStore.all()
          UserAdminPage.render(
            currentUser = currentUser,
            users = users,
            editingUserId = editId,
            message = msg,
            error = err
          )
        }
      }

  private val createUserEndpoint: ServerEndpoint[Any, IO] =
    security.authorizedEndpoint(Permission.EditUsers)
      .post
      .in("admin" / "users" / "create")
      .in(formBody[Map[String, String]])
      .out(statusCode.and(header[String]("Location")))
      .serverLogicSuccess { _ => formData =>
        IO.blocking {
          val username = formData.getOrElse("username", "").trim
          val password = formData.getOrElse("password", "")
          val roleInput = formData.getOrElse("role", formData.getOrElse("roles", "user")).trim
          val role = RolePermissions.fromString(roleInput).getOrElse(RolePermissions.User)
          val enabled = formData.get("enabled").contains("true")

          if username.isEmpty then
            (StatusCode.SeeOther, s"/admin/users?err=${urlEncode("Username cannot be empty.")}")
          else if password.isEmpty then
            (StatusCode.SeeOther, s"/admin/users?err=${ ("Password cannot be empty.")}")
          else if password.length < 8 then
            (StatusCode.SeeOther, s"/admin/users?err=${urlEncode("Password must be at least 8 characters.")}")
          else
            val passwordHash = passwordService.hash(password)
            val newUser = User(
              username = username,
              passwordHash = passwordHash,
              role = role,
              enabled = enabled
            )
            userStore.add(newUser) match
              case Right(_) =>
                (StatusCode.SeeOther, s"/admin/users?msg=${urlEncode(s"User '$username' created successfully.")}")
              case Left(err) =>
                (StatusCode.SeeOther, s"/admin/users?err=${urlEncode(err)}")
        }
      }

  private val editUserEndpoint: ServerEndpoint[Any, IO] =
    security.authorizedEndpoint(Permission.EditUsers)
      .post
      .in("admin" / "users" / "edit")
      .in(formBody[Map[String, String]])
      .out(statusCode.and(header[String]("Location")))
      .serverLogicSuccess { _ => formData =>
        IO.blocking {
          val id = formData.getOrElse("id", "")
          val username = formData.getOrElse("username", "").trim
          val password = formData.getOrElse("password", "")
          val roleInput = formData.getOrElse("role", formData.getOrElse("roles", "user")).trim
          val role = RolePermissions.fromString(roleInput).getOrElse(RolePermissions.User)
          val enabled = formData.get("enabled").contains("true")

          if id.isEmpty then
            (StatusCode.SeeOther, s"/admin/users?err=${urlEncode("User ID is missing.")}")
          else if username.isEmpty then
            (StatusCode.SeeOther, s"/admin/users?edit=${urlEncode(id)}&err=${urlEncode("Username cannot be empty.")}")
          else if password.nonEmpty && password.length < 8 then
            (StatusCode.SeeOther, s"/admin/users?edit=${urlEncode(id)}&err=${urlEncode("Password must be at least 8 characters.")}")
          else
            userStore.findById(id) match
              case None =>
                (StatusCode.SeeOther, s"/admin/users?err=${urlEncode("User not found.")}")
              case Some(existing) =>
                val passwordHash = if password.nonEmpty then passwordService.hash(password) else existing.passwordHash
                val updated = existing.copy(
                  username = username,
                  passwordHash = passwordHash,
                  role = role,
                  enabled = enabled
                )
                userStore.update(updated) match
                  case Right(_) =>
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
      .in(formBody[Map[String, String]])
      .out(statusCode.and(header[String]("Location")))
      .serverLogicSuccess { _ => formData =>
        IO.blocking {
          val id = formData.getOrElse("id", "")
          if id.nonEmpty then
            sessionStore.deleteAllForUser(id)
            userStore.delete(id)
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
