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
import ics205.log.Ics205ActivityLogger
import ics205.model.*
import ics205.store.{Ics205Store, InMemJsonSessionStore, SessionStore, UserStore}
import ics205.util.FileHelper
import io.circe.parser.parse
import org.http4s.{Header, Method, Request, Status, Uri}
import org.typelevel.ci.CIString
import sttp.tapir.server.http4s.Http4sServerInterpreter

class ActivityEndpointsTests extends munit.FunSuite:

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

      val plan = Ics205(
        incidentName = "Flood Response",
        operationalPeriod = OperationalPeriod(),
        channels = Seq.empty
      )
      ics205Store.save(Ics205Event(plan))

      val activityEndpoints = new ActivityEndpoints(ics205Store, authService, config)
      val httpApp = Http4sServerInterpreter[IO]().toRoutes(activityEndpoints.endpoints).orNotFound

      test(tempDir, userStore, sessionStore, ics205Store, authService, httpApp)
    finally
      os.remove.all(tempDir)

  test("POST /import/log without session returns 401 Unauthorized"):
    withContext { (_, _, _, _, _, app) =>
      val body = """{"eventName":"Flood","incidentName":"Flood Response","channelCount":5,"fileName":"flood.json"}"""
      val req = Request[IO](Method.POST, Uri.unsafeFromString("/import/log"))
        .withEntity(body)
        .putHeaders(Header.Raw(CIString("Content-Type"), "application/json"))
      val res = app.run(req).unsafeRunSync()
      assertEquals(res.status, Status.Unauthorized)
    }

  test("POST /import/log with valid session logs import activity and returns 200"):
    withContext { (_, userStore, _, _, authService, app) =>
      val passwordService = new ScalaPassPasswordService()
      userStore.add(User("operator1", passwordService.hash("password"), Role.User, enabled = true, id = "u1"))
      val session = authService.authenticate("operator1", "password").get

      val body = """{"eventName":"Flood","incidentName":"Flood Response","channelCount":5,"fileName":"flood.json","format":"json"}"""
      val req = Request[IO](Method.POST, Uri.unsafeFromString("/import/log"))
        .withEntity(body)
        .putHeaders(
          Header.Raw(CIString("Cookie"), s"session=${session.id}"),
          Header.Raw(CIString("Content-Type"), "application/json")
        )
      val res = app.run(req).unsafeRunSync()
      assertEquals(res.status, Status.Ok)

      val resBody = res.as[String].unsafeRunSync()
      val parsed = parse(resBody).toOption.get
      assertEquals(parsed.hcursor.get[String]("status"), Right("ok"))
    }

  test("POST /export/log without session returns 401 Unauthorized"):
    withContext { (_, _, _, _, _, app) =>
      val body = """{"eventName":"Flood","format":"json","incidentName":"Flood Response","channelCount":5}"""
      val req = Request[IO](Method.POST, Uri.unsafeFromString("/export/log"))
        .withEntity(body)
        .putHeaders(Header.Raw(CIString("Content-Type"), "application/json"))
      val res = app.run(req).unsafeRunSync()
      assertEquals(res.status, Status.Unauthorized)
    }

  test("POST /export/log with valid session logs export activity and returns 200"):
    withContext { (_, userStore, _, _, authService, app) =>
      val passwordService = new ScalaPassPasswordService()
      userStore.add(User("operator2", passwordService.hash("password"), Role.User, enabled = true, id = "u2"))
      val session = authService.authenticate("operator2", "password").get

      val body = """{"eventName":"Flood","format":"json","incidentName":"Flood Response","channelCount":5}"""
      val req = Request[IO](Method.POST, Uri.unsafeFromString("/export/log"))
        .withEntity(body)
        .putHeaders(
          Header.Raw(CIString("Cookie"), s"session=${session.id}"),
          Header.Raw(CIString("Content-Type"), "application/json")
        )
      val res = app.run(req).unsafeRunSync()
      assertEquals(res.status, Status.Ok)

      val resBody = res.as[String].unsafeRunSync()
      val parsed = parse(resBody).toOption.get
      assertEquals(parsed.hcursor.get[String]("status"), Right("ok"))
    }
