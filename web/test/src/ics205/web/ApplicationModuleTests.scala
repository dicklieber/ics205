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

import com.google.inject.Guice
import cats.effect.IO
import cats.effect.unsafe.implicits.global
import ics205.store.Ics205Store
import jakarta.inject.Inject
import org.http4s.{Method, Request, Status, Uri}
import sttp.tapir.*
import sttp.tapir.server.ServerEndpoint

class ApplicationModuleTests extends munit.FunSuite:
  test("endpoint groups receive the injector's singleton store"):
    val injector = Guice.createInjector(new ApplicationModule)
    val store = injector.getInstance(classOf[Ics205Store])
    val first = injector.getInstance(classOf[IndexEndpoints])
    val second = injector.getInstance(classOf[IndexEndpoints])

    assert(first.store eq store)
    assert(second.store eq store)
    assert(injector.getInstance(classOf[Ics205Store]) eq store)

  test("independent injectors have independent stores"):
    val first = Guice.createInjector(new ApplicationModule)
    val second = Guice.createInjector(new ApplicationModule)

    assert(!(first.getInstance(classOf[Ics205Store]) eq
      second.getInstance(classOf[Ics205Store])))

  test("discovered endpoint groups are served by the web application"):
    val injector = Guice.createInjector(new ApplicationModule)
    val app = injector.getInstance(classOf[WebApplication]).httpApp
    val userStore = injector.getInstance(classOf[ics205.store.UserStore])
    val sessionStore = injector.getInstance(classOf[ics205.store.SessionStore])
    userStore.add(ics205.auth.User("test-id", "admin", "hash", ics205.auth.RolePermissions.Admin, enabled = true))
    val session = sessionStore.create("test-id")

    // Unauthenticated connection to / redirects to /login
    val unauthed = app.run(Request[IO](Method.GET, Uri.unsafeFromString("/"))).unsafeRunSync()
    assertEquals(unauthed.status, Status.SeeOther)
    assertEquals(unauthed.headers.get(org.typelevel.ci.CIString("Location")).map(_.head.value), Some("/login"))

    val responses = (for
      index <- app.run(Request[IO](Method.GET, Uri.unsafeFromString("/"))
        .putHeaders(org.http4s.Header.Raw(org.typelevel.ci.CIString("Cookie"), s"session=${session.id}")))
      indexBody <- index.as[String]
      metrics <- app.run(Request[IO](Method.GET, Uri.unsafeFromString("/metrics")))
      discovered <- app.run(Request[IO](Method.GET, Uri.unsafeFromString("/test-discovery")))
      discoveredBody <- discovered.as[String]
      missing <- app.run(Request[IO](Method.GET, Uri.unsafeFromString("/missing")))
    yield (index, indexBody, metrics, discovered, discoveredBody, missing)).unsafeRunSync()

    val (index, indexBody, metrics, discovered, discoveredBody, missing) = responses
    assertEquals(index.status, Status.Ok)
    assert(indexBody.contains("Incident Radio Communications Plan"))
    assert(!indexBody.contains("<style"))
    Seq("/css/ics205.css", "/css/ics205-editor.css").foreach { path =>
      assert(indexBody.contains(s"""href="$path""""))
      val response = app.run(Request[IO](Method.GET, Uri.unsafeFromString(path))).unsafeRunSync()
      assertEquals(response.status, Status.Ok)
      assert(response.headers.headers.exists(h =>
        h.name.toString.equalsIgnoreCase("Content-Type") && h.value.startsWith("text/css")
      ))
      assert(response.as[String].unsafeRunSync().contains("@media print"))
    }
    assertEquals(metrics.status, Status.Ok)
    assert(metrics.headers.headers.exists(h =>
      h.name.toString.equalsIgnoreCase("Content-Type") &&
        h.value.contains("text/plain") && h.value.contains("version=0.0.4")
    ))
    assertEquals(discovered.status, Status.Ok)
    assertEquals(discoveredBody, "injected")
    assertEquals(missing.status, Status.NotFound)

// This test-only group has no explicit Guice binding or server registration.
class DiscoveryTestEndpoints @Inject() (store: Ics205Store) extends ApiEndpoints:
  override val endpoints: List[ServerEndpoint[Any, IO]] = List(
    endpoint.get.in("test-discovery").out(stringBody)
      .serverLogicSuccess[IO](_ => IO.pure(if store != null then "injected" else "missing"))
  )
