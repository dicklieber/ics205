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
import com.google.inject.name.Names
import com.typesafe.config.{Config, ConfigFactory}
import cats.effect.IO
import cats.effect.unsafe.implicits.global
import ics205.BuildInfo
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

  test("ApplicationModule binds Config and individual config entries"):
    val config = ConfigFactory.parseString(
      """
        |test.str = "hello"
        |test.int = 42
        |test.long = 1234567890123
        |test.double = 3.14
        |test.bool = true
        |test.duration = "10s"
        |""".stripMargin
    )
    val injector = Guice.createInjector(new ApplicationModule(config))
    val injectedConfig = injector.getInstance(classOf[Config])
    assertEquals(injectedConfig, config)

    val strVal = injector.getInstance(com.google.inject.Key.get(classOf[String], Names.named("test.str")))
    assertEquals(strVal, "hello")

    val intVal = injector.getInstance(com.google.inject.Key.get(classOf[Int], Names.named("test.int")))
    assertEquals(intVal, 42)

    val longVal = injector.getInstance(com.google.inject.Key.get(classOf[Long], Names.named("test.long")))
    assertEquals(longVal, 1234567890123L)

    val doubleVal = injector.getInstance(com.google.inject.Key.get(classOf[Double], Names.named("test.double")))
    assertEquals(doubleVal, 3.14)

    val boolVal = injector.getInstance(com.google.inject.Key.get(classOf[Boolean], Names.named("test.bool")))
    assertEquals(boolVal, true)

    val durationVal = injector.getInstance(com.google.inject.Key.get(classOf[java.time.Duration], Names.named("test.duration")))
    assertEquals(durationVal, java.time.Duration.ofSeconds(10))

  test("discovered endpoint groups are served by the web application"):
    val injector = Guice.createInjector(new ApplicationModule)
    val app = injector.getInstance(classOf[WebApplication]).httpApp
    val store = injector.getInstance(classOf[Ics205Store])
    store.save(ics205.model.Ics205Event("Main Event", ics205.model.Ics205(incidentName = "Incident Radio Communications Plan", operationalPeriod = ics205.model.OperationalPeriod(), channels = Seq.empty)))
    val userStore = injector.getInstance(classOf[ics205.store.UserStore])
    val sessionStore = injector.getInstance(classOf[ics205.store.SessionStore])
    userStore.add(ics205.auth.User("admin", "hash", ics205.auth.Role.Admin, enabled = true, id = "test-id"))
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
      docs <- app.run(Request[IO](Method.GET, Uri.unsafeFromString("/docs")))
      docsIndex <- app.run(Request[IO](Method.GET, Uri.unsafeFromString("/docs/index.html")))
      docsIndexBody <- docsIndex.as[String]
      docsYaml <- app.run(Request[IO](Method.GET, Uri.unsafeFromString("/docs/docs.yaml")))
      discovered <- app.run(Request[IO](Method.GET, Uri.unsafeFromString("/test-discovery")))
      discoveredBody <- discovered.as[String]
      missing <- app.run(Request[IO](Method.GET, Uri.unsafeFromString("/missing")))
    yield (index, indexBody, metrics, docs, docsIndex, docsIndexBody, docsYaml, discovered, discoveredBody, missing)).unsafeRunSync()

    val (index, indexBody, metrics, docs, docsIndex, docsIndexBody, docsYaml, discovered, discoveredBody, missing) = responses
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
    assertEquals(docs.status, Status.PermanentRedirect)
    assertEquals(docsIndex.status, Status.Ok)
    assert(docsIndexBody.contains("swagger-ui"))
    assertEquals(docsYaml.status, Status.Ok)
    val yamlContent = docsYaml.as[String].unsafeRunSync()
    assert(yamlContent.contains("openapi:"))
    assert(yamlContent.contains(BuildInfo.appName))
    assertEquals(discovered.status, Status.Ok)
    assertEquals(discoveredBody, "injected")
    assertEquals(missing.status, Status.NotFound)

  test("WebApplication displayUri converts wildcard addresses to localhost"):
    val app = Guice.createInjector(new ApplicationModule).getInstance(classOf[WebApplication])
    val mockWildcardServer = new org.http4s.server.Server:
      def address: java.net.InetSocketAddress = new java.net.InetSocketAddress("0.0.0.0", 8080)
      def isSecure: Boolean = false
      override def baseUri: Uri = Uri.unsafeFromString("http://[::]:8080/")
    assertEquals(app.displayUri(mockWildcardServer), "http://localhost:8080/")

    val mockExplicitServer = new org.http4s.server.Server:
      def address: java.net.InetSocketAddress = new java.net.InetSocketAddress("192.168.1.50", 8080)
      def isSecure: Boolean = false
      override def baseUri: Uri = Uri.unsafeFromString("http://192.168.1.50:8080/")
    assertEquals(app.displayUri(mockExplicitServer), "http://192.168.1.50:8080/")

  test("WebApplication uses default port 8080 from reference.conf"):
    val injector = Guice.createInjector(new ApplicationModule)
    val app = injector.getInstance(classOf[WebApplication])
    assertEquals(app.port, 8080)

  test("WebApplication uses overridden port from config"):
    val config = ConfigFactory.parseString("port = 9090").withFallback(ConfigFactory.load()).resolve()
    val injector = Guice.createInjector(new ApplicationModule(config))
    val app = injector.getInstance(classOf[WebApplication])
    assertEquals(app.port, 9090)

  test("WebApplication uses overridden PORT environment variable or system property"):
    System.setProperty("PORT", "9191")
    try
      ConfigFactory.invalidateCaches()
      val injector = Guice.createInjector(new ApplicationModule)
      val app = injector.getInstance(classOf[WebApplication])
      assertEquals(app.port, 9191)
    finally
      System.clearProperty("PORT")
      ConfigFactory.invalidateCaches()

// This test-only group has no explicit Guice binding or server registration.
class DiscoveryTestEndpoints @Inject() (store: Ics205Store) extends ApiEndpoints:
  override val endpoints: List[ServerEndpoint[Any, IO]] = List(
    endpoint.get.in("test-discovery").out(stringBody)
      .serverLogicSuccess[IO](_ => IO.pure(if store != null then "injected" else "missing"))
  )
