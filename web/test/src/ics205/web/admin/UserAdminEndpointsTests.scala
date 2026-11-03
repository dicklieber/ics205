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
import cats.effect.unsafe.implicits.global
import ics205.auth.*
import ics205.store.{InMemJsonSessionStore, SessionStore, UserStore}
import ics205.util.FileHelper
import ics205.web.auth.AuthSecurity
import org.http4s.{Header, Headers, Method, Request, Status, Uri, UrlForm}
import org.typelevel.ci.CIString
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.http4s.Http4sServerInterpreter

class UserAdminEndpointsTests extends munit.FunSuite:

  private def withContext(
    test: (os.Path, UserStore, SessionStore, PasswordService, AuthenticationService, AuthSecurity, UserAdminEndpoints, org.http4s.HttpApp[IO]) => Unit
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
      val store = new ics205.store.Ics205Store(helper)
      val adminEndpoints = new UserAdminEndpoints(userStore, sessionStore, passwordService, security, store)

      val allServerEndpoints: List[ServerEndpoint[Any, IO]] = adminEndpoints.endpoints
      val httpApp = Http4sServerInterpreter[IO]().toRoutes(allServerEndpoints).orNotFound
      test(tempDir, userStore, sessionStore, passwordService, authService, security, adminEndpoints, httpApp)
    finally
      os.remove.all(tempDir)

  test("GET /admin/users without session cookie when users exist returns 401 Unauthorized"):
    withContext { (_, userStore, _, passwordService, _, _, _, app) =>
      val hash = passwordService.hash("password")
      userStore.add(User("admin", hash, Role.Admin, enabled = true, id = "u-admin"))
      val req = Request[IO](Method.GET, Uri.unsafeFromString("/admin/users"))
      val res = app.run(req).unsafeRunSync()
      assertEquals(res.status, Status.Unauthorized)
    }

  test("GET /admin/users without session cookie when no users exist returns 200 OK"):
    withContext { (_, _, _, _, _, _, _, app) =>
      val req = Request[IO](Method.GET, Uri.unsafeFromString("/admin/users?msg=An+initial+admin+user+must+be+created."))
      val (res, body) = (for
        r <- app.run(req)
        b <- r.as[String]
      yield (r, b)).unsafeRunSync()
      assertEquals(res.status, Status.Ok)
      assert(body.contains("Initial administrator setup"))
      assert(body.contains("name=\"bootstrapToken\""))
      assert(!body.contains("name=\"username\""))
    }

  test("shared user save enforces setup and administrator authorization for both operations"):
    withContext { (_, userStore, sessionStore, _, _, _, _, app) =>
      def save(fields: Map[String, String], cookie: Option[String] = None) =
        val request = Request[IO](Method.POST, Uri.unsafeFromString("/admin/users"))
          .withEntity(UrlForm(fields.toSeq*))
        app.run(cookie.fold(request)(value => request.putHeaders(Header.Raw(CIString("Cookie"), value)))).unsafeRunSync()
      val token = userStore.initialSetupToken.get
      assertEquals(save(Map("operation" -> "create", "username" -> "attacker")).status, Status.Forbidden)
      assertEquals(save(Map("operation" -> "edit", "id" -> "missing", "bootstrapToken" -> token)).status, Status.Forbidden)
      assertEquals(userStore.all().size, 0)
      val viewer = User("viewer", "hash", Role.Viewer, id = "viewer-id")
      userStore.add(viewer)
      val session = sessionStore.create(viewer.id)
      for operation <- Seq("create", "edit") do
        val fields = Map("operation" -> operation, "username" -> "changed", "bootstrapToken" -> token) ++
          (if operation == "edit" then Map("id" -> viewer.id) else Map.empty[String, String])
        assertEquals(save(fields).status, Status.Unauthorized)
        assertEquals(save(fields, Some(s"session=${session.id}")).status, Status.Forbidden)
      assertEquals(userStore.all(), Seq(viewer))
    }

  test("initial setup validation uses the same populated create form"):
    withContext { (_, userStore, _, _, _, _, _, app) =>
      val token = userStore.initialSetupToken.get
      val response = app.run(Request[IO](Method.POST, Uri.unsafeFromString("/admin/users"))
        .putHeaders(Header.Raw(CIString("Cookie"), s"ics205-setup=$token"))
        .withEntity(UrlForm("operation" -> "create", "username" -> "first-admin", "password" -> "short", "confirmPassword" -> "short", "role" -> "admin"))).unsafeRunSync()
      assertEquals(response.status, Status.UnprocessableEntity)
      val html = response.as[String].unsafeRunSync()
      assert(html.contains("value=\"first-admin\""))
      assert(html.contains("action=\"/admin/users\""))
      assert(html.contains("Password must be at least 8 characters."))
      assert(!html.contains(token))
      assertEquals(userStore.all().size, 0)
    }

  test("POST /admin/users when no users exist creates initial admin user and redirects to /login"):
    withContext { (tempDir, userStore, _, passwordService, _, _, _, app) =>
      assert(!os.exists(tempDir / "admin" / "users.json"))
      val form = UrlForm(
        "bootstrapToken" -> userStore.initialSetupToken.get,
        "username" -> "initialadmin",
        "password" -> "adminpass123",
        "confirmPassword" -> "adminpass123",
        "role" -> "admin"
      )
      val req = Request[IO](Method.POST, Uri.unsafeFromString("/admin/users"))
        .withEntity(form)
      val res = app.run(req).unsafeRunSync()
      assertEquals(res.status, Status.SeeOther)
      val location = res.headers.get(CIString("Location")).map(_.head.value)
      assertEquals(location, Some("/login?msg=User+%27initialadmin%27+created+successfully.+Please+log+in."))

      assert(os.exists(tempDir / "admin" / "users.json"))
      val created = userStore.findByUsername("initialadmin").get
      assertEquals(created.role, Role.Admin)
      assertEquals(created.enabled, true)
      assert(passwordService.verify("adminpass123", created.passwordHash))
    }

  test("setup token gate redirects to admin creation and stops working after setup"):
    withContext { (_, userStore, _, _, _, _, _, app) =>
      val token = userStore.initialSetupToken.get
      val invalid = app.run(Request[IO](Method.POST, Uri.unsafeFromString("/admin/users/setup"))
        .withEntity(UrlForm("bootstrapToken" -> "wrong"))).unsafeRunSync()
      assert(invalid.headers.get(CIString("Set-Cookie")).isEmpty)
      val verified = app.run(Request[IO](Method.POST, Uri.unsafeFromString("/admin/users/setup"))
        .withEntity(UrlForm("bootstrapToken" -> token))).unsafeRunSync()
      assertEquals(verified.status, Status.SeeOther)
      assertEquals(verified.headers.get(CIString("Location")).get.head.value, "/admin/users")
      val setCookie = verified.headers.get(CIString("Set-Cookie")).get.head.value
      assert(setCookie.toLowerCase.contains("httponly"))
      assert(setCookie.toLowerCase.contains("samesite=lax"))
      val cookie = Header.Raw(CIString("Cookie"), setCookie.takeWhile(_ != ';'))
      val page = app.run(Request[IO](Method.GET, Uri.unsafeFromString("/admin/users")).putHeaders(cookie)).unsafeRunSync()
      val body = page.as[String].unsafeRunSync()
      assert(body.contains("name=\"username\""))
      assert(!body.contains(token))
      val created = app.run(Request[IO](Method.POST, Uri.unsafeFromString("/admin/users"))
        .putHeaders(cookie)
        .withEntity(UrlForm("username" -> "first", "password" -> "password123", "confirmPassword" -> "password123"))).unsafeRunSync()
      assertEquals(created.status, Status.SeeOther)
      assertEquals(userStore.all().size, 1)
      val replay = app.run(Request[IO](Method.POST, Uri.unsafeFromString("/admin/users/setup"))
        .withEntity(UrlForm("bootstrapToken" -> token))).unsafeRunSync()
      assert(replay.headers.get(CIString("Set-Cookie")).isEmpty)
      val locked = app.run(Request[IO](Method.GET, Uri.unsafeFromString("/admin/users")).putHeaders(cookie)).unsafeRunSync()
      assertEquals(locked.status, Status.Unauthorized)
    }

  test("initial setup rejects absent and incorrect bootstrap tokens"):
    withContext { (_, userStore, _, _, _, _, _, app) =>
      for token <- Seq("", "incorrect") do
        val response = app.run(Request[IO](Method.POST, Uri.unsafeFromString("/admin/users"))
          .withEntity(UrlForm("username" -> "attacker", "password" -> "password123", "confirmPassword" -> "password123", "bootstrapToken" -> token))).unsafeRunSync()
        assertEquals(response.status, Status.Forbidden)
        assertEquals(userStore.all(), Seq.empty)
    }

  test("GET /admin/users with user lacking EditUsers permission returns 403 Forbidden"):
    withContext { (_, userStore, _, passwordService, authService, _, _, app) =>
      val hash = passwordService.hash("password")
      userStore.add(User("viewer1", hash, Role.Viewer, enabled = true, id = "u1"))
      val session = authService.authenticate("viewer1", "password").get

      val req = Request[IO](Method.GET, Uri.unsafeFromString("/admin/users"))
        .putHeaders(Header.Raw(CIString("Cookie"), s"session=${session.id}"))
      val res = app.run(req).unsafeRunSync()
      assertEquals(res.status, Status.Forbidden)
    }

  test("GET /admin/users with user having EditUsers permission returns 200 OK and renders HTML"):
    withContext { (_, userStore, _, passwordService, authService, _, _, app) =>
      val hash = passwordService.hash("password")
      userStore.add(User("admin", hash, Role.Admin, enabled = true, id = "u-admin"))
      userStore.add(User("bob", hash, Role.Viewer, enabled = true, id = "u-bob"))
      val session = authService.authenticate("admin", "password").get

      val req = Request[IO](Method.GET, Uri.unsafeFromString("/admin/users"))
        .putHeaders(Header.Raw(CIString("Cookie"), s"session=${session.id}"))
      val (res, body) = (for
        r <- app.run(req)
        b <- r.as[String]
      yield (r, b)).unsafeRunSync()

      assertEquals(res.status, Status.Ok)
      assert(body.contains("User Administration"))
      assert(body.contains("admin"))
      assert(body.contains("bob"))
      assert(body.contains("u-admin"))
      assert(body.contains("Add New User"))
      assert(body.contains("id=\"password\""))
      assert(body.contains("id=\"confirmPassword\""))
      assert(body.contains("<select"))
      assert(body.contains("id=\"role\""))
      assert(body.contains("Role Permissions Reference:"))
      assert(body.contains("AllGroup, Debug, DeletePlans, EditPlans, EditUsers, ViewPlans, ViewUsers"))
    }

  test("GET /admin/users?edit=<id> renders edit form with user details"):
    withContext { (_, userStore, _, passwordService, authService, _, _, app) =>
      val hash = passwordService.hash("password")
      userStore.add(User("admin", hash, Role.Admin, enabled = true, id = "u-admin"))
      userStore.add(User("targetuser", hash, Role.Editor, enabled = true, id = "u-target"))
      val session = authService.authenticate("admin", "password").get

      val req = Request[IO](Method.GET, Uri.unsafeFromString("/admin/users?edit=u-target"))
        .putHeaders(Header.Raw(CIString("Cookie"), s"session=${session.id}"))
      val (res, body) = (for
        r <- app.run(req)
        b <- r.as[String]
      yield (r, b)).unsafeRunSync()

      assertEquals(res.status, Status.Ok)
      assert(body.contains("Edit User: targetuser"))
      assert(body.contains("action=\"/admin/users\""))
      assert(body.contains("value=\"targetuser\""))
      assert(body.contains("id=\"password\""))
      assert(body.contains("id=\"confirmPassword\""))
      assert(body.contains("<select"))
      assert(body.contains("selected"))
    }

  test("POST /admin/users creates a new user and hashes password"):
    withContext { (_, userStore, _, passwordService, authService, _, _, app) =>
      val hash = passwordService.hash("adminpass")
      userStore.add(User("admin", hash, Role.Admin, enabled = true, id = "u-admin"))
      val session = authService.authenticate("admin", "adminpass").get

      ics205.util.Ids.useSeqentialStartingAt(100)
      try
        val form = UrlForm(
          "username" -> "newuser",
          "password" -> "secret123",
          "confirmPassword" -> "secret123",
          "role" -> "editor",
          "enabled" -> "true"
        )
        val req = Request[IO](Method.POST, Uri.unsafeFromString("/admin/users"))
          .withEntity(form)
          .putHeaders(Header.Raw(CIString("Cookie"), s"session=${session.id}"))

        val res = app.run(req).unsafeRunSync()
        assertEquals(res.status, Status.SeeOther)
        val location = res.headers.get(CIString("Location")).map(_.head.value).getOrElse("")
        assert(location.startsWith("/admin/users?msg="))

        val created = userStore.findByUsername("newuser")
        assert(created.isDefined)
        assertEquals(created.get.id, "100")
        assertEquals(created.get.username, "newuser")
        assertEquals(created.get.role, Role.Editor)
        assertEquals(created.get.enabled, true)
        assert(passwordService.verify("secret123", created.get.passwordHash))
      finally
        ics205.util.Ids.revertToRandom()
    }

  test("POST /admin/users rejects empty username or password"):
    withContext { (_, userStore, _, passwordService, authService, _, _, app) =>
      val hash = passwordService.hash("adminpass")
      userStore.add(User("admin", hash, Role.Admin, enabled = true, id = "u-admin"))
      val session = authService.authenticate("admin", "adminpass").get

      val formNoUser = UrlForm("username" -> "", "password" -> "secret123")
      val req1 = Request[IO](Method.POST, Uri.unsafeFromString("/admin/users"))
        .withEntity(formNoUser)
        .putHeaders(Header.Raw(CIString("Cookie"), s"session=${session.id}"))
      val res1 = app.run(req1).unsafeRunSync()
      assertEquals(res1.status, Status.UnprocessableEntity)
      assert(res1.as[String].unsafeRunSync().contains("Username cannot be empty."))

      val formNoPass = UrlForm("username" -> "testuser", "password" -> "")
      val req2 = Request[IO](Method.POST, Uri.unsafeFromString("/admin/users"))
        .withEntity(formNoPass)
        .putHeaders(Header.Raw(CIString("Cookie"), s"session=${session.id}"))
      val res2 = app.run(req2).unsafeRunSync()
      assertEquals(res2.status, Status.UnprocessableEntity)
      assert(res2.as[String].unsafeRunSync().contains("Password cannot be empty."))

      val formShortPass = UrlForm("username" -> "testuser", "password" -> "short", "confirmPassword" -> "short")
      val req3 = Request[IO](Method.POST, Uri.unsafeFromString("/admin/users"))
        .withEntity(formShortPass)
        .putHeaders(Header.Raw(CIString("Cookie"), s"session=${session.id}"))
      val res3 = app.run(req3).unsafeRunSync()
      assertEquals(res3.status, Status.UnprocessableEntity)
      assert(res3.as[String].unsafeRunSync().contains("Password must be at least 8 characters."))

      val formMismatchPass = UrlForm("username" -> "testuser", "password" -> "password123", "confirmPassword" -> "different123")
      val req4 = Request[IO](Method.POST, Uri.unsafeFromString("/admin/users"))
        .withEntity(formMismatchPass)
        .putHeaders(Header.Raw(CIString("Cookie"), s"session=${session.id}"))
      val res4 = app.run(req4).unsafeRunSync()
      assertEquals(res4.status, Status.UnprocessableEntity)
      assert(res4.as[String].unsafeRunSync().contains("Passwords do not match."))
    }

  test("failed new-user creation preserves entered username, role, groups, and enabled state"):
    withContext { (_, userStore, _, passwordService, authService, _, _, app) =>
      userStore.add(User("admin", passwordService.hash("admin-password"), Role.Admin, id = "admin-id"))
      val session = authService.authenticate("admin", "admin-password").get
      for (username, password, confirmation, enabled) <- Seq(
        ("rob", "x", "x", true),
        ("rob", "password123", "different123", false),
        ("admin", "password123", "password123", true)
      ) do
        val fields = Map("username" -> username, "password" -> password, "confirmPassword" -> confirmation,
          "role" -> "editor", "group_Cmt7" -> "true", "group_Default" -> "true", "newGroup" -> "new team") ++
          (if enabled then Map("enabled" -> "true") else Map.empty[String, String])
        val response = app.run(Request[IO](Method.POST, Uri.unsafeFromString("/admin/users"))
          .putHeaders(Header.Raw(CIString("Cookie"), s"session=${session.id}"))
          .withEntity(UrlForm(fields.toSeq*))).unsafeRunSync()
        assertEquals(response.status, Status.UnprocessableEntity)
        assert(response.headers.get(CIString("Location")).isEmpty)
        assertEquals(response.headers.get(CIString("Cache-Control")).map(_.head.value), Some("no-store"))
        val html = response.as[String].unsafeRunSync()
        def input(name: String): String =
          ("<input[^>]*name=\"" + java.util.regex.Pattern.quote(name) + "\"[^>]*>").r.findFirstIn(html).get
        assert(input("username").contains(s"value=\"$username\""))
        assert(html.contains("value=\"editor\" selected"))
        assert(input("group_Cmt7").contains("checked"))
        assert(input("group_Default").contains("checked"))
        assertEquals(input("enabled").contains("checked"), enabled)
        assert(input("newGroup").contains("value=\"new team\""))
        assert(html.contains("action=\"/admin/users\""))
        assert(html.contains("Add New User"))
        assert(!html.contains("Update User"))
        assert(!input("password").contains("value="))
        assertEquals(userStore.all().size, 1)
    }

  test("POST /admin/users rejects short or mismatched passwords when updated"):
    withContext { (_, userStore, _, passwordService, authService, _, _, app) =>
      val hash = passwordService.hash("originalpass")
      userStore.add(User("admin", hash, Role.Admin, enabled = true, id = "u-admin"))
      userStore.add(User("alice", hash, Role.Viewer, enabled = true, id = "u-edit"))
      val session = authService.authenticate("admin", "originalpass").get

      val editFormShort = UrlForm(
        "id" -> "u-edit",
        "username" -> "alice",
        "password" -> "short",
        "confirmPassword" -> "short",
        "role" -> "user",
        "enabled" -> "true"
      )
      val req1 = Request[IO](Method.POST, Uri.unsafeFromString("/admin/users"))
        .withEntity(editFormShort)
        .putHeaders(Header.Raw(CIString("Cookie"), s"session=${session.id}"))
      val res1 = app.run(req1).unsafeRunSync()
      assertEquals(res1.status, Status.UnprocessableEntity)
      assert(res1.as[String].unsafeRunSync().contains("Password must be at least 8 characters."))

      val editFormMismatch = UrlForm(
        "id" -> "u-edit",
        "username" -> "alice",
        "password" -> "newsecret456",
        "confirmPassword" -> "wrongsecret456",
        "role" -> "user",
        "enabled" -> "true"
      )
      val req2 = Request[IO](Method.POST, Uri.unsafeFromString("/admin/users"))
        .withEntity(editFormMismatch)
        .putHeaders(Header.Raw(CIString("Cookie"), s"session=${session.id}"))
      val res2 = app.run(req2).unsafeRunSync()
      assertEquals(res2.status, Status.UnprocessableEntity)
      assert(res2.as[String].unsafeRunSync().contains("Passwords do not match."))
    }

  test("failed user edits preserve submitted fields without reflecting passwords or changing stored users"):
    withContext { (_, userStore, _, passwordService, authService, _, _, app) =>
      val admin = User("admin", passwordService.hash("admin-password"), Role.Admin, id = "admin-id")
      val target = User("original", "original-hash", Role.Viewer, id = "target-id", groups = Set("Old Group"))
      userStore.add(admin)
      userStore.add(target)
      val session = authService.authenticate("admin", "admin-password").get
      val cases = Seq(
        ("renamed", "tiny", "tiny", "Password must be at least 8 characters."),
        ("renamed", "first-password", "other-password", "Passwords do not match."),
        ("", "", "", "Username cannot be empty."),
        ("admin", "", "", "already taken")
      )
      for (username, password, confirmation, error) <- cases do
        val response = app.run(Request[IO](Method.POST, Uri.unsafeFromString("/admin/users"))
          .putHeaders(Header.Raw(CIString("Cookie"), s"session=${session.id}"))
          .withEntity(UrlForm(
            "id" -> target.id, "username" -> username,
            "password" -> password, "confirmPassword" -> confirmation,
            "role" -> "editor", "group_New Group" -> "true", "newGroup" -> "another group"
          ))).unsafeRunSync()
        assertEquals(response.status, Status.UnprocessableEntity)
        assert(response.headers.get(CIString("Location")).isEmpty)
        assertEquals(response.headers.get(CIString("Cache-Control")).map(_.head.value), Some("no-store"))
        val html = response.as[String].unsafeRunSync()
        assert(html.contains(error))
        def input(name: String): String =
          ("<input[^>]*name=\"" + java.util.regex.Pattern.quote(name) + "\"[^>]*>").r.findFirstIn(html).get
        assert(input("username").contains(s"value=\"$username\""))
        assert(html.contains("value=\"editor\" selected"))
        assert(input("group_New Group").contains("checked"))
        assert(!input("group_Old Group").contains("checked"))
        assert(!input("enabled").contains("checked"))
        assert(html.contains("name=\"newGroup\" value=\"another group\""))
        assert(!html.contains("original-hash"))
        if password.nonEmpty then assert(!html.contains(s"value=\"$password\""))
        assertEquals(userStore.findById(target.id), Some(target))
    }

  test("user save IO errors preserve submitted fields and show a recoverable error"):
    withContext { (directory, userStore, _, passwordService, authService, _, _, app) =>
      userStore.add(User("admin", passwordService.hash("admin-password"), Role.Admin, id = "admin-id"))
      val original = User("original", "hash", Role.Viewer, id = "target-id")
      userStore.add(original)
      val session = authService.authenticate("admin", "admin-password").get
      val path = directory / "admin" / "users.json"
      os.remove(path)
      os.makeDir.all(path)
      os.write(path / "block-replacement", "blocked")
      val response = app.run(Request[IO](Method.POST, Uri.unsafeFromString("/admin/users"))
        .putHeaders(Header.Raw(CIString("Cookie"), s"session=${session.id}"))
        .withEntity(UrlForm("id" -> original.id, "username" -> "kept-name", "role" -> "editor", "enabled" -> "true", "newGroup" -> "kept group"))).unsafeRunSync()
      assertEquals(response.status, Status.InternalServerError)
      val html = response.as[String].unsafeRunSync()
      assert(html.contains("The user could not be saved. Please try again."))
      assert(html.contains("value=\"kept-name\""))
      assert(html.contains("value=\"kept group\""))
      assertEquals(userStore.findById(original.id), Some(original))
    }

  test("POST /admin/users updates user roles, enabled state, and optionally password, revoking active sessions"):
    withContext { (_, userStore, sessionStore, passwordService, authService, _, _, app) =>
      val hash = passwordService.hash("originalpass")
      userStore.add(User("admin", hash, Role.Admin, enabled = true, id = "u-admin"))
      userStore.add(User("alice", hash, Role.Viewer, enabled = true, id = "u-edit"))
      val adminSession = authService.authenticate("admin", "originalpass").get
      val aliceSession1 = authService.authenticate("alice", "originalpass").get

      assert(sessionStore.find(aliceSession1.id).isDefined)

      // Update role/enabled -> revokes alice sessions
      val editForm1 = UrlForm(
        "id" -> "u-edit",
        "username" -> "alice_updated",
        "password" -> "",
        "role" -> "editor"
        // enabled omitted -> false
      )
      val req1 = Request[IO](Method.POST, Uri.unsafeFromString("/admin/users"))
        .withEntity(editForm1)
        .putHeaders(Header.Raw(CIString("Cookie"), s"session=${adminSession.id}"))
      val res1 = app.run(req1).unsafeRunSync()
      assertEquals(res1.status, Status.SeeOther)

      val u1 = userStore.findById("u-edit").get
      assertEquals(u1.username, "alice_updated")
      assertEquals(u1.role, Role.Editor)
      assertEquals(u1.enabled, false)
      assertEquals(u1.passwordHash, hash) // Hash preserved
      assertEquals(sessionStore.find(aliceSession1.id), None) // Alice session revoked

      // Create new session for alice (after re-enabling)
      userStore.save(u1.copy(enabled = true))
      val aliceSession2 = authService.authenticate("alice_updated", "originalpass").get
      assert(sessionStore.find(aliceSession2.id).isDefined)

      // Update with new password -> revokes alice sessions
      val editForm2 = UrlForm(
        "id" -> "u-edit",
        "username" -> "alice_updated",
        "password" -> "newsecret456",
        "confirmPassword" -> "newsecret456",
        "role" -> "admin",
        "enabled" -> "true"
      )
      val req2 = Request[IO](Method.POST, Uri.unsafeFromString("/admin/users"))
        .withEntity(editForm2)
        .putHeaders(Header.Raw(CIString("Cookie"), s"session=${adminSession.id}"))
      val res2 = app.run(req2).unsafeRunSync()
      assertEquals(res2.status, Status.SeeOther)

      val u2 = userStore.findById("u-edit").get
      assertEquals(u2.role, Role.Admin)
      assertEquals(u2.enabled, true)
      assert(passwordService.verify("newsecret456", u2.passwordHash))
      assertEquals(sessionStore.find(aliceSession2.id), None) // Alice session revoked
    }

  test("POST /admin/users/delete deletes the specified user and revokes active sessions"):
    withContext { (_, userStore, sessionStore, passwordService, authService, _, _, app) =>
      val hash = passwordService.hash("password")
      userStore.add(User("admin", hash, Role.Admin, enabled = true, id = "u-admin"))
      userStore.add(User("delete_me", hash, Role.Viewer, enabled = true, id = "u-del"))
      val session = authService.authenticate("admin", "password").get
      val delSession = authService.authenticate("delete_me", "password").get

      assert(sessionStore.find(delSession.id).isDefined)

      val form = UrlForm("id" -> "u-del")
      val req = Request[IO](Method.POST, Uri.unsafeFromString("/admin/users/delete"))
        .withEntity(form)
        .putHeaders(Header.Raw(CIString("Cookie"), s"session=${session.id}"))

      val res = app.run(req).unsafeRunSync()
      assertEquals(res.status, Status.SeeOther)
      assert(userStore.findById("u-del").isEmpty)
      assertEquals(sessionStore.find(delSession.id), None)
    }

  test("POST /admin/users and /edit assign groups and correct capitalization to Caps words"):
    withContext { (_, userStore, _, passwordService, authService, _, _, app) =>
      val hash = passwordService.hash("password")
      userStore.add(User("admin", hash, Role.Admin, enabled = true, id = "u-admin"))
      val session = authService.authenticate("admin", "password").get

      val createForm = UrlForm(
        "username" -> "bob",
        "password" -> "password123",
        "confirmPassword" -> "password123",
        "role" -> "user",
        "enabled" -> "true",
        "group_Default" -> "true",
        "newGroup" -> "hello world"
      )
      val req1 = Request[IO](Method.POST, Uri.unsafeFromString("/admin/users"))
        .withEntity(createForm)
        .putHeaders(Header.Raw(CIString("Cookie"), s"session=${session.id}"))
      val res1 = app.run(req1).unsafeRunSync()
      assertEquals(res1.status, Status.SeeOther)

      val createdBob = userStore.findByUsername("bob").get
      assertEquals(createdBob.groups, Set("Default", "Hello World"))

      val editForm = UrlForm(
        "id" -> createdBob.id,
        "username" -> "bob",
        "role" -> "user",
        "enabled" -> "true",
        "group_Hello World" -> "true",
        "newGroup" -> "ARES OPERATIONS"
      )
      val req2 = Request[IO](Method.POST, Uri.unsafeFromString("/admin/users"))
        .withEntity(editForm)
        .putHeaders(Header.Raw(CIString("Cookie"), s"session=${session.id}"))
      val res2 = app.run(req2).unsafeRunSync()
      assertEquals(res2.status, Status.SeeOther)

      val updatedBob = userStore.findById(createdBob.id).get
      assertEquals(updatedBob.groups, Set("Hello World", "Ares Operations"))
    }
