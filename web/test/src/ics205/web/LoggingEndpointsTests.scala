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
import ics205.store.{InMemJsonSessionStore, SessionStore, UserStore}
import ics205.util.{FileHelper, LoggingStore}
import ics205.web.auth.AuthSecurity
import io.circe.parser.parse
import org.http4s.{Header, Method, Request, Status, Uri, UrlForm}
import org.typelevel.ci.CIString
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.http4s.Http4sServerInterpreter

class LoggingEndpointsTests extends munit.FunSuite:

  private def withContext(
    test: (os.Path, FileHelper, UserStore, SessionStore, PasswordService, AuthenticationService, AuthSecurity, LoggingStore, LoggingEndpoints, org.http4s.HttpApp[IO]) => Unit
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
      val loggingStore = new LoggingStore(helper)
      val loggingEndpoints = new LoggingEndpoints(loggingStore, security)

      val allServerEndpoints: List[ServerEndpoint[Any, IO]] = loggingEndpoints.endpoints
      val httpApp = Http4sServerInterpreter[IO]().toRoutes(allServerEndpoints).orNotFound
      test(tempDir, helper, userStore, sessionStore, passwordService, authService, security, loggingStore, loggingEndpoints, httpApp)
    finally
      os.remove.all(tempDir)

  test("GET /debug/logging without session cookie returns 401 Unauthorized"):
    withContext { (_, _, _, _, _, _, _, _, _, app) =>
      val req = Request[IO](Method.GET, Uri.unsafeFromString("/debug/logging"))
      val res = app.run(req).unsafeRunSync()
      assertEquals(res.status, Status.Unauthorized)
    }

  test("GET /debug/logging with user lacking Debug permission returns 403 Forbidden"):
    withContext { (_, _, userStore, _, passwordService, authService, _, _, _, app) =>
      val hash = passwordService.hash("password")
      userStore.add(User("viewer1", hash, Role.Viewer, enabled = true, id = "u1"))
      val session = authService.authenticate("viewer1", "password").get

      val req = Request[IO](Method.GET, Uri.unsafeFromString("/debug/logging"))
        .putHeaders(Header.Raw(CIString("Cookie"), s"session=${session.id}"))
      val res = app.run(req).unsafeRunSync()
      assertEquals(res.status, Status.Forbidden)
    }

  test("GET /debug/logging with user having Debug permission renders HTML with discovered loggers and dropdown"):
    withContext { (_, _, userStore, _, passwordService, authService, _, _, _, app) =>
      val hash = passwordService.hash("password")
      userStore.add(User("admin", hash, Role.Admin, enabled = true, id = "u-admin"))
      val session = authService.authenticate("admin", "password").get

      val req = Request[IO](Method.GET, Uri.unsafeFromString("/debug/logging"))
        .putHeaders(Header.Raw(CIString("Cookie"), s"session=${session.id}"))
      val res = app.run(req).unsafeRunSync()
      assertEquals(res.status, Status.Ok)

      val body = res.as[String].unsafeRunSync()
      assert(body.contains("Logging Configuration"))
      assert(body.contains("ics205.util.FileHelper"))
      assert(body.contains("DEBUG"))
      assert(body.contains("INFO"))
      assert(body.contains("<select"))
    }

  test("POST /debug/logging sets log level and persists in Locus.config"):
    withContext { (tempDir, _, userStore, _, passwordService, authService, _, loggingStore, _, app) =>
      val hash = passwordService.hash("password")
      userStore.add(User("admin", hash, Role.Admin, enabled = true, id = "u-admin"))
      val session = authService.authenticate("admin", "password").get

      val targetLogger = "ics205.util.FileHelper"
      val form = UrlForm("logger" -> targetLogger, "level" -> "TRACE")
      val req = Request[IO](Method.POST, Uri.unsafeFromString("/debug/logging"))
        .putHeaders(Header.Raw(CIString("Cookie"), s"session=${session.id}"))
        .withEntity(form)

      val res = app.run(req).unsafeRunSync()
      assertEquals(res.status, Status.SeeOther)
      val location = res.headers.get(CIString("Location")).map(_.head.value).getOrElse("")
      assert(location.contains("/debug/logging"))

      // Check in store and effective level
      assertEquals(loggingStore.getLevel(targetLogger), Some("TRACE"))
      assertEquals(loggingStore.getEffectiveLevel(targetLogger), "TRACE")

      // Check persisted file in Locus.config
      val configFile = tempDir / "config" / "loggers.json"
      assert(os.exists(configFile), s"Config file $configFile should exist")
      val content = os.read(configFile)
      assert(content.contains(targetLogger))
      assert(content.contains("TRACE"))
    }

  test("POST /debug/logging with action=reset removes level from config"):
    withContext { (tempDir, _, userStore, _, passwordService, authService, _, loggingStore, _, app) =>
      val hash = passwordService.hash("password")
      userStore.add(User("admin", hash, Role.Admin, enabled = true, id = "u-admin"))
      val session = authService.authenticate("admin", "password").get

      val targetLogger = "ics205.util.FileHelper"
      loggingStore.setLevel(targetLogger, "DEBUG")
      assertEquals(loggingStore.getLevel(targetLogger), Some("DEBUG"))

      val form = UrlForm("logger" -> targetLogger, "level" -> "DEBUG", "action" -> "reset")
      val req = Request[IO](Method.POST, Uri.unsafeFromString("/debug/logging"))
        .putHeaders(Header.Raw(CIString("Cookie"), s"session=${session.id}"))
        .withEntity(form)

      val res = app.run(req).unsafeRunSync()
      assertEquals(res.status, Status.SeeOther)

      assertEquals(loggingStore.getLevel(targetLogger), None)
    }

  test("GET and POST /api/debug/logging work via JSON"):
    withContext { (tempDir, _, userStore, _, passwordService, authService, _, loggingStore, _, app) =>
      val hash = passwordService.hash("password")
      userStore.add(User("admin", hash, Role.Admin, enabled = true, id = "u-admin"))
      val session = authService.authenticate("admin", "password").get

      // GET API
      val getReq = Request[IO](Method.GET, Uri.unsafeFromString("/api/debug/logging"))
        .putHeaders(Header.Raw(CIString("Cookie"), s"session=${session.id}"))
      val getRes = app.run(getReq).unsafeRunSync()
      assertEquals(getRes.status, Status.Ok)

      val getBody = getRes.as[String].unsafeRunSync()
      val json = parse(getBody).toOption.get
      assert(json.hcursor.downField("discovered").as[Seq[String]].toOption.get.contains("ics205.util.FileHelper"))

      // POST API
      val postReq = Request[IO](Method.POST, Uri.unsafeFromString("/api/debug/logging"))
        .putHeaders(
          Header.Raw(CIString("Cookie"), s"session=${session.id}"),
          Header.Raw(CIString("Content-Type"), "application/json")
        )
        .withEntity("""{"logger": "ics205.util.FileHelper", "level": "WARN"}""")
      val postRes = app.run(postReq).unsafeRunSync()
      assertEquals(postRes.status, Status.Ok)

      assertEquals(loggingStore.getLevel("ics205.util.FileHelper"), Some("WARN"))
    }

  test("GET /debug/logging/yaml requires Debug permission and renders Log4j2 YAML Configuration"):
    withContext { (_, _, userStore, _, passwordService, authService, _, _, _, app) =>
      // Unauthorized
      val unauthReq = Request[IO](Method.GET, Uri.unsafeFromString("/debug/logging/yaml"))
      assertEquals(app.run(unauthReq).unsafeRunSync().status, Status.Unauthorized)

      // Forbidden (viewer)
      val hash = passwordService.hash("password")
      userStore.add(User("viewer1", hash, Role.Viewer, enabled = true, id = "u-view"))
      val viewSession = authService.authenticate("viewer1", "password").get
      val viewReq = Request[IO](Method.GET, Uri.unsafeFromString("/debug/logging/yaml"))
        .putHeaders(Header.Raw(CIString("Cookie"), s"session=${viewSession.id}"))
      assertEquals(app.run(viewReq).unsafeRunSync().status, Status.Forbidden)

      // Admin
      userStore.add(User("admin", hash, Role.Admin, enabled = true, id = "u-admin"))
      val adminSession = authService.authenticate("admin", "password").get
      val adminReq = Request[IO](Method.GET, Uri.unsafeFromString("/debug/logging/yaml"))
        .putHeaders(Header.Raw(CIString("Cookie"), s"session=${adminSession.id}"))
      val res = app.run(adminReq).unsafeRunSync()
      assertEquals(res.status, Status.Ok)
      val body = res.as[String].unsafeRunSync()
      assert(body.contains("Log4j2 YAML Configuration"))
      assert(body.contains("Configuration:"))
      assert(body.contains("Appenders:"))
      assert(body.contains("RollingFile:"))
      assert(body.contains("ics205.log"))
      assert(body.contains("ics205.exporter.RadioExportDefinitions"))
      assert(body.contains("Copy YAML"))
    }

  test("GET /debug/logging.yaml and /api/debug/logging/yaml return raw YAML text"):
    withContext { (_, _, userStore, _, passwordService, authService, _, loggingStore, _, app) =>
      val hash = passwordService.hash("password")
      userStore.add(User("admin", hash, Role.Admin, enabled = true, id = "u-admin"))
      val session = authService.authenticate("admin", "password").get

      loggingStore.setLevel("ics205.util.FileHelper", "DEBUG")

      // Test /debug/logging.yaml
      val req1 = Request[IO](Method.GET, Uri.unsafeFromString("/debug/logging.yaml"))
        .putHeaders(Header.Raw(CIString("Cookie"), s"session=${session.id}"))
      val res1 = app.run(req1).unsafeRunSync()
      assertEquals(res1.status, Status.Ok)
      val body1 = res1.as[String].unsafeRunSync()
      assert(body1.contains("Configuration:"))
      assert(body1.contains("ics205.util.FileHelper"))
      assert(body1.contains("DEBUG"))

      // Test /api/debug/logging/yaml
      val req2 = Request[IO](Method.GET, Uri.unsafeFromString("/api/debug/logging/yaml"))
        .putHeaders(Header.Raw(CIString("Cookie"), s"session=${session.id}"))
      val res2 = app.run(req2).unsafeRunSync()
      assertEquals(res2.status, Status.Ok)
      val body2 = res2.as[String].unsafeRunSync()
      assert(body2.contains("Configuration:"))
      assert(body2.contains("ics205.util.FileHelper"))
    }
