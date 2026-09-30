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
import cats.effect.unsafe.implicits.global
import ics205.auth.*
import ics205.store.{InMemJsonSessionStore, SessionStore, UserStore}
import ics205.util.FileHelper
import ics205.web.ApiEndpoints
import io.circe.parser.parse
import jakarta.inject.{Inject, Singleton}
import org.http4s.{Header, Headers, Method, Request, Status, Uri}
import org.typelevel.ci.CIString
import sttp.tapir.*
import sttp.tapir.json.circe.*
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.http4s.Http4sServerInterpreter

class AuthEndpointsTests extends munit.FunSuite:
  private def withContext(
    test: (os.Path, UserStore, SessionStore, PasswordService, AuthenticationService, AuthSecurity, AuthEndpoints, org.http4s.HttpApp[IO]) => Unit
  ): Unit =
    val tempDir = os.temp.dir()
    try
      val helper = new FileHelper(tempDir)
      val config = AuthConfig()
      val userStore = new UserStore(helper, config)
      val sessionStore = new InMemJsonSessionStore(helper, config)
      val passwordService = new ScalaPassPasswordService()
      val authService = new AuthenticationService(userStore, passwordService, sessionStore)
      val security = new AuthSecurity(authService, config)
      val authEndpoints = new AuthEndpoints(authService, sessionStore, security, config)
      val testProtectedEndpoints = new TestProtectedEndpoints(security)

      val allServerEndpoints: List[ServerEndpoint[Any, IO]] =
        authEndpoints.endpoints ++ testProtectedEndpoints.endpoints

      val httpApp = Http4sServerInterpreter[IO]().toRoutes(allServerEndpoints).orNotFound
      test(tempDir, userStore, sessionStore, passwordService, authService, security, authEndpoints, httpApp)
    finally
      os.remove.all(tempDir)

  test("GET / without session redirects to /login"):
    withContext { (tempDir, userStore, sessionStore, _, authService, security, authEndpoints, _) =>
      val icsStore = new ics205.store.Ics205Store(new FileHelper(tempDir))
      val indexEndpoints = new ics205.web.IndexEndpoints(icsStore, authService, security.config)
      val app = Http4sServerInterpreter[IO]().toRoutes(authEndpoints.endpoints ++ indexEndpoints.endpoints).orNotFound

      val req = Request[IO](Method.GET, Uri.unsafeFromString("/"))
      val res = app.run(req).unsafeRunSync()
      assertEquals(res.status, Status.SeeOther)
      assertEquals(res.headers.get(CIString("Location")).map(_.head.value), Some("/login"))
    }

  test("GET / with valid session renders editor"):
    withContext { (tempDir, userStore, sessionStore, passwordService, authService, security, authEndpoints, _) =>
      val hash = passwordService.hash("mypassword")
      userStore.add(User("alice", hash, RolePermissions.Admin, enabled = true, id = "u1"))
      val session = sessionStore.create("u1")

      val icsStore = new ics205.store.Ics205Store(new FileHelper(tempDir))
      icsStore.saveEvent(ics205.model.Ics205Event("Main Event", ics205.model.Ics205(incidentName = "Incident Radio Communications Plan", operationalPeriod = ics205.model.OperationalPeriod(), channels = Seq.empty)))
      val indexEndpoints = new ics205.web.IndexEndpoints(icsStore, authService, security.config)
      val app = Http4sServerInterpreter[IO]().toRoutes(authEndpoints.endpoints ++ indexEndpoints.endpoints).orNotFound

      val req = Request[IO](Method.GET, Uri.unsafeFromString("/"))
        .putHeaders(Header.Raw(CIString("Cookie"), s"session=${session.id}"))
      val (res, body) = (for
        r <- app.run(req)
        b <- r.as[String]
      yield (r, b)).unsafeRunSync()

      assertEquals(res.status, Status.Ok)
      assert(body.contains("Incident Radio Communications Plan"))
      assert(body.contains("alice"))
    }

  test("GET /login renders HTML login page with form and messages, without navbar"):
    withContext { (_, _, _, _, _, _, _, app) =>
      val req = Request[IO](Method.GET, Uri.unsafeFromString("/login?msg=Logged+out&err=Invalid+credentials"))
      val (response, body) = (for
        res <- app.run(req)
        b <- res.as[String]
      yield (res, b)).unsafeRunSync()

      assertEquals(response.status, Status.Ok)
      assert(body.contains("<form method=\"post\" action=\"/login\">"))
      assert(body.contains("name=\"username\""))
      assert(body.contains("name=\"password\""))
      assert(body.contains("Logged out"))
      assert(body.contains("Invalid credentials"))
      assert(!body.contains("class=\"navbar"))
      assert(!body.contains("Main navigation"))
    }

  test("POST /login with form data authenticates user, sets session cookie, and redirects to target"):
    withContext { (_, userStore, sessionStore, passwordService, _, _, _, app) =>
      val hash = passwordService.hash("mypassword")
      userStore.add(User("alice", hash, RolePermissions.Admin, enabled = true, id = "u1"))

      val formData = "username=alice&password=mypassword&redirect=%2Fadmin%2Fusers"
      val req = Request[IO](Method.POST, Uri.unsafeFromString("/login"))
        .withEntity(formData)
        .putHeaders(Header.Raw(CIString("Content-Type"), "application/x-www-form-urlencoded"))

      val res = app.run(req).unsafeRunSync()
      assertEquals(res.status, Status.SeeOther)
      assertEquals(res.headers.get(CIString("Location")).map(_.head.value), Some("/admin/users"))

      val cookieHeader = res.headers.get(CIString("Set-Cookie")).map(_.head.value)
      assert(cookieHeader.isDefined)
      val cookieStr = cookieHeader.get
      assert(cookieStr.contains("session="))
      assert(cookieStr.contains("HttpOnly"))
      assert(cookieStr.contains("Path=/"))

      // Session was stored
      val sessionId = cookieStr.split(";").head.replace("session=", "").trim
      assert(sessionStore.find(sessionId).isDefined)
    }

  test("POST /login with invalid form data redirects to /login with error"):
    withContext { (_, userStore, _, passwordService, _, _, _, app) =>
      val hash = passwordService.hash("mypassword")
      userStore.add(User("alice", hash, RolePermissions.Admin, enabled = true, id = "u1"))

      val formData = "username=alice&password=wrong&redirect=%2Fadmin%2Fusers"
      val req = Request[IO](Method.POST, Uri.unsafeFromString("/login"))
        .withEntity(formData)
        .putHeaders(Header.Raw(CIString("Content-Type"), "application/x-www-form-urlencoded"))

      val res = app.run(req).unsafeRunSync()
      assertEquals(res.status, Status.SeeOther)
      val location = res.headers.get(CIString("Location")).map(_.head.value).getOrElse("")
      assert(location.startsWith("/login?err="))
      assert(location.contains("redirect=%2Fadmin%2Fusers"))
      assert(res.headers.get(CIString("Set-Cookie")).isEmpty)
    }

  test("GET /logout clears session, expires cookie, and redirects to /login"):
    withContext { (_, userStore, sessionStore, _, _, _, _, app) =>
      userStore.add(User("alice", "hash", RolePermissions.Admin, enabled = true, id = "u1"))
      val session = sessionStore.create("u1")

      val req = Request[IO](Method.GET, Uri.unsafeFromString("/logout"))
        .putHeaders(Header.Raw(CIString("Cookie"), s"session=${session.id}"))

      val res = app.run(req).unsafeRunSync()
      assertEquals(res.status, Status.SeeOther)
      val location = res.headers.get(CIString("Location")).map(_.head.value).getOrElse("")
      assert(location.startsWith("/login?msg="))
      assertEquals(sessionStore.find(session.id), None)

      val cookieHeader = res.headers.get(CIString("Set-Cookie")).map(_.head.value)
      assert(cookieHeader.isDefined)
      val cookieStr = cookieHeader.get
      assert(cookieStr.contains("Max-Age=0") || cookieStr.contains("Expires="))
    }

  test("successful login returns 200, sets session cookie with proper flags, and returns AuthenticatedUser"):
    withContext { (_, userStore, sessionStore, passwordService, _, _, _, app) =>
      val hash = passwordService.hash("mypassword")
      userStore.add(User("alice", hash, RolePermissions.Admin, enabled = true, id = "u1"))

      val reqBody = """{"username":"alice","password":"mypassword"}"""
      val req = Request[IO](Method.POST, Uri.unsafeFromString("/login"))
        .withEntity(reqBody)
        .putHeaders(Header.Raw(CIString("Content-Type"), "application/json"))

      val (response, body) = (for
        res <- app.run(req)
        b <- res.as[String]
      yield (res, b)).unsafeRunSync()

      assertEquals(response.status, Status.Ok)
      val cookieHeader = response.headers.get(CIString("Set-Cookie")).map(_.head.value)
      assert(cookieHeader.isDefined)
      val cookieStr = cookieHeader.get
      assert(cookieStr.contains("session="))
      assert(cookieStr.contains("HttpOnly"))
      assert(cookieStr.contains("SameSite=Lax"))
      assert(cookieStr.contains("Path=/"))

      val json = parse(body).toOption.get
      assertEquals(json.hcursor.downField("user").downField("username").as[String], Right("alice"))
      assertEquals(json.hcursor.downField("user").downField("role").as[String], Right("admin"))
    }

  test("failed login with bad username or password returns 401 without cookie"):
    withContext { (_, userStore, _, passwordService, _, _, _, app) =>
      val hash = passwordService.hash("mypassword")
      userStore.add(User("alice", hash, RolePermissions.Admin, enabled = true, id = "u1"))

      // Bad password
      val badPassReq = Request[IO](Method.POST, Uri.unsafeFromString("/login"))
        .withEntity("""{"username":"alice","password":"wrongpassword"}""")
        .putHeaders(Header.Raw(CIString("Content-Type"), "application/json"))

      val badPassRes = app.run(badPassReq).unsafeRunSync()
      assertEquals(badPassRes.status, Status.Unauthorized)
      assert(badPassRes.headers.get(CIString("Set-Cookie")).isEmpty)

      // Bad username
      val badUserReq = Request[IO](Method.POST, Uri.unsafeFromString("/login"))
        .withEntity("""{"username":"bob","password":"mypassword"}""")
        .putHeaders(Header.Raw(CIString("Content-Type"), "application/json"))

      val badUserRes = app.run(badUserReq).unsafeRunSync()
      assertEquals(badUserRes.status, Status.Unauthorized)
    }

  test("disabled user login returns 401"):
    withContext { (_, userStore, _, passwordService, _, _, _, app) =>
      val hash = passwordService.hash("mypassword")
      userStore.add(User("alice", hash, RolePermissions.Admin, enabled = false, id = "u1"))

      val req = Request[IO](Method.POST, Uri.unsafeFromString("/login"))
        .withEntity("""{"username":"alice","password":"mypassword"}""")
        .putHeaders(Header.Raw(CIString("Content-Type"), "application/json"))

      val res = app.run(req).unsafeRunSync()
      assertEquals(res.status, Status.Unauthorized)
    }

  test("logout invalidates session and clears session cookie"):
    withContext { (_, userStore, sessionStore, _, _, _, _, app) =>
      userStore.add(User("alice", "hash", RolePermissions.Admin, enabled = true, id = "u1"))
      val session = sessionStore.create("u1")

      val req = Request[IO](Method.POST, Uri.unsafeFromString("/logout"))
        .putHeaders(Header.Raw(CIString("Cookie"), s"session=${session.id}"))

      val res = app.run(req).unsafeRunSync()
      assertEquals(res.status, Status.Ok)

      // In-memory and persisted session should be removed
      assertEquals(sessionStore.find(session.id), None)

      // Expired cookie header returned
      val cookieHeader = res.headers.get(CIString("Set-Cookie")).map(_.head.value)
      assert(cookieHeader.isDefined)
      val cookieStr = cookieHeader.get
      assert(cookieStr.contains("Max-Age=0") || cookieStr.contains("Expires="))
    }

  test("protected endpoint requires valid session cookie"):
    withContext { (_, userStore, sessionStore, _, _, _, _, app) =>
      userStore.add(User("alice", "hash", RolePermissions.Admin, enabled = true, id = "u1"))
      val session = sessionStore.create("u1")

      // No cookie -> 401
      val noCookieRes = app.run(Request[IO](Method.GET, Uri.unsafeFromString("/protected"))).unsafeRunSync()
      assertEquals(noCookieRes.status, Status.Unauthorized)

      // Invalid cookie -> 401
      val invalidCookieRes = app.run(
        Request[IO](Method.GET, Uri.unsafeFromString("/protected"))
          .putHeaders(Header.Raw(CIString("Cookie"), "session=invalid-session-token"))
      ).unsafeRunSync()
      assertEquals(invalidCookieRes.status, Status.Unauthorized)

      // Valid cookie -> 200
      val validRes = app.run(
        Request[IO](Method.GET, Uri.unsafeFromString("/protected"))
          .putHeaders(Header.Raw(CIString("Cookie"), s"session=${session.id}"))
      ).unsafeRunSync()
      assertEquals(validRes.status, Status.Ok)
      assertEquals(validRes.as[String].unsafeRunSync(), "alice:admin")
    }

  test("permission-guarded endpoints allow authorized roles and return 403 Forbidden for unauthorized roles"):
    withContext { (_, userStore, sessionStore, _, _, _, _, app) =>
      userStore.add(User("adminUser", "hash", RolePermissions.Admin, enabled = true, id = "u-admin"))
      userStore.add(User("editorUser", "hash", RolePermissions.Editor, enabled = true, id = "u-editor"))

      val adminSession = sessionStore.create("u-admin")
      val editorSession = sessionStore.create("u-editor")

      // Admin has Debug permission
      val adminRes = app.run(
        Request[IO](Method.GET, Uri.unsafeFromString("/admin-only"))
          .putHeaders(Header.Raw(CIString("Cookie"), s"session=${adminSession.id}"))
      ).unsafeRunSync()
      assertEquals(adminRes.status, Status.Ok)

      // Editor does not have Debug permission -> 403 Forbidden
      val editorRes = app.run(
        Request[IO](Method.GET, Uri.unsafeFromString("/admin-only"))
          .putHeaders(Header.Raw(CIString("Cookie"), s"session=${editorSession.id}"))
      ).unsafeRunSync()
      assertEquals(editorRes.status, Status.Forbidden)
    }

  test("role changes in UserStore take effect immediately for existing session"):
    withContext { (_, userStore, sessionStore, _, _, _, _, app) =>
      val user = User("bob", "hash", RolePermissions.Viewer, enabled = true, id = "u-user")
      userStore.add(user)
      val session = sessionStore.create("u-user")

      // Viewer cannot access admin endpoint
      val res1 = app.run(
        Request[IO](Method.GET, Uri.unsafeFromString("/admin-only"))
          .putHeaders(Header.Raw(CIString("Cookie"), s"session=${session.id}"))
      ).unsafeRunSync()
      assertEquals(res1.status, Status.Forbidden)

      // Promote bob to admin in UserStore
      userStore.update(user.copy(role = RolePermissions.Admin))

      // Same session now succeeds
      val res2 = app.run(
        Request[IO](Method.GET, Uri.unsafeFromString("/admin-only"))
          .putHeaders(Header.Raw(CIString("Cookie"), s"session=${session.id}"))
      ).unsafeRunSync()
      assertEquals(res2.status, Status.Ok)
    }

  test("disabling user in UserStore invalidates active session immediately"):
    withContext { (_, userStore, sessionStore, _, _, _, _, app) =>
      val user = User("bob", "hash", RolePermissions.Admin, enabled = true, id = "u-user")
      userStore.add(user)
      val session = sessionStore.create("u-user")

      val res1 = app.run(
        Request[IO](Method.GET, Uri.unsafeFromString("/protected"))
          .putHeaders(Header.Raw(CIString("Cookie"), s"session=${session.id}"))
      ).unsafeRunSync()
      assertEquals(res1.status, Status.Ok)

      // Disable bob in UserStore
      userStore.update(user.copy(enabled = false))

      // Same session now fails with 401 Unauthorized
      val res2 = app.run(
        Request[IO](Method.GET, Uri.unsafeFromString("/protected"))
          .putHeaders(Header.Raw(CIString("Cookie"), s"session=${session.id}"))
      ).unsafeRunSync()
      assertEquals(res2.status, Status.Unauthorized)
    }

class TestProtectedEndpoints @Inject()(security: AuthSecurity) extends ApiEndpoints:
  private val protectedEndpoint =
    security.secureEndpoint
      .get
      .in("protected")
      .out(stringBody)
      .serverLogicSuccess(user => _ => IO.pure(s"${user.username}:${user.roles.mkString(",")}"))

  private val adminOnlyEndpoint =
    security.authorizedEndpoint(Permission.Debug)
      .get
      .in("admin-only")
      .out(stringBody)
      .serverLogicSuccess(user => _ => IO.pure(s"Welcome admin ${user.username}"))

  override val endpoints: List[ServerEndpoint[Any, IO]] = List(protectedEndpoint, adminOnlyEndpoint)
