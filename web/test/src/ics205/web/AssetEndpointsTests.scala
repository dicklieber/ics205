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
import sttp.tapir.server.http4s.Http4sServerInterpreter

class AssetEndpointsTests extends munit.FunSuite:
  private val app = Http4sServerInterpreter[IO]().toRoutes(new AssetEndpoints().endpoints).orNotFound

  test("serves compiled admin.css with expected rules") {
    val req = Request[IO](Method.GET, Uri.unsafeFromString("/css/admin.css"))
    val resp = app.run(req).unsafeRunSync()
    assertEquals(resp.status, Status.Ok)
    val body = resp.as[String].unsafeRunSync()
    assert(body.contains(".admin-container"))
    assert(body.contains(".btn-primary"))
    assert(body.contains("table.users-table"))
    assert(body.contains(".alert-success"))
  }

  test("serves compiled ics205.css with expected rules") {
    val req = Request[IO](Method.GET, Uri.unsafeFromString("/css/ics205.css"))
    val resp = app.run(req).unsafeRunSync()
    assertEquals(resp.status, Status.Ok)
    val body = resp.as[String].unsafeRunSync()
    assert(body.contains(".sheet"))
    assert(body.contains(".channels"))
    assert(body.contains(".prepared-by"))
  }

  test("serves compiled ics205-editor.css with expected rules") {
    val req = Request[IO](Method.GET, Uri.unsafeFromString("/css/ics205-editor.css"))
    val resp = app.run(req).unsafeRunSync()
    assertEquals(resp.status, Status.Ok)
    val body = resp.as[String].unsafeRunSync()
    assert(body.contains(".toolbar"))
    assert(body.contains(".channel-numbers-dialog"))
    assert(body.contains(".ctcss-controls"))
    assert(body.contains(".btn-primary"))
    assert(body.contains(".alert-success"))
  }

  test("serves compiled navbar.css with expected rules") {
    val req = Request[IO](Method.GET, Uri.unsafeFromString("/css/navbar.css"))
    val resp = app.run(req).unsafeRunSync()
    assertEquals(resp.status, Status.Ok)
    val body = resp.as[String].unsafeRunSync()
    assert(body.contains(".navbar"))
    assert(body.contains(".about-dialog"))
    assert(body.contains(".navbar-brand"))
  }

  test("serves compiled radio.css with expected rules") {
    val req = Request[IO](Method.GET, Uri.unsafeFromString("/css/radio.css"))
    val resp = app.run(req).unsafeRunSync()
    assertEquals(resp.status, Status.Ok)
    val body = resp.as[String].unsafeRunSync()
    assert(body.contains(".radio-page"))
    assert(body.contains(".radio-details"))
    assert(body.contains(".radio-table-scroll"))
  }
