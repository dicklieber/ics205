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
import cats.effect.{Deferred, IO}
import cats.effect.unsafe.implicits.global
import fs2.Stream
import io.dropwizard.metrics5.MetricRegistry
import ics205.metrics.ApplicationMetrics
import org.http4s.{HttpApp, Request, Response, Status}

class HttpTransactionMetricsTests extends munit.FunSuite:
  test("records each transaction only after its response body completes, including 404s"):
    val registry = new MetricRegistry
    val app = HttpTransactionMetrics(
      Kleisli[IO, Request[IO], Response[IO]](_ => IO.pure(Response[IO](Status.NotFound))),
      new ApplicationMetrics(registry)
    )
    val timer = registry.timer("http.transactions")
    (for
      response <- app(Request[IO]())
      _ <- IO(assertEquals(timer.getCount, 0L))
      _ <- response.body.compile.drain
      _ <- IO(assertEquals(timer.getCount, 1L))
      second <- app(Request[IO]())
      _ <- second.body.compile.drain
    yield ()).unsafeRunSync()
    assertEquals(timer.getCount, 2L)
    assert(timer.getSnapshot.getMax > 0L)
    assert(new ApplicationMetrics(registry).renderPrometheus.contains("ics205_http_transactions_seconds_count 2.0"))

  test("records handler and response-body failures"):
    val failure = new RuntimeException("request failed")
    val handlers: List[HttpApp[IO]] = List(
      Kleisli(_ => IO.raiseError[Response[IO]](failure)),
      Kleisli(_ => IO.pure(Response[IO]().withBodyStream(Stream.raiseError[IO](failure))))
    )
    handlers.foreach { handler =>
      val registry = new MetricRegistry
      val result = HttpTransactionMetrics(handler, new ApplicationMetrics(registry))(Request[IO]())
        .flatMap(_.body.compile.drain).attempt.unsafeRunSync()
      assertEquals(result, Left(failure))
      assertEquals(registry.timer("http.transactions").getCount, 1L)
    }

  test("records cancellation during handler execution and body streaming"):
    List(false, true).foreach { duringBody =>
      val registry = new MetricRegistry
      (for
        started <- Deferred[IO, Unit]
        blocked = started.complete(()).void *> IO.never[Unit]
        handler: HttpApp[IO] = Kleisli(_ =>
          if duringBody then
            IO.pure(Response[IO]().withBodyStream(Stream.eval(blocked).drain))
          else blocked.as(Response[IO]())
        )
        fiber <- HttpTransactionMetrics(handler, new ApplicationMetrics(registry))(Request[IO]())
          .flatMap(_.body.compile.drain).start
        _ <- started.get
        _ <- fiber.cancel
      yield ()).unsafeRunSync()
      assertEquals(registry.timer("http.transactions").getCount, 1L)
    }
