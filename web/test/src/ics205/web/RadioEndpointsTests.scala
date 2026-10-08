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
import ics205.model.*
import ics205.store.*
import ics205.util.FileHelper
import org.http4s.{Header, Method, Request, Status, Uri}
import org.typelevel.ci.CIString
import sttp.tapir.server.http4s.Http4sServerInterpreter

class RadioEndpointsTests extends munit.FunSuite:
  test("Radio requires a session and shows the latest saved plan with its stylesheet"):
    val directory = os.temp.dir()
    try
      val helper = new FileHelper(directory)
      val config = AuthConfig()
      val store = new Ics205Store(helper)
      val users = new UserStore(helper, config)
      val sessions = new InMemJsonSessionStore(helper, config)
      val auth = new AuthenticationService(users, new ScalaPassPasswordService(), sessions)
      users.add(User("viewer", "unused", Role.User, enabled = true, id = "viewer"))
      val session = sessions.create("viewer")
      val app = Http4sServerInterpreter[IO]().toRoutes(
        new IndexEndpoints(store, auth, config).endpoints ++ new AssetEndpoints().endpoints).orNotFound
      val request = Request[IO](Method.GET, Uri.unsafeFromString("/radio"))
      Seq(request, request.putHeaders(Header.Raw(CIString("Cookie"), "session=invalid"))).foreach { req =>
        val response = app.run(req).unsafeRunSync()
        assertEquals(response.status, Status.Unauthorized)
      }
      val authenticated = request.putHeaders(Header.Raw(CIString("Cookie"), s"session=${session.id}"))
      Seq("First incident", "Updated incident").foreach { incident =>
        store.save(ics205.model.Ics205Event(ics205.model.Ics205(incidentName = incident, operationalPeriod = OperationalPeriod(), channels = Seq.empty)))
        val response = app.run(authenticated).unsafeRunSync()
        assertEquals(response.status, Status.Ok)
        val html = response.as[String].unsafeRunSync()
        assert(html.contains(incident))
        assert(html.contains("<h1>Radio</h1>"))
        assert(html.contains("/css/radio.css"))
      }
      val css = app.run(Request[IO](Method.GET, Uri.unsafeFromString("/css/radio.css"))).unsafeRunSync()
      assertEquals(css.status, Status.Ok)
      assert(css.as[String].unsafeRunSync().contains(".radio-table-scroll"))
      assert(Ics205Editor.render(store.ics205()).contains("href=\"/radio\""))
    finally os.remove.all(directory)
