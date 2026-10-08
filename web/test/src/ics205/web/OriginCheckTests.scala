package ics205.web

import cats.data.Kleisli
import cats.effect.IO
import cats.effect.unsafe.implicits.global
import org.http4s.{Header, Method, Request, Response, Status, Uri}
import org.typelevel.ci.CIString

class OriginCheckTests extends munit.FunSuite:
  private val app = OriginCheck(Kleisli(_ => IO.pure(Response[IO](Status.Ok))))

  private def status(method: Method, headers: (String, String)*): Status =
    val request = headers.foldLeft(Request[IO](method, Uri.unsafeFromString("/events/delete"))) {
      case (req, (name, value)) => req.putHeaders(Header.Raw(CIString(name), value))
    }
    app.run(request).unsafeRunSync().status

  test("POST from the app's own origin is allowed"):
    assertEquals(status(Method.POST, "Host" -> "localhost:8080", "Origin" -> "http://localhost:8080"), Status.Ok)
    assertEquals(status(Method.POST, "Host" -> "ics205.example.org", "Origin" -> "https://ICS205.example.org"), Status.Ok)

  test("POST from another port on the same host is rejected"):
    assertEquals(status(Method.POST, "Host" -> "localhost:8080", "Origin" -> "http://localhost:3000"), Status.Forbidden)

  test("POST from another site or an opaque origin is rejected"):
    assertEquals(status(Method.POST, "Host" -> "localhost:8080", "Origin" -> "https://evil.example"), Status.Forbidden)
    assertEquals(status(Method.POST, "Host" -> "localhost:8080", "Origin" -> "null"), Status.Forbidden)

  test("Origin matching X-Forwarded-Host is allowed behind a reverse proxy"):
    assertEquals(status(Method.POST, "Host" -> "127.0.0.1:8080", "X-Forwarded-Host" -> "ics205.example.org",
      "Origin" -> "https://ics205.example.org"), Status.Ok)

  test("POST without Origin uses Sec-Fetch-Site when a browser sends it"):
    assertEquals(status(Method.POST, "Host" -> "localhost:8080", "Sec-Fetch-Site" -> "same-origin"), Status.Ok)
    assertEquals(status(Method.POST, "Host" -> "localhost:8080", "Sec-Fetch-Site" -> "same-site"), Status.Forbidden)
    assertEquals(status(Method.POST, "Host" -> "localhost:8080", "Sec-Fetch-Site" -> "cross-site"), Status.Forbidden)

  test("non-browser POST with neither header is allowed"):
    assertEquals(status(Method.POST), Status.Ok)

  test("safe methods are never checked"):
    assertEquals(status(Method.GET, "Host" -> "localhost:8080", "Origin" -> "https://evil.example"), Status.Ok)
