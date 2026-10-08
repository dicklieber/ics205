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
import ics205.model.{Frequency, Ics205, Ics205Channel, Ics205Event, OperationalPeriod, RadioMode, RxWithOffset}
import ics205.store.{Ics205Store, InMemJsonSessionStore, SessionStore, UserStore}
import ics205.util.FileHelper
import io.circe.parser.decode
import org.http4s.{Header, Method, Request, Status, Uri}
import org.typelevel.ci.CIString
import sttp.tapir.server.http4s.Http4sServerInterpreter

class JsonExportEndpointsTests extends munit.FunSuite:

  private def withContext(
    test: (os.Path, UserStore, SessionStore, Ics205Store, AuthenticationService, org.http4s.HttpApp[IO]) => Unit
  ): Unit =
    val tempDir = os.temp.dir()
    try
      val helper = new FileHelper(tempDir)
      val config = AuthConfig()
      val userStore = new UserStore(helper, config)
      val sessionStore = new InMemJsonSessionStore(helper, config)
      val passwordService = new ScalaPassPasswordService()
      val authService = new AuthenticationService(userStore, passwordService, sessionStore)
      val ics205Store = new Ics205Store(helper)

      val sampleChannel = Ics205Channel(
        id = "ch-1",
        zoneGroup = Some("Zone 1"),
        channelNumber = Some("1"),
        function = "Tac 1",
        name = "TAC1",
        assignment = "Operations",
        frequency = RxWithOffset(Frequency(BigDecimal("146.520")), Frequency(BigDecimal("0.600"))),
        mode = RadioMode.Fm,
        remarks = "Primary tactical channel"
      )
      val plan = Ics205(
        incidentName = "Wildfire Incident",
        operationalPeriod = OperationalPeriod(),
        channels = Seq(sampleChannel)
      )
      ics205Store.save(Ics205Event(plan))

      val jsonEndpoints = new JsonExportEndpoints(ics205Store, authService, config)
      val httpApp = Http4sServerInterpreter[IO]().toRoutes(jsonEndpoints.endpoints).orNotFound

      test(tempDir, userStore, sessionStore, ics205Store, authService, httpApp)
    finally
      os.remove.all(tempDir)

  test("GET /export/json without session returns 401 Unauthorized"):
    withContext { (_, _, _, _, _, app) =>
      val req = Request[IO](Method.GET, Uri.unsafeFromString("/export/json"))
      val res = app.run(req).unsafeRunSync()
      assertEquals(res.status, Status.Unauthorized)
    }

  test("GET /export/json with invalid session returns 401 Unauthorized"):
    withContext { (_, _, _, _, _, app) =>
      val req = Request[IO](Method.GET, Uri.unsafeFromString("/export/json"))
        .putHeaders(Header.Raw(CIString("Cookie"), "session=invalid-session-id"))
      val res = app.run(req).unsafeRunSync()
      assertEquals(res.status, Status.Unauthorized)
    }

  test("GET /export/json with valid session downloads pretty JSON for current event"):
    withContext { (_, userStore, _, store, authService, app) =>
      val passwordService = new ScalaPassPasswordService()
      userStore.add(User("testuser", passwordService.hash("password"), Role.User, enabled = true, id = "u1"))
      val session = authService.authenticate("testuser", "password").get

      val req = Request[IO](Method.GET, Uri.unsafeFromString("/export/json"))
        .putHeaders(Header.Raw(CIString("Cookie"), s"session=${session.id}"))
      val res = app.run(req).unsafeRunSync()

      assertEquals(res.status, Status.Ok)
      assertEquals(res.headers.get(CIString("Content-Type")).map(_.head.value), Some("application/json; charset=utf-8"))
      assertEquals(res.headers.get(CIString("Content-Disposition")).map(_.head.value), Some("attachment; filename=\"Wildfire Incident.json\""))
      assertEquals(res.headers.get(CIString("Cache-Control")).map(_.head.value), Some("no-store"))

      val body = res.as[String].unsafeRunSync()
      val decoded = decode[Ics205Event](body)
      assert(decoded.isRight)
      assertEquals(decoded.toOption.get.ics205.incidentName, "Wildfire Incident")
      assertEquals(decoded.toOption.get.ics205.channels.size, 1)
      assertEquals(decoded.toOption.get.ics205.channels.head.name, "TAC1")
    }
