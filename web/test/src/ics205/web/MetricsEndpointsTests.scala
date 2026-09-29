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
import org.http4s.{Method, Request, Status, Uri}
import org.typelevel.ci.CIString
import sttp.tapir.server.http4s.Http4sServerInterpreter

class MetricsEndpointsTests extends munit.FunSuite:

  test("GET /metrics returns Prometheus formatted metrics"):
    val endpoints = new MetricsEndpoints()
    val httpApp = Http4sServerInterpreter[IO]().toRoutes(endpoints.endpoints).orNotFound

    val req = Request[IO](Method.GET, Uri.unsafeFromString("/metrics"))
    val (res, body) = (for
      r <- httpApp.run(req)
      b <- r.as[String]
    yield (r, b)).unsafeRunSync()

    assertEquals(res.status, Status.Ok)
    assertEquals(
      res.headers.get(CIString("Content-Type")).map(_.head.value),
      Some("text/plain; version=0.0.4; charset=utf-8")
    )
    assert(body.isInstanceOf[String])
