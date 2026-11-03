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
import org.http4s.{Header, Headers, Method, Request, Status, Uri, UrlForm}
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
      val authEndpoints = new AuthEndpoints(authService, sessionStore, security, config, userStore)
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
      userStore.add(User("alice", hash, Role.Admin, enabled = true, id = "u1"))
      val session = sessionStore.create("u1")

      val icsStore = new ics205.store.Ics205Store(new FileHelper(tempDir))
      icsStore.save(ics205.model.Ics205Plan("Main Event", ics205.model.Ics205(incidentName = "Incident Radio Communications Plan", operationalPeriod = ics205.model.OperationalPeriod(), channels = Seq.empty)))
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

  test("GET /login when no users defined redirects to /admin/users with message"):
    withContext { (_, _, _, _, _, _, _, app) =>
      val req = Request[IO](Method.GET, Uri.unsafeFromString("/login"))
      val res = app.run(req).unsafeRunSync()
      assertEquals(res.status, Status.SeeOther)
      val location = res.headers.get(CIString("Location")).map(_.head.value)
      assert(location.isDefined)
      assert(location.get.startsWith("/admin/users?msg="))
      assert(location.get.contains("initial+admin+user"))
    }

  test("GET /login renders HTML login page with form and messages, without navbar"):
    withContext { (_, userStore, _, passwordService, _, _, _, app) =>
      val hash = passwordService.hash("mypassword")
      userStore.add(User("admin", hash, Role.Admin, enabled = true, id = "u-admin"))

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
    }

  test("POST /login with form data authenticates user, sets session cookie, and redirects to target"):
    withContext { (_, userStore, sessionStore, passwordService, _, _, _, app) =>
      val hash = passwordService.hash("mypassword")
      userStore.add(User("alice", hash, Role.Admin, enabled = true, id = "u1"))

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
      userStore.add(User("alice", hash, Role.Admin, enabled = true, id = "u1"))

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
      userStore.add(User("alice", "hash", Role.Admin, enabled = true, id = "u1"))
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

  test("successful login returns public identity and keeps password hash and session token out of JSON"):
    withContext { (_, userStore, sessionStore, passwordService, _, _, _, app) =>
      val hash = passwordService.hash("mypassword")
      userStore.add(User("alice", hash, Role.Admin, enabled = true, id = "u1"))

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
      assert(json.hcursor.downField("user").downField("role").as[String] == Right("Admin"))
      assertEquals(json.hcursor.downField("user").downField("id").as[String], Right("u1"))
      assert(!body.contains("passwordHash"))
      assert(!body.contains(userStore.findByUsername("alice").get.passwordHash))
      assert(!body.contains(sessionStore.all().head.id))
      assert(!body.contains("session"))
    }

  test("failed login with bad username or password returns 401 without cookie"):
    withContext { (_, userStore, _, passwordService, _, _, _, app) =>
      val hash = passwordService.hash("mypassword")
      userStore.add(User("alice", hash, Role.Admin, enabled = true, id = "u1"))

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
      userStore.add(User("alice", hash, Role.Admin, enabled = false, id = "u1"))

      val req = Request[IO](Method.POST, Uri.unsafeFromString("/login"))
        .withEntity("""{"username":"alice","password":"mypassword"}""")
        .putHeaders(Header.Raw(CIString("Content-Type"), "application/json"))

      val res = app.run(req).unsafeRunSync()
      assertEquals(res.status, Status.Unauthorized)
    }

  test("logout invalidates session and clears session cookie"):
    withContext { (_, userStore, sessionStore, _, _, _, _, app) =>
      userStore.add(User("alice", "hash", Role.Admin, enabled = true, id = "u1"))
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
      userStore.add(User("alice", "hash", Role.Admin, enabled = true, id = "u1"))
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
      userStore.add(User("adminUser", "hash", Role.Admin, enabled = true, id = "u-admin"))
      userStore.add(User("editorUser", "hash", Role.Editor, enabled = true, id = "u-editor"))

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
      val user = User("bob", "hash", Role.Viewer, enabled = true, id = "u-user")
      userStore.add(user)
      val session = sessionStore.create("u-user")

      // Viewer cannot access admin endpoint
      val res1 = app.run(
        Request[IO](Method.GET, Uri.unsafeFromString("/admin-only"))
          .putHeaders(Header.Raw(CIString("Cookie"), s"session=${session.id}"))
      ).unsafeRunSync()
      assertEquals(res1.status, Status.Forbidden)

      // Promote bob to admin in UserStore
      userStore.save(user.copy(role = Role.Admin))

      // Same session now succeeds
      val res2 = app.run(
        Request[IO](Method.GET, Uri.unsafeFromString("/admin-only"))
          .putHeaders(Header.Raw(CIString("Cookie"), s"session=${session.id}"))
      ).unsafeRunSync()
      assertEquals(res2.status, Status.Ok)
    }

  test("disabling user in UserStore invalidates active session immediately"):
    withContext { (_, userStore, sessionStore, _, _, _, _, app) =>
      val user = User("bob", "hash", Role.Admin, enabled = true, id = "u-user")
      userStore.add(user)
      val session = sessionStore.create("u-user")

      val res1 = app.run(
        Request[IO](Method.GET, Uri.unsafeFromString("/protected"))
          .putHeaders(Header.Raw(CIString("Cookie"), s"session=${session.id}"))
      ).unsafeRunSync()
      assertEquals(res1.status, Status.Ok)

      // Disable bob in UserStore
      userStore.save(user.copy(enabled = false))

      // Same session now fails with 401 Unauthorized
      val res2 = app.run(
        Request[IO](Method.GET, Uri.unsafeFromString("/protected"))
          .putHeaders(Header.Raw(CIString("Cookie"), s"session=${session.id}"))
      ).unsafeRunSync()
      assertEquals(res2.status, Status.Unauthorized)
    }

  test("GET /change-password without session redirects to /login"):
    withContext { (_, _, _, _, _, _, _, app) =>
      val req = Request[IO](Method.GET, Uri.unsafeFromString("/change-password"))
      val res = app.run(req).unsafeRunSync()
      assertEquals(res.status, Status.SeeOther)
      val location = res.headers.get(CIString("Location")).map(_.head.value)
      assertEquals(location, Some("/login?redirect=%2Fchange-password"))
    }

  test("GET /password redirects to /change-password"):
    withContext { (_, _, _, _, _, _, _, app) =>
      val req = Request[IO](Method.GET, Uri.unsafeFromString("/password?msg=hello"))
      val res = app.run(req).unsafeRunSync()
      assertEquals(res.status, Status.SeeOther)
      val location = res.headers.get(CIString("Location")).map(_.head.value)
      assertEquals(location, Some("/change-password?msg=hello"))
    }

  test("GET /change-password with valid session renders change password page"):
    withContext { (_, userStore, sessionStore, passwordService, _, _, _, app) =>
      val hash = passwordService.hash("mypassword")
      userStore.add(User("alice", hash, Role.Viewer, enabled = true, id = "u1"))
      val session = sessionStore.create("u1")

      val req = Request[IO](Method.GET, Uri.unsafeFromString("/change-password"))
        .putHeaders(Header.Raw(CIString("Cookie"), s"session=${session.id}"))
      val (res, body) = (for
        r <- app.run(req)
        b <- r.as[String]
      yield (r, b)).unsafeRunSync()

      assertEquals(res.status, Status.Ok)
      assert(body.contains("Change Password"))
      assert(body.contains("id=\"currentPassword\""))
      assert(body.contains("id=\"newPassword\""))
      assert(body.contains("id=\"confirmPassword\""))
      assert(body.contains("Logged in as:"))
      assert(body.contains("alice"))
    }

  test("POST /change-password validates current password, minimum length, and password match"):
    withContext { (_, userStore, sessionStore, passwordService, authService, _, _, app) =>
      val hash = passwordService.hash("correctOldPass123")
      userStore.add(User("bob", hash, Role.Viewer, enabled = true, id = "u-bob"))
      val session = sessionStore.create("u-bob")

      // Empty current password
      val formEmptyCurrent = UrlForm("currentPassword" -> "", "newPassword" -> "newPass12345", "confirmPassword" -> "newPass12345")
      val res1 = app.run(
        Request[IO](Method.POST, Uri.unsafeFromString("/change-password"))
          .withEntity(formEmptyCurrent)
          .putHeaders(Header.Raw(CIString("Cookie"), s"session=${session.id}"))
      ).unsafeRunSync()
      assertEquals(res1.status, Status.SeeOther)
      assert(res1.headers.get(CIString("Location")).get.head.value.contains("err=Current+password+cannot+be+empty."))

      // Empty new password
      val formEmptyNew = UrlForm("currentPassword" -> "correctOldPass123", "newPassword" -> "", "confirmPassword" -> "")
      val res2 = app.run(
        Request[IO](Method.POST, Uri.unsafeFromString("/change-password"))
          .withEntity(formEmptyNew)
          .putHeaders(Header.Raw(CIString("Cookie"), s"session=${session.id}"))
      ).unsafeRunSync()
      assertEquals(res2.status, Status.SeeOther)
      assert(res2.headers.get(CIString("Location")).get.head.value.contains("err=Password+cannot+be+empty."))

      // Short new password (< 8 chars)
      val formShortNew = UrlForm("currentPassword" -> "correctOldPass123", "newPassword" -> "short", "confirmPassword" -> "short")
      val res3 = app.run(
        Request[IO](Method.POST, Uri.unsafeFromString("/change-password"))
          .withEntity(formShortNew)
          .putHeaders(Header.Raw(CIString("Cookie"), s"session=${session.id}"))
      ).unsafeRunSync()
      assertEquals(res3.status, Status.SeeOther)
      assert(res3.headers.get(CIString("Location")).get.head.value.contains("err=Password+must+be+at+least+8+characters."))

      // Password mismatch
      val formMismatch = UrlForm("currentPassword" -> "correctOldPass123", "newPassword" -> "brandNewPass1", "confirmPassword" -> "brandNewPass2")
      val res4 = app.run(
        Request[IO](Method.POST, Uri.unsafeFromString("/change-password"))
          .withEntity(formMismatch)
          .putHeaders(Header.Raw(CIString("Cookie"), s"session=${session.id}"))
      ).unsafeRunSync()
      assertEquals(res4.status, Status.SeeOther)
      assert(res4.headers.get(CIString("Location")).get.head.value.contains("err=Passwords+do+not+match."))

      // Wrong current password
      val formWrongCurrent = UrlForm("currentPassword" -> "wrongOldPass123", "newPassword" -> "brandNewPass123", "confirmPassword" -> "brandNewPass123")
      val res5 = app.run(
        Request[IO](Method.POST, Uri.unsafeFromString("/change-password"))
          .withEntity(formWrongCurrent)
          .putHeaders(Header.Raw(CIString("Cookie"), s"session=${session.id}"))
      ).unsafeRunSync()
      assertEquals(res5.status, Status.SeeOther)
      assert(res5.headers.get(CIString("Location")).get.head.value.contains("err=Current+password+is+incorrect."))

      // Successful password change
      val formSuccess = UrlForm("currentPassword" -> "correctOldPass123", "newPassword" -> "brandNewPass123", "confirmPassword" -> "brandNewPass123")
      val res6 = app.run(
        Request[IO](Method.POST, Uri.unsafeFromString("/change-password"))
          .withEntity(formSuccess)
          .putHeaders(Header.Raw(CIString("Cookie"), s"session=${session.id}"))
      ).unsafeRunSync()
      assertEquals(res6.status, Status.SeeOther)
      assert(res6.headers.get(CIString("Location")).get.head.value.startsWith("/login?msg=Password+changed+successfully."))

      // Old password no longer authenticates, new password authenticates
      assert(authService.authenticate("bob", "correctOldPass123").isEmpty)
      val newAuth = authService.authenticate("bob", "brandNewPass123")
      assert(newAuth.isDefined)
    }

  test("POST /change-password via JSON handles authentication, validation, and updates password"):
    withContext { (_, userStore, sessionStore, passwordService, authService, _, _, app) =>
      val hash = passwordService.hash("mysecretpass")
      userStore.add(User("carol", hash, Role.Viewer, enabled = true, id = "u-carol"))
      val session = sessionStore.create("u-carol")

      // Unauthorized without session
      val resNoAuth = app.run(
        Request[IO](Method.POST, Uri.unsafeFromString("/change-password"))
          .withEntity("""{"currentPassword":"mysecretpass","newPassword":"newsecretpass123"}""")
          .putHeaders(Header.Raw(CIString("Content-Type"), "application/json"))
      ).unsafeRunSync()
      assertEquals(resNoAuth.status, Status.Unauthorized)

      // Invalid current password
      val resBadOld = app.run(
        Request[IO](Method.POST, Uri.unsafeFromString("/change-password"))
          .withEntity("""{"currentPassword":"wrongpass","newPassword":"newsecretpass123"}""")
          .putHeaders(
            Header.Raw(CIString("Content-Type"), "application/json"),
            Header.Raw(CIString("Cookie"), s"session=${session.id}")
          )
      ).unsafeRunSync()
      assertEquals(resBadOld.status, Status.BadRequest)

      // Mismatched confirm password
      val resMismatch = app.run(
        Request[IO](Method.POST, Uri.unsafeFromString("/change-password"))
          .withEntity("""{"currentPassword":"mysecretpass","newPassword":"newsecretpass123","confirmPassword":"differentpass"}""")
          .putHeaders(
            Header.Raw(CIString("Content-Type"), "application/json"),
            Header.Raw(CIString("Cookie"), s"session=${session.id}")
          )
      ).unsafeRunSync()
      assertEquals(resMismatch.status, Status.BadRequest)

      // Success
      val resSuccess = app.run(
        Request[IO](Method.POST, Uri.unsafeFromString("/change-password"))
          .withEntity("""{"currentPassword":"mysecretpass","newPassword":"newsecretpass123","confirmPassword":"newsecretpass123"}""")
          .putHeaders(
            Header.Raw(CIString("Content-Type"), "application/json"),
            Header.Raw(CIString("Cookie"), s"session=${session.id}")
          )
      ).unsafeRunSync()
      assertEquals(resSuccess.status, Status.Ok)

      assert(authService.authenticate("carol", "mysecretpass").isEmpty)
      assert(authService.authenticate("carol", "newsecretpass123").isDefined)
    }

  test("password changes revoke every user session for form and JSON requests, including persisted sessions"):
    for isJson <- Seq(false, true) do
      withContext { (directory, userStore, sessions, passwords, auth, _, _, app) =>
        val hash = passwords.hash("old-password123")
        userStore.add(User("alice", hash, Role.Viewer, id = "alice-id"))
        userStore.add(User("bob", hash, Role.Viewer, id = "bob-id"))
        val current = sessions.create("alice-id")
        val otherDevice = sessions.create("alice-id")
        val unrelated = sessions.create("bob-id")
        def submit(currentPassword: String) =
          val request = Request[IO](Method.POST, Uri.unsafeFromString("/change-password"))
          val encoded = if isJson then
            request.withEntity(io.circe.Json.obj(
              "currentPassword" -> io.circe.Json.fromString(currentPassword),
              "newPassword" -> io.circe.Json.fromString("new-password123"),
              "confirmPassword" -> io.circe.Json.fromString("new-password123")
            ).noSpaces).putHeaders(Header.Raw(CIString("Content-Type"), "application/json"))
          else request.withEntity(UrlForm("currentPassword" -> currentPassword, "newPassword" -> "new-password123", "confirmPassword" -> "new-password123"))
          app.run(encoded.putHeaders(Header.Raw(CIString("Cookie"), s"session=${current.id}"))).unsafeRunSync()

        val rejected = submit("wrong-password")
        assertEquals(rejected.status, if isJson then Status.BadRequest else Status.SeeOther)
        assert(auth.authenticateSession(current.id).isRight)
        assert(auth.authenticateSession(otherDevice.id).isRight)
        assert(rejected.headers.get(CIString("Set-Cookie")).isEmpty)

        val response = submit("old-password123")
        assertEquals(response.status, if isJson then Status.Ok else Status.SeeOther)
        val cookie = response.headers.get(CIString("Set-Cookie")).get.head.value
        assert(cookie.contains("Max-Age=0"))
        assert(cookie.contains("HttpOnly"))
        assertEquals(response.headers.get(CIString("Cache-Control")).map(_.head.value), Some("no-store"))
        if !isJson then assert(response.headers.get(CIString("Location")).get.head.value.startsWith("/login?msg="))
        for oldSession <- Seq(current, otherDevice) do
          assert(auth.authenticateSession(oldSession.id).isLeft)
          val protectedResponse = app.run(Request[IO](Method.GET, Uri.unsafeFromString("/protected"))
            .putHeaders(Header.Raw(CIString("Cookie"), s"session=${oldSession.id}"))).unsafeRunSync()
          assertEquals(protectedResponse.status, Status.Unauthorized)
        assert(auth.authenticateSession(unrelated.id).isRight)
        val restarted = new InMemJsonSessionStore(new FileHelper(directory), AuthConfig())
        assert(restarted.find(current.id).isEmpty)
        assert(restarted.find(otherDevice.id).isEmpty)
        assert(restarted.find(unrelated.id).isDefined)
        assert(auth.authenticate("alice", "old-password123").isEmpty)
        assert(auth.authenticate("alice", "new-password123").isDefined)
      }

  test("failed password persistence reports failure and leaves sessions and password intact"):
    for isJson <- Seq(false, true) do
      withContext { (directory, userStore, sessions, passwords, auth, _, _, app) =>
        val hash = passwords.hash("old-password123")
        userStore.add(User("alice", hash, Role.Viewer, id = "alice-id"))
        val current = sessions.create("alice-id")
        val otherDevice = sessions.create("alice-id")
        val path = directory / "admin" / "users.json"
        os.remove(path)
        os.makeDir.all(path)
        os.write(path / "block-replacement", "blocked")
        val request = Request[IO](Method.POST, Uri.unsafeFromString("/change-password"))
        val encoded = if isJson then request
          .withEntity("""{"currentPassword":"old-password123","newPassword":"new-password123"}""")
          .putHeaders(Header.Raw(CIString("Content-Type"), "application/json"))
        else request.withEntity(UrlForm("currentPassword" -> "old-password123", "newPassword" -> "new-password123", "confirmPassword" -> "new-password123"))
        val response = app.run(encoded.putHeaders(Header.Raw(CIString("Cookie"), s"session=${current.id}"))).unsafeRunSync()
        assertEquals(response.status, Status.InternalServerError)
        assert(response.headers.get(CIString("Set-Cookie")).isEmpty)
        assertEquals(userStore.findById("alice-id").get.passwordHash, hash)
        assert(auth.authenticateSession(current.id).isRight)
        assert(auth.authenticateSession(otherDevice.id).isRight)
      }

  test("login throttles before password verification across JSON, forms, username aliases, and spoofed IP headers"):
    withContext { (_, userStore, sessionStore, _, auth, security, _, _) =>
      var checks = 0
      val passwords = new PasswordService:
        def hash(password: String): String = "hash"
        def verify(password: String, hash: String): Boolean =
          checks += 1
          false
      userStore.add(User("I-user", "hash", Role.Viewer, id = "limited-user"))
      val endpoints = new AuthEndpoints(auth, sessionStore, security, security.config, userStore, passwords)
      val app = Http4sServerInterpreter[IO]().toRoutes(endpoints.endpoints).orNotFound
      for n <- 1 to 6 do
        val username = if n % 2 == 0 then "ı-user" else "I-user"
        val request = Request[IO](Method.POST, Uri.unsafeFromString("/login"))
        val encoded = if n % 2 == 0 then request
          .withEntity(io.circe.Json.obj("username" -> io.circe.Json.fromString(username), "password" -> io.circe.Json.fromString("wrong")).noSpaces)
          .putHeaders(Header.Raw(CIString("Content-Type"), "application/json"))
        else request.withEntity(UrlForm("username" -> username, "password" -> "wrong"))
        val response = app.run(encoded.putHeaders(Header.Raw(CIString("X-Forwarded-For"), s"192.0.2.$n"))).unsafeRunSync()
        if n <= 5 then assert(response.status != Status.TooManyRequests)
        else
          assertEquals(response.status, Status.TooManyRequests)
          assert(response.headers.get(CIString("Retry-After")).isDefined)
          assert(response.headers.get(CIString("Set-Cookie")).isEmpty)
      assertEquals(checks, 5)
      assertEquals(sessionStore.all().size, 0)
    }

  test("peer login limit prevents rotating usernames and forwarded addresses from bypassing throttling"):
    withContext { (_, _, _, _, _, _, _, app) =>
      for n <- 1 to 31 do
        val response = app.run(Request[IO](Method.POST, Uri.unsafeFromString("/login"))
          .withEntity(UrlForm("username" -> s"unknown$n", "password" -> "wrong"))
          .putHeaders(Header.Raw(CIString("X-Forwarded-For"), s"192.0.2.$n"), Header.Raw(CIString("X-Real-IP"), s"198.51.100.$n"))).unsafeRunSync()
        assertEquals(response.status, if n <= 30 then Status.SeeOther else Status.TooManyRequests)
        if n == 31 then
          assert(response.as[String].unsafeRunSync().contains("Too many login attempts"))
          assert(response.headers.get(CIString("Retry-After")).isDefined)
    }

class TestProtectedEndpoints @Inject()(security: AuthSecurity) extends ApiEndpoints:
  private val protectedEndpoint =
    security.secureEndpoint
      .get
      .in("protected")
      .out(stringBody)
      .serverLogicSuccess(user => _ => IO.pure(s"${user.username}:${user.role.toString.toLowerCase}"))

  private val adminOnlyEndpoint =
    security.authorizedEndpoint(Permission.Debug)
      .get
      .in("admin-only")
      .out(stringBody)
      .serverLogicSuccess(user => _ => IO.pure(s"Welcome admin ${user.user}"))

  override val endpoints: List[ServerEndpoint[Any, IO]] = List(protectedEndpoint, adminOnlyEndpoint)
