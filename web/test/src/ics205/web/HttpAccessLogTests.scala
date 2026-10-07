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

import cats.data.Kleisli
import cats.effect.IO
import cats.effect.unsafe.implicits.global
import com.google.inject.Guice
import fs2.Stream
import ics205.auth.*
import ics205.store.{InMemJsonSessionStore, SessionStore, UserStore}
import ics205.util.FileHelper
import org.http4s.*
import org.http4s.headers.`Content-Length`
import org.slf4j.Logger
import org.typelevel.ci.CIString

import java.nio.charset.StandardCharsets
import java.time.{Instant, ZoneId}
import java.util.Base64
import scala.collection.mutable.ListBuffer

class HttpAccessLogTests extends munit.FunSuite:

  private val clfPattern = """^(\S+) (\S+) (\S+) \[([\w:/]+\s[+\-]\d{4})\] "(\S+)\s(\S+)\s(\S+)" (\d{3}) (\d+|-)$""".r

  test("formatClf produces valid Common Log Format line"):
    val formatted = HttpAccessLog.formatClf(
      clientIp = "127.0.0.1",
      ident = "-",
      authUser = "admin",
      timeStr = "07/Oct/2026:18:35:00 +0000",
      method = "GET",
      uri = "/events",
      httpVersion = "HTTP/1.1",
      statusCode = 200,
      bytes = 1024L
    )
    assertEquals(formatted, """127.0.0.1 - admin [07/Oct/2026:18:35:00 +0000] "GET /events HTTP/1.1" 200 1024""")
    assert(clfPattern.matches(formatted))

  test("formatClf handles empty or zero values properly with dashes"):
    val formatted = HttpAccessLog.formatClf(
      clientIp = "",
      ident = "",
      authUser = "",
      timeStr = "07/Oct/2026:18:35:00 +0000",
      method = "POST",
      uri = "/login",
      httpVersion = "HTTP/1.1",
      statusCode = 302,
      bytes = 0L
    )
    assertEquals(formatted, """- - - [07/Oct/2026:18:35:00 +0000] "POST /login HTTP/1.1" 302 -""")
    assert(clfPattern.matches(formatted))

  test("logs successful HTTP request in CLF format upon response body drain"):
    val messages = ListBuffer[String]()
    val responseBody = "Hello, ICS-205 access log!"
    val app = HttpAccessLog(
      Kleisli[IO, Request[IO], Response[IO]](_ =>
        IO.pure(Response[IO](Status.Ok).withEntity(responseBody))
      ),
      authService = None,
      logSink = Some(messages += _),
      zoneId = ZoneId.of("UTC")
    )

    val req = Request[IO](Method.GET, Uri.unsafeFromString("/api/v1/status"))
      .putHeaders(Header.Raw(CIString("X-Forwarded-For"), "192.168.1.100, 10.0.0.1"))

    val response = app(req).unsafeRunSync()
    assertEquals(messages.size, 0) // Not logged until body completes

    val bodyContent = response.as[String].unsafeRunSync()
    assertEquals(bodyContent, responseBody)
    assertEquals(messages.size, 1)

    val logLine = messages.head
    assert(clfPattern.matches(logLine), s"Expected CLF format match for: $logLine")
    assert(logLine.startsWith("192.168.1.100 - - ["), s"Unexpected prefix in: $logLine")
    assert(logLine.contains(""""GET /api/v1/status HTTP/1.1" 200 """), s"Unexpected request in: $logLine")
    assert(logLine.endsWith(s" ${responseBody.getBytes(StandardCharsets.UTF_8).length}"), s"Unexpected bytes in: $logLine")

  test("extracts client IP from X-Real-IP when X-Forwarded-For is absent"):
    val messages = ListBuffer[String]()
    val app = HttpAccessLog(
      Kleisli[IO, Request[IO], Response[IO]](_ => IO.pure(Response[IO](Status.NotFound))),
      authService = None,
      logSink = Some(messages += _)
    )

    val req = Request[IO](Method.GET, Uri.unsafeFromString("/missing"))
      .putHeaders(Header.Raw(CIString("X-Real-IP"), "10.20.30.40"))

    val response = app(req).unsafeRunSync()
    response.body.compile.drain.unsafeRunSync()

    assertEquals(messages.size, 1)
    val logLine = messages.head
    assert(logLine.startsWith("10.20.30.40 - - ["), s"Expected client IP 10.20.30.40 in: $logLine")
    assert(logLine.contains(""""GET /missing HTTP/1.1" 404 -"""), s"Expected 404 in: $logLine")

  test("extracts authenticated user from session cookie"):
    val messages = ListBuffer[String]()
    val tempDir = os.temp.dir()
    try
      val fileHelper = new FileHelper(tempDir)
      val userStore = new UserStore(fileHelper)
      val sessionStore = new InMemJsonSessionStore(fileHelper)
      val passwordService = new ScalaPassPasswordService
      val authService = new AuthenticationService(userStore, passwordService, sessionStore)

      val user = User("commander", passwordService.hash("secret123"), Role.Admin)
      userStore.add(user)
      val session = sessionStore.create(user.id)

      val app = HttpAccessLog(
        Kleisli[IO, Request[IO], Response[IO]](_ => IO.pure(Response[IO](Status.Ok).withEntity("Authorized payload"))),
        authService = Some(authService),
        cookieName = "session",
        logSink = Some(messages += _)
      )

      val req = Request[IO](Method.POST, Uri.unsafeFromString("/events/save"))
        .putHeaders(
          Header.Raw(CIString("X-Forwarded-For"), "172.16.0.5"),
          Header.Raw(CIString("Cookie"), s"session=${session.id}")
        )

      val response = app(req).unsafeRunSync()
      response.as[String].unsafeRunSync()

      assertEquals(messages.size, 1)
      val logLine = messages.head
      assert(logLine.startsWith("172.16.0.5 - commander ["), s"Expected authenticated user commander in: $logLine")
      assert(logLine.contains(""""POST /events/save HTTP/1.1" 200 """), s"Expected POST in: $logLine")
    finally
      os.remove.all(tempDir)

  test("extracts authenticated user from HTTP Basic Authorization header"):
    val messages = ListBuffer[String]()
    val app = HttpAccessLog(
      Kleisli[IO, Request[IO], Response[IO]](_ => IO.pure(Response[IO](Status.Ok))),
      authService = None,
      logSink = Some(messages += _)
    )

    val basicToken = Base64.getEncoder.encodeToString("radio_operator:password123".getBytes(StandardCharsets.UTF_8))
    val req = Request[IO](Method.GET, Uri.unsafeFromString("/api/export"))
      .putHeaders(
        Header.Raw(CIString("X-Forwarded-For"), "192.168.1.50"),
        Header.Raw(CIString("Authorization"), s"Basic $basicToken")
      )

    val response = app(req).unsafeRunSync()
    response.body.compile.drain.unsafeRunSync()

    assertEquals(messages.size, 1)
    val logLine = messages.head
    assert(logLine.startsWith("192.168.1.50 - radio_operator ["), s"Expected basic auth user in: $logLine")

  test("logs 500 internal server error when request handler fails"):
    val messages = ListBuffer[String]()
    val failure = new RuntimeException("Simulated catastrophic crash")
    val app = HttpAccessLog(
      Kleisli[IO, Request[IO], Response[IO]](_ => IO.raiseError(failure)),
      authService = None,
      logSink = Some(messages += _)
    )

    val req = Request[IO](Method.GET, Uri.unsafeFromString("/failing-route"))
      .putHeaders(Header.Raw(CIString("X-Forwarded-For"), "10.0.0.99"))

    val result = app(req).attempt.unsafeRunSync()
    assertEquals(result, Left(failure))
    assertEquals(messages.size, 1)

    val logLine = messages.head
    assert(logLine.startsWith("10.0.0.99 - - ["), s"Expected IP in: $logLine")
    assert(logLine.contains(""""GET /failing-route HTTP/1.1" 500 -"""), s"Expected 500 status in: $logLine")

  test("end-to-end WebApplication writes access logs into log directory via rolling appender"):
    val tempDir = os.temp.dir()
    try
      val fileHelper = new FileHelper(tempDir)
      val module = new ApplicationModule(customFileHelper = fileHelper)
      val injector = Guice.createInjector(module)
      val webApp = injector.getInstance(classOf[WebApplication])

      val logFile = fileHelper.logDirectory / "access.log"

      val req = Request[IO](Method.GET, Uri.unsafeFromString("/metrics"))
        .putHeaders(Header.Raw(CIString("X-Forwarded-For"), "127.0.0.1"))

      val response = webApp.httpApp.run(req).unsafeRunSync()
      val body = response.as[String].unsafeRunSync()
      assertEquals(response.status, Status.Ok)

      assert(os.exists(logFile), s"Access log file should exist at $logFile")
      val logContent = os.read(logFile)
      assert(logContent.contains(""""GET /metrics HTTP/1.1" 200 """), s"Access log file should contain metrics request, got:\n$logContent")
      assert(logContent.startsWith("127.0.0.1 - - ["), s"Access log line should begin with client IP, got:\n$logContent")
    finally
      os.remove.all(tempDir)
