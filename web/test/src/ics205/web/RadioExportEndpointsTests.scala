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
import ics205.exporter.{RadioExportDefinitions, RadioExporter}
import ics205.model.{Frequency, Ics205, Ics205Channel, OperationalPeriod, RadioMode, RxWithOffset}
import ics205.store.{Ics205Store, InMemJsonSessionStore, SessionStore, UserStore}
import ics205.util.FileHelper
import org.http4s.{Header, Method, Request, Status, Uri, UrlForm}
import org.typelevel.ci.CIString
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.http4s.Http4sServerInterpreter

class RadioExportEndpointsTests extends munit.FunSuite:

  private def withContext(
    test: (os.Path, UserStore, SessionStore, Ics205Store, AuthenticationService, RadioExportEndpoints, org.http4s.HttpApp[IO]) => Unit
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

      // Seed a sample ICS-205 plan
      val sampleChannel = Ics205Channel(
        id = "1",
        zoneGroup = Some("Zone 1"),
        channelNumber = Some("CH-01"),
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
      ics205Store.save(plan)

      val definitions = new RadioExportDefinitions()
      val exporter = new RadioExporter(definitions)
      val exportEndpoints = new RadioExportEndpoints(exporter, definitions, ics205Store, authService, config)

      val allServerEndpoints: List[ServerEndpoint[Any, IO]] = exportEndpoints.endpoints
      val httpApp = Http4sServerInterpreter[IO]().toRoutes(allServerEndpoints).orNotFound

      test(tempDir, userStore, sessionStore, ics205Store, authService, exportEndpoints, httpApp)
    finally
      os.remove.all(tempDir)

  test("GET /export/radio without session redirects to /login"):
    withContext { (_, _, _, _, _, _, app) =>
      val req = Request[IO](Method.GET, Uri.unsafeFromString("/export/radio"))
      val res = app.run(req).unsafeRunSync()
      assertEquals(res.status, Status.SeeOther)
      assertEquals(res.headers.get(CIString("Location")).map(_.head.value), Some("/login"))
    }

  test("GET /export redirects to /export/radio"):
    withContext { (_, _, _, _, _, _, app) =>
      val req = Request[IO](Method.GET, Uri.unsafeFromString("/export"))
      val res = app.run(req).unsafeRunSync()
      assertEquals(res.status, Status.SeeOther)
      assertEquals(res.headers.get(CIString("Location")).map(_.head.value), Some("/export/radio"))
    }

  test("GET /export/radio with authenticated user renders export page"):
    withContext { (_, userStore, _, _, authService, _, app) =>
      val passwordService = new ScalaPassPasswordService()
      userStore.add(User("u1", "testuser", passwordService.hash("password"), RolePermissions.User, enabled = true))
      val session = authService.authenticate("testuser", "password").get

      val req = Request[IO](Method.GET, Uri.unsafeFromString("/export/radio"))
        .putHeaders(Header.Raw(CIString("Cookie"), s"session=${session.id}"))
      val (res, body) = (for
        r <- app.run(req)
        b <- r.as[String]
      yield (r, b)).unsafeRunSync()

      assertEquals(res.status, Status.Ok)
      assert(body.contains("Export Radio CSV"))
      assert(body.contains("Wildfire Incident"))
      assert(body.contains("Kenwood TH-D75"))
      assert(body.contains("Include Header Row"))
      assert(body.contains("Generate CSV"))
    }

  test("POST /export/radio generates CSV and displays in textarea"):
    withContext { (_, userStore, _, _, authService, _, app) =>
      val passwordService = new ScalaPassPasswordService()
      userStore.add(User("u1", "testuser", passwordService.hash("password"), RolePermissions.User, enabled = true))
      val session = authService.authenticate("testuser", "password").get

      val form = UrlForm(
        "definition" -> "Kenwood TH-D75",
        "includeHeader" -> "true"
      )

      val req = Request[IO](Method.POST, Uri.unsafeFromString("/export/radio"))
        .putHeaders(Header.Raw(CIString("Cookie"), s"session=${session.id}"))
        .withEntity(form)

      val (res, body) = (for
        r <- app.run(req)
        b <- r.as[String]
      yield (r, b)).unsafeRunSync()

      assertEquals(res.status, Status.Ok)
      assert(body.contains("Generated CSV Output"))
      assert(body.contains("id=\"csv-output\""))
      assert(body.contains("Receive Frequency"))
      assert(body.contains("146.520"))
      assert(body.contains("147.120"))
      assert(body.contains("TAC1 Operations"))
      assert(body.contains("Copy to Clipboard"))
    }

  test("POST /export/radio without includeHeader generates CSV without header"):
    withContext { (_, userStore, _, _, authService, _, app) =>
      val passwordService = new ScalaPassPasswordService()
      userStore.add(User("u1", "testuser", passwordService.hash("password"), RolePermissions.User, enabled = true))
      val session = authService.authenticate("testuser", "password").get

      val form = UrlForm(
        "definition" -> "Kenwood TH-D75"
      )

      val req = Request[IO](Method.POST, Uri.unsafeFromString("/export/radio"))
        .putHeaders(Header.Raw(CIString("Cookie"), s"session=${session.id}"))
        .withEntity(form)

      val (res, body) = (for
        r <- app.run(req)
        b <- r.as[String]
      yield (r, b)).unsafeRunSync()

      assertEquals(res.status, Status.Ok)
      assert(body.contains("Generated CSV Output"))
      assert(!body.contains("Receive Frequency"))
      assert(body.contains("146.520"))
    }
