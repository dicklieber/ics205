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
      val adminEndpoints = new UserAdminEndpoints(userStore, passwordService, security)

      val allServerEndpoints: List[ServerEndpoint[Any, IO]] = adminEndpoints.endpoints
      val httpApp = Http4sServerInterpreter[IO]().toRoutes(allServerEndpoints).orNotFound
      test(tempDir, userStore, sessionStore, passwordService, authService, security, adminEndpoints, httpApp)
    finally
      os.remove.all(tempDir)

  test("GET /admin/users without session cookie returns 401 Unauthorized"):
    withContext { (_, _, _, _, _, _, _, app) =>
      val req = Request[IO](Method.GET, Uri.unsafeFromString("/admin/users"))
      val res = app.run(req).unsafeRunSync()
      assertEquals(res.status, Status.Unauthorized)
    }

  test("GET /admin/users with user lacking EditUsers permission returns 403 Forbidden"):
    withContext { (_, userStore, _, passwordService, authService, _, _, app) =>
      val hash = passwordService.hash("password")
      userStore.add(User("u1", "viewer1", hash, Set("viewer"), enabled = true))
      val session = authService.authenticate("viewer1", "password").get

      val req = Request[IO](Method.GET, Uri.unsafeFromString("/admin/users"))
        .putHeaders(Header.Raw(CIString("Cookie"), s"session=${session.id}"))
      val res = app.run(req).unsafeRunSync()
      assertEquals(res.status, Status.Forbidden)
    }

  test("GET /admin/users with user having EditUsers permission returns 200 OK and renders HTML"):
    withContext { (_, userStore, _, passwordService, authService, _, _, app) =>
      val hash = passwordService.hash("password")
      userStore.add(User("u-admin", "admin", hash, Set("admin"), enabled = true))
      userStore.add(User("u-bob", "bob", hash, Set("user"), enabled = true))
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
    }

  test("GET /admin/users?edit=<id> renders edit form with user details"):
    withContext { (_, userStore, _, passwordService, authService, _, _, app) =>
      val hash = passwordService.hash("password")
      userStore.add(User("u-admin", "admin", hash, Set("admin"), enabled = true))
      userStore.add(User("u-target", "targetuser", hash, Set("editor"), enabled = true))
      val session = authService.authenticate("admin", "password").get

      val req = Request[IO](Method.GET, Uri.unsafeFromString("/admin/users?edit=u-target"))
        .putHeaders(Header.Raw(CIString("Cookie"), s"session=${session.id}"))
      val (res, body) = (for
        r <- app.run(req)
        b <- r.as[String]
      yield (r, b)).unsafeRunSync()

      assertEquals(res.status, Status.Ok)
      assert(body.contains("Edit User: targetuser"))
      assert(body.contains("action=\"/admin/users/edit\""))
      assert(body.contains("value=\"targetuser\""))
    }

  test("POST /admin/users/create creates a new user and hashes password"):
    withContext { (_, userStore, _, passwordService, authService, _, _, app) =>
      val hash = passwordService.hash("adminpass")
      userStore.add(User("u-admin", "admin", hash, Set("admin"), enabled = true))
      val session = authService.authenticate("admin", "adminpass").get

      val form = UrlForm(
        "username" -> "newuser",
        "password" -> "secret123",
        "roles" -> "editor, user",
        "enabled" -> "true"
      )
      val req = Request[IO](Method.POST, Uri.unsafeFromString("/admin/users/create"))
        .withEntity(form)
        .putHeaders(Header.Raw(CIString("Cookie"), s"session=${session.id}"))

      val res = app.run(req).unsafeRunSync()
      assertEquals(res.status, Status.SeeOther)
      val location = res.headers.get(CIString("Location")).map(_.head.value).getOrElse("")
      assert(location.startsWith("/admin/users?msg="))

      val created = userStore.findByUsername("newuser")
      assert(created.isDefined)
      assertEquals(created.get.username, "newuser")
      assertEquals(created.get.roles, Set("editor", "user"))
      assertEquals(created.get.enabled, true)
      assert(passwordService.verify("secret123", created.get.passwordHash))
    }

  test("POST /admin/users/create rejects empty username or password"):
    withContext { (_, userStore, _, passwordService, authService, _, _, app) =>
      val hash = passwordService.hash("adminpass")
      userStore.add(User("u-admin", "admin", hash, Set("admin"), enabled = true))
      val session = authService.authenticate("admin", "adminpass").get

      val formNoUser = UrlForm("username" -> "", "password" -> "secret123")
      val req1 = Request[IO](Method.POST, Uri.unsafeFromString("/admin/users/create"))
        .withEntity(formNoUser)
        .putHeaders(Header.Raw(CIString("Cookie"), s"session=${session.id}"))
      val res1 = app.run(req1).unsafeRunSync()
      assertEquals(res1.status, Status.SeeOther)
      assert(res1.headers.get(CIString("Location")).get.head.value.contains("err="))

      val formNoPass = UrlForm("username" -> "testuser", "password" -> "")
      val req2 = Request[IO](Method.POST, Uri.unsafeFromString("/admin/users/create"))
        .withEntity(formNoPass)
        .putHeaders(Header.Raw(CIString("Cookie"), s"session=${session.id}"))
      val res2 = app.run(req2).unsafeRunSync()
      assertEquals(res2.status, Status.SeeOther)
      assert(res2.headers.get(CIString("Location")).get.head.value.contains("err="))
    }

  test("POST /admin/users/edit updates user roles, enabled state, and optionally password"):
    withContext { (_, userStore, _, passwordService, authService, _, _, app) =>
      val hash = passwordService.hash("originalpass")
      userStore.add(User("u-admin", "admin", hash, Set("admin"), enabled = true))
      userStore.add(User("u-edit", "alice", hash, Set("user"), enabled = true))
      val session = authService.authenticate("admin", "originalpass").get

      // Update without password change
      val editForm1 = UrlForm(
        "id" -> "u-edit",
        "username" -> "alice_updated",
        "password" -> "",
        "roles" -> "editor, viewer"
        // enabled omitted -> false
      )
      val req1 = Request[IO](Method.POST, Uri.unsafeFromString("/admin/users/edit"))
        .withEntity(editForm1)
        .putHeaders(Header.Raw(CIString("Cookie"), s"session=${session.id}"))
      val res1 = app.run(req1).unsafeRunSync()
      assertEquals(res1.status, Status.SeeOther)

      val u1 = userStore.findById("u-edit").get
      assertEquals(u1.username, "alice_updated")
      assertEquals(u1.roles, Set("editor", "viewer"))
      assertEquals(u1.enabled, false)
      assertEquals(u1.passwordHash, hash) // Hash preserved

      // Update with new password
      val editForm2 = UrlForm(
        "id" -> "u-edit",
        "username" -> "alice_updated",
        "password" -> "newsecret456",
        "roles" -> "admin",
        "enabled" -> "true"
      )
      val req2 = Request[IO](Method.POST, Uri.unsafeFromString("/admin/users/edit"))
        .withEntity(editForm2)
        .putHeaders(Header.Raw(CIString("Cookie"), s"session=${session.id}"))
      val res2 = app.run(req2).unsafeRunSync()
      assertEquals(res2.status, Status.SeeOther)

      val u2 = userStore.findById("u-edit").get
      assertEquals(u2.roles, Set("admin"))
      assertEquals(u2.enabled, true)
      assert(passwordService.verify("newsecret456", u2.passwordHash))
    }

  test("POST /admin/users/delete deletes the specified user"):
    withContext { (_, userStore, _, passwordService, authService, _, _, app) =>
      val hash = passwordService.hash("password")
      userStore.add(User("u-admin", "admin", hash, Set("admin"), enabled = true))
      userStore.add(User("u-del", "delete_me", hash, Set("user"), enabled = true))
      val session = authService.authenticate("admin", "password").get

      val form = UrlForm("id" -> "u-del")
      val req = Request[IO](Method.POST, Uri.unsafeFromString("/admin/users/delete"))
        .withEntity(form)
        .putHeaders(Header.Raw(CIString("Cookie"), s"session=${session.id}"))

      val res = app.run(req).unsafeRunSync()
      assertEquals(res.status, Status.SeeOther)
      assert(userStore.findById("u-del").isEmpty)
    }
