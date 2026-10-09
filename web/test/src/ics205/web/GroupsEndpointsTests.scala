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

package ics205.web

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import ics205.auth.*
import ics205.model.{Ics205, Ics205Event, OperationalPeriod}
import ics205.store.{Ics205Store, InMemJsonSessionStore, SessionStore, UserStore}
import ics205.util.FileHelper
import ics205.web.auth.AuthSecurity
import org.http4s.{Header, Method, Request, Status, Uri}
import org.typelevel.ci.CIString
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.http4s.Http4sServerInterpreter

class GroupsEndpointsTests extends munit.FunSuite:

  private def withContext(
    test: (os.Path, Ics205Store, UserStore, SessionStore, PasswordService, AuthenticationService, AuthSecurity, org.http4s.HttpApp[IO]) => Unit
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
      val store = new Ics205Store(helper)
      val groupsEndpoints = new GroupsEndpoints(store, userStore, security)

      val allServerEndpoints: List[ServerEndpoint[Any, IO]] = groupsEndpoints.endpoints
      val httpApp = Http4sServerInterpreter[IO]().toRoutes(allServerEndpoints).orNotFound
      test(tempDir, store, userStore, sessionStore, passwordService, authService, security, httpApp)
    finally
      os.remove.all(tempDir)

  test("GET /groups without session returns 401 Unauthorized"):
    withContext { (_, _, _, _, _, _, _, app) =>
      val req = Request[IO](Method.GET, Uri.unsafeFromString("/groups"))
      val res = app.run(req).unsafeRunSync()
      assertEquals(res.status, Status.Unauthorized)
    }

  test("GET /groups renders all known groups, associated users and events, and edit links"):
    withContext { (_, store, userStore, _, passwordService, authService, _, app) =>
      val hash = passwordService.hash("password")
      val admin = userStore.add(User("admin", hash, Role.Admin, enabled = true, id = "u-admin")).toOption.get
      val alice = userStore.add(User("alice", hash, Role.User, enabled = true, id = "u-alice", groups = Set("Ares Operations", "North Team"))).toOption.get
      val bob = userStore.add(User("bob", hash, Role.User, enabled = true, id = "u-bob", groups = Set("North Team"))).toOption.get

      val ev1 = Ics205Event("Ev-North", Ics205(incidentName = "North Incident", operationalPeriod = OperationalPeriod(), channels = Seq.empty), group = "North Team")
      val ev2 = Ics205Event("Ev-Ares", Ics205(incidentName = "Ares Drill", operationalPeriod = OperationalPeriod(), channels = Seq.empty), group = "Ares Operations")
      val ev3 = Ics205Event("Ev-Default", Ics205(incidentName = "General Ops", operationalPeriod = OperationalPeriod(), channels = Seq.empty), group = "Default")

      store.save(ev1)
      store.save(ev2)
      store.save(ev3)

      val session = authService.authenticate("admin", "password").get

      val req = Request[IO](Method.GET, Uri.unsafeFromString("/groups"))
        .putHeaders(Header.Raw(CIString("Cookie"), s"session=${session.id}"))
      val res = app.run(req).unsafeRunSync()
      assertEquals(res.status, Status.Ok)

      val body = res.as[String].unsafeRunSync()
      assert(body.contains("Groups"))
      assert(body.contains("Ares Operations"))
      assert(body.contains("North Team"))
      assert(body.contains("Default"))

      // Users shown in groups
      assert(body.contains("alice"))
      assert(body.contains("bob"))

      // Events shown in groups
      assert(body.contains("North Incident"))
      assert(body.contains("Ares Drill"))
      assert(body.contains("General Ops"))

      // Links to edit user and edit event
      assert(body.contains(s"/admin/users?edit=${alice.id}#user-form"))
      assert(body.contains(s"/admin/users?edit=${bob.id}#user-form"))
      assert(body.contains("/events/metadata?name=Ev-North"))
      assert(body.contains("/events/metadata?name=Ev-Ares"))
    }
