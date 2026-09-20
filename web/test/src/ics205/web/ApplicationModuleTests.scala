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
    val responses = (for
      index <- app.run(Request[IO](Method.GET, Uri.unsafeFromString("/")))
      indexBody <- index.as[String]
      metrics <- app.run(Request[IO](Method.GET, Uri.unsafeFromString("/metrics")))
      discovered <- app.run(Request[IO](Method.GET, Uri.unsafeFromString("/test-discovery")))
      discoveredBody <- discovered.as[String]
      missing <- app.run(Request[IO](Method.GET, Uri.unsafeFromString("/missing")))
    yield (index, indexBody, metrics, discovered, discoveredBody, missing)).unsafeRunSync()

    val (index, indexBody, metrics, discovered, discoveredBody, missing) = responses
    assertEquals(index.status, Status.Ok)
    assert(indexBody.contains("Incident Radio Communications Plan"))
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
