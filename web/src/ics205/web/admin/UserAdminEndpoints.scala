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
    if role.hasPermission(Permission.AllGroup) then Set.empty
    else
      val selectedGroups = formData.collect {
        case (k, v) if k.startsWith("group_") && (v == "true" || v == "1" || v == "on") =>
          k.stripPrefix("group_")
        case (k, v) if k == "groups" && v.nonEmpty =>
          v
      }.toSet
      val newGroupOpt = formData.get("newGroup").map(GroupHelper.formatGroupName).filter(_.nonEmpty)
      (selectedGroups ++ newGroupOpt).map(GroupHelper.formatGroupName).filter(_.nonEmpty)

  private val setupCookieName = "ics205-setup"

  private def setupPage(error: Option[String] = None): String =
    import scalatags.Text.all.*
    doctype("html")(html(lang := "en")(
      head(scalatags.Text.tags2.title("ICS-205 Initial Setup"), link(rel := "stylesheet", href := "/css/admin.css")),
      body(div(cls := "admin-container")(
        h1("Initial administrator setup"),
        p("Enter the setup token printed to the application console when the server started."),
        error.map(message => div(cls := "alert alert-error")(message)),
        form(method := "post", action := "/admin/users/setup")(
          label(attr("for") := "bootstrapToken")("Setup token"),
          input(tpe := "password", id := "bootstrapToken", name := "bootstrapToken", required, autocomplete := "off"),
          button(tpe := "submit", cls := "btn btn-primary")("Continue")
        )
      ))
    )).render

  private val setupEndpoint: ServerEndpoint[Any, IO] =
    endpoint.post.in("admin" / "users" / "setup")
      .in(formBody[Map[String, String]])
      .out(statusCode.and(header[String]("Location")).and(setCookies))
      .serverLogicSuccess[IO] { data =>
        IO.blocking {
          val token = data.getOrElse("bootstrapToken", "")
          if userStore.canBootstrap(token) then
            val cookie = sttp.model.headers.CookieWithMeta.unsafeApply(
              name = setupCookieName,
              value = token,
              path = Some("/admin/users"),
              maxAge = Some(600L),
              expires = Some(java.time.Instant.now().plusSeconds(600)),
              secure = security.config.secureCookie,
              httpOnly = true,
              sameSite = Some(sttp.model.headers.Cookie.SameSite.Lax)
            )
            (StatusCode.SeeOther, "/admin/users", List(cookie))
          else
            (StatusCode.SeeOther, "/admin/users?err=Invalid+or+expired+setup+token", Nil)
        }
      }

  private val viewUsersEndpoint: ServerEndpoint[Any, IO] =
    endpoint.get
      .in("admin" / "users")
      .in(cookie[Option[String]](security.config.cookieName))
      .in(cookie[Option[String]](setupCookieName))
      .in(query[Option[String]]("edit"))
      .in(query[Option[String]]("msg"))
      .in(query[Option[String]]("err"))
      .errorOut(statusCode.and(stringBody))
      .out(htmlBodyUtf8)
      .serverLogic[IO] { (sessionIdOpt, setupTokenOpt, editId, msg, err) =>
        IO.blocking {
          val users = userStore.all()
          val allEvents = store.listEvents()
          val knownGroups = GroupHelper.knownGroups(allEvents, users)
          if users.isEmpty && !setupTokenOpt.exists(userStore.canBootstrap) then
            Right(setupPage(err))
          else if users.isEmpty then
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

  private val saveUserEndpoint: ServerEndpoint[Any, IO] =
    endpoint.post
      .in("admin" / "users")
      .in(extractFromRequest(identity))
      .in(cookie[Option[String]](security.config.cookieName))
      .in(cookie[Option[String]](setupCookieName))
      .in(formBody[Map[String, String]])
      .errorOut(statusCode.and(stringBody))
      .out(statusCode.and(header[Option[String]]("Location")).and(htmlBodyUtf8))
      .out(header("Cache-Control", "no-store"))
      .serverLogic[IO] { (serverRequest, sessionIdOpt, setupTokenOpt, formData) =>
        IO.blocking {
          val ip = RequestUtils.clientIp(serverRequest)
          val editingId =
            if formData.get("operation").contains("edit") || formData.contains("id") then Some(formData.getOrElse("id", ""))
            else None
          val isInitial = userStore.all().isEmpty
          val setupToken = setupTokenOpt.getOrElse(formData.getOrElse("bootstrapToken", ""))
          val authorized: Either[(StatusCode, String), Option[AuthenticatedUser]] =
            if isInitial then
              if editingId.isEmpty && userStore.canBootstrap(setupToken) then Right(None)
              else Left((StatusCode.Forbidden, "Initial setup requires the valid token printed to the application console."))
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
              val roleInput = formData.getOrElse("role", formData.getOrElse("roles", "viewer")).trim
              val role = if isInitial then Role.Admin else Role.values.find(_.toString.equalsIgnoreCase(roleInput)).getOrElse(Role.Viewer)
              val enabled = isInitial || formData.get("enabled").contains("true")
              val userGroups = parseUserGroups(formData, role)
              val existing = editingId.flatMap(userStore.findById)

              def failed(message: String, status: StatusCode = StatusCode.UnprocessableEntity): (StatusCode, Option[String], String) =
                val users = userStore.all()
                (status, None, UserAdminPage.render(
                  currentUser = adminOpt,
                  users = users,
                  editingUserId = editingId,
                  error = Some(message),
                  knownGroups = GroupHelper.knownGroups(store.listEvents(), users),
                  submitted = Some(formData -- Seq("password", "confirmPassword", "confirm_password", "bootstrapToken"))
                ))

              val validationError =
                if editingId.contains("") then Some("User ID is missing.")
                else if editingId.isDefined && existing.isEmpty then Some("User not found.")
                else if username.isEmpty then Some("Username cannot be empty.")
                else if editingId.isEmpty && password.isEmpty then Some("Password cannot be empty.")
                else if password.nonEmpty && password.length < 8 then Some("Password must be at least 8 characters.")
                else if password != confirmPassword then Some("Passwords do not match.")
                else None

              validationError match
                case Some(message) => Right(failed(message))
                case None =>
                  try
                    val passwordHash = if password.nonEmpty then passwordService.hash(password) else existing.get.passwordHash
                    val user = existing match
                      case Some(original) => original.copy(username = username, passwordHash = passwordHash, role = role, enabled = enabled, groups = userGroups)
                      case None => User(username = username, passwordHash = passwordHash, role = role, enabled = enabled, groups = userGroups)
                    val result =
                      if existing.isDefined then userStore.save(user)
                      else if isInitial then userStore.initializeAdmin(user, setupToken)
                      else userStore.add(user)
                    result match
                      case Left(message) => Right(failed(message))
                      case Right(saved) =>
                        val adminName = adminOpt.map(_.username).getOrElse("unknown")
                        existing match
                          case Some(original) =>
                            val changedFields = List(
                              Option.when(original.username != username)(s"username: '${original.username}' -> '$username'"),
                              Option.when(original.role != role)(s"role: ${original.role} -> $role"),
                              Option.when(original.enabled != enabled)(s"enabled: ${original.enabled} -> $enabled"),
                              Option.when(original.groups != userGroups)(s"groups: [${original.groups.mkString(",")}] -> [${userGroups.mkString(",")}]"),
                              Option.when(password.nonEmpty)("password: changed")
                            ).flatten
                            val changesSummary = if changedFields.nonEmpty then changedFields.mkString(", ") else "none"
                            logger.info(s"Admin '$adminName' updated user '${original.username}' (id: '${saved.id}') from IP $ip - changed fields: [$changesSummary]")
                            if password.nonEmpty || original.role != role || !enabled then sessionStore.deleteAllForUser(saved.id)
                          case None if isInitial =>
                            logger.info(s"Initial admin user '$username' (id: '${saved.id}', role: ${saved.role}, enabled: ${saved.enabled}) created from IP $ip")
                          case None =>
                            logger.info(s"Admin '$adminName' created user '$username' (id: '${saved.id}', role: ${saved.role}, groups: [${userGroups.mkString(",")}], enabled: ${saved.enabled}) from IP $ip")
                        val message = if existing.isDefined then s"User '$username' updated successfully."
                          else s"User '$username' created successfully.${if isInitial then " Please log in." else ""}"
                        val destination = if isInitial then "/login" else "/admin/users"
                        Right((StatusCode.SeeOther, Some(s"$destination?msg=${urlEncode(message)}"), ""))
                  catch
                    case scala.util.control.NonFatal(_) =>
                      Right(failed("The user could not be saved. Please try again.", StatusCode.InternalServerError))
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
            logger.info(s"Admin '${adminUser.username}' deleted user '$username' (id: '$id') from IP $ip")
            (StatusCode.SeeOther, s"/admin/users?msg=${urlEncode("User deleted successfully.")}")
          else
            (StatusCode.SeeOther, s"/admin/users?err=${urlEncode("User ID is missing.")}")
        }
      }

  override val endpoints: List[ServerEndpoint[Any, IO]] =
    List(
      setupEndpoint,
      viewUsersEndpoint,
      saveUserEndpoint,
      deleteUserEndpoint
    )
