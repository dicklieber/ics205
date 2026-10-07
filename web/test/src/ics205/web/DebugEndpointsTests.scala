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
import ics205.model.Ics205
import ics205.store.{Ics205Store, InMemJsonSessionStore, SessionStore, UserStore}
import ics205.util.FileHelper
import ics205.web.auth.AuthSecurity
import io.circe.Printer
import io.circe.syntax.*
import org.http4s.{Header, Method, Request, Status, Uri}
import org.typelevel.ci.CIString
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.http4s.Http4sServerInterpreter

class DebugEndpointsTests extends munit.FunSuite:

  private def withContext(
    test: (os.Path, Ics205Store, UserStore, SessionStore, PasswordService, AuthenticationService, AuthSecurity, DebugEndpoints, org.http4s.HttpApp[IO]) => Unit
  ): Unit =
    val tempDir = os.temp.dir()
    try
      val helper = new FileHelper(tempDir)
      val config = AuthConfig()
      val ics205Store = new Ics205Store(helper)
      val userStore = new UserStore(helper, config)
      val sessionStore = new InMemJsonSessionStore(helper, config)
      val passwordService = new ScalaPassPasswordService()
      val authService = new AuthenticationService(userStore, passwordService, sessionStore)
      val security = new AuthSecurity(authService, config)
      val debugEndpoints = new DebugEndpoints(ics205Store, userStore, sessionStore, security, helper)

      val allServerEndpoints: List[ServerEndpoint[Any, IO]] = debugEndpoints.endpoints
      val httpApp = Http4sServerInterpreter[IO]().toRoutes(allServerEndpoints).orNotFound
      test(tempDir, ics205Store, userStore, sessionStore, passwordService, authService, security, debugEndpoints, httpApp)
    finally
      os.remove.all(tempDir)

  test("GET /debug/reload-files without session cookie returns 401 Unauthorized"):
    withContext { (_, _, _, _, _, _, _, _, app) =>
      val req = Request[IO](Method.GET, Uri.unsafeFromString("/debug/reload-files"))
      val res = app.run(req).unsafeRunSync()
      assertEquals(res.status, Status.Unauthorized)
    }

  test("GET /debug/reload-files with user lacking Debug permission returns 403 Forbidden"):
    withContext { (_, _, userStore, _, passwordService, authService, _, _, app) =>
      val hash = passwordService.hash("password")
      userStore.add(User("viewer1", hash, Role.Viewer, enabled = true, id = "u1"))
      val session = authService.authenticate("viewer1", "password").get

      val req = Request[IO](Method.GET, Uri.unsafeFromString("/debug/reload-files"))
        .putHeaders(Header.Raw(CIString("Cookie"), s"session=${session.id}"))
      val res = app.run(req).unsafeRunSync()
      assertEquals(res.status, Status.Forbidden)
    }

  test("GET /debug/reload-files with user having Debug permission reloads files and redirects"):
    withContext { (tempDir, store, userStore, sessionStore, passwordService, authService, _, _, app) =>
      val hash = passwordService.hash("password")
      userStore.add(User("admin", hash, Role.Admin, enabled = true, id = "u-admin"))
      val session = authService.authenticate("admin", "password").get

      // Initial event store - create an event
      val initialEvent = ics205.model.Ics205Event(
        id = "TestEvent",
        ics205 = ics205.model.Ics205(incidentName = "Initial Incident", operationalPeriod = ics205.model.OperationalPeriod(), channels = Seq.empty),
        metadata = ics205.model.Ics205Metadata()
      )
      store.save(initialEvent)
      assertEquals(store.getEvent("TestEvent").get.ics205.incidentName, "Initial Incident")

      // Modify the event file directly on disk
      val eventsDir = tempDir / "events"
      val eventFile = eventsDir / "TestEvent.json"
      val updatedEvent = initialEvent.copy(
        ics205 = initialEvent.ics205.copy(incidentName = "Updated Incident From Disk")
      )
      val json = updatedEvent.asJson.printWith(Printer.spaces2)
      os.write.over(eventFile, json)

      // Before reload, in-memory store still has old incidentName
      assertEquals(store.getEvent("TestEvent").get.ics205.incidentName, "Initial Incident")

      // Call GET /debug/reload-files
      val req = Request[IO](Method.GET, Uri.unsafeFromString("/debug/reload-files"))
        .putHeaders(Header.Raw(CIString("Cookie"), s"session=${session.id}"))
      val res = app.run(req).unsafeRunSync()

      assertEquals(res.status, Status.SeeOther)
      val location = res.headers.get(CIString("Location")).map(_.head.value).getOrElse("")
      assertEquals(location, "/")

      // After reload, in-memory store has updated data from disk
      assertEquals(store.getEvent("TestEvent").get.ics205.incidentName, "Updated Incident From Disk")
    }

  test("GET /debug/reload-files redirects to returnUrl if provided"):
    withContext { (_, _, userStore, _, passwordService, authService, _, _, app) =>
      val hash = passwordService.hash("password")
      userStore.add(User("admin", hash, Role.Admin, enabled = true, id = "u-admin"))
      val session = authService.authenticate("admin", "password").get

      val req = Request[IO](Method.GET, Uri.unsafeFromString("/debug/reload-files?returnUrl=%2Fradio"))
        .putHeaders(Header.Raw(CIString("Cookie"), s"session=${session.id}"))
      val res = app.run(req).unsafeRunSync()

      assertEquals(res.status, Status.SeeOther)
      val location = res.headers.get(CIString("Location")).map(_.head.value).getOrElse("")
      assertEquals(location, "/radio")
    }

  test("POST /debug/reload-files reloads files and returns SeeOther"):
    withContext { (tempDir, store, userStore, sessionStore, passwordService, authService, _, _, app) =>
      val hash = passwordService.hash("password")
      userStore.add(User("admin", hash, Role.Admin, enabled = true, id = "u-admin"))
      val session = authService.authenticate("admin", "password").get

      val initialEvent = ics205.model.Ics205Event(
        id = "PostEvent",
        ics205 = ics205.model.Ics205(incidentName = "Initial Incident", operationalPeriod = ics205.model.OperationalPeriod(), channels = Seq.empty),
        metadata = ics205.model.Ics205Metadata()
      )
      store.save(initialEvent)

      val eventsDir = tempDir / "events"
      val eventFile = eventsDir / "PostEvent.json"
      val updatedEvent = initialEvent.copy(
        ics205 = initialEvent.ics205.copy(incidentName = "POST Reload Incident")
      )
      val json = updatedEvent.asJson.printWith(Printer.spaces2)
      os.write.over(eventFile, json)

      val req = Request[IO](Method.POST, Uri.unsafeFromString("/debug/reload-files"))
        .putHeaders(Header.Raw(CIString("Cookie"), s"session=${session.id}"))
      val res = app.run(req).unsafeRunSync()

      assertEquals(res.status, Status.SeeOther)
      assertEquals(store.getEvent("PostEvent").get.ics205.incidentName, "POST Reload Incident")
    }

  test("GET /debug/download-directory without session returns 401 Unauthorized"):
    withContext { (_, _, _, _, _, _, _, _, app) =>
      val req = Request[IO](Method.GET, Uri.unsafeFromString("/debug/download-directory"))
      val res = app.run(req).unsafeRunSync()
      assertEquals(res.status, Status.Unauthorized)
    }

  test("GET /debug/download-directory with user lacking Debug permission returns 403 Forbidden"):
    withContext { (_, _, userStore, _, passwordService, authService, _, _, app) =>
      val hash = passwordService.hash("password")
      userStore.add(User("viewer1", hash, Role.Viewer, enabled = true, id = "u1"))
      val session = authService.authenticate("viewer1", "password").get

      val req = Request[IO](Method.GET, Uri.unsafeFromString("/debug/download-directory"))
        .putHeaders(Header.Raw(CIString("Cookie"), s"session=${session.id}"))
      val res = app.run(req).unsafeRunSync()
      assertEquals(res.status, Status.Forbidden)
    }

  test("GET /debug/download-directory by admin downloads zip archive of entire FileHelper.directory"):
    withContext { (tempDir, store, userStore, sessionStore, passwordService, authService, _, _, app) =>
      val hash = passwordService.hash("password")
      userStore.add(User("admin", hash, Role.Admin, enabled = true, id = "u-admin"))
      val session = authService.authenticate("admin", "password").get

      // Add files in data directory
      val event = ics205.model.Ics205Event(
        id = "SummerDrill",
        ics205 = ics205.model.Ics205(incidentName = "Summer Incident", operationalPeriod = ics205.model.OperationalPeriod(), channels = Seq.empty),
        metadata = ics205.model.Ics205Metadata()
      )
      store.save(event)
      os.write(tempDir / "extra.txt", "some extra text")

      val req = Request[IO](Method.GET, Uri.unsafeFromString("/debug/download-directory"))
        .putHeaders(Header.Raw(CIString("Cookie"), s"session=${session.id}"))
      val res = app.run(req).unsafeRunSync()

      assertEquals(res.status, Status.Ok)
      assertEquals(res.headers.get(CIString("Content-Type")).map(_.head.value), Some("application/zip"))
      val disposition = res.headers.get(CIString("Content-Disposition")).map(_.head.value).getOrElse("")
      assert(disposition.matches("""attachment;\s*filename="ics205-\d{8}T\d{6}Z\.zip""""))
      assertEquals(res.headers.get(CIString("Cache-Control")).map(_.head.value), Some("no-store"))

      val bodyBytes = res.body.compile.toVector.unsafeRunSync().toArray
      assert(bodyBytes.nonEmpty)

      val entries = collection.mutable.Map[String, String]()
      val bais = new java.io.ByteArrayInputStream(bodyBytes)
      val zis = new java.util.zip.ZipInputStream(bais)
      var entry = zis.getNextEntry
      while entry != null do
        val name = entry.getName
        val content = if entry.isDirectory then "" else new String(zis.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
        entries(name) = content
        zis.closeEntry()
        entry = zis.getNextEntry
      zis.close()

      assertEquals(entries.get("extra.txt"), Some("some extra text"))
      assert(entries.contains("events/SummerDrill.ics205") || entries.contains("events/SummerDrill.json"))
      val drillContent = entries.get("events/SummerDrill.ics205").orElse(entries.get("events/SummerDrill.json")).getOrElse("")
      assert(drillContent.contains("Summer Incident"))
    }

  test("GET /debug/download-data alias endpoint also returns directory zip"):
    withContext { (tempDir, store, userStore, sessionStore, passwordService, authService, _, _, app) =>
      val hash = passwordService.hash("password")
      userStore.add(User("admin", hash, Role.Admin, enabled = true, id = "u-admin"))
      val session = authService.authenticate("admin", "password").get

      val req = Request[IO](Method.GET, Uri.unsafeFromString("/debug/download-data"))
        .putHeaders(Header.Raw(CIString("Cookie"), s"session=${session.id}"))
      val res = app.run(req).unsafeRunSync()

      assertEquals(res.status, Status.Ok)
      assertEquals(res.headers.get(CIString("Content-Type")).map(_.head.value), Some("application/zip"))
      val disposition = res.headers.get(CIString("Content-Disposition")).map(_.head.value).getOrElse("")
      assert(disposition.matches("""attachment;\s*filename="ics205-\d{8}T\d{6}Z\.zip""""))
    }
