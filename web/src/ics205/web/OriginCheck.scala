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
import com.typesafe.scalalogging.LazyLogging
import org.http4s.{HttpApp, Method, Request, Response, Status}
import org.typelevel.ci.CIString

import java.net.URI
import scala.util.Try

/** CSRF defence: rejects state-changing requests that a browser reports as coming from another origin.
  *
  * SameSite=Lax already keeps the session cookie off cross-site POSTs, but other ports on the same
  * host (e.g. localhost:3000 vs localhost:8080) count as the same site. Browsers send `Origin` on
  * every POST, so comparing it with `Host` closes that gap. Requests with no `Origin` and no
  * `Sec-Fetch-Site` (curl, scripts, tests) are not from a browser page and are allowed through.
  */
private[web] object OriginCheck extends LazyLogging:
  private val safeMethods = Set(Method.GET, Method.HEAD, Method.OPTIONS)

  def apply(app: HttpApp[IO]): HttpApp[IO] =
    Kleisli { request =>
      if safeMethods.contains(request.method) || isSameOrigin(request) then app(request)
      else
        IO(logger.warn(s"Rejected cross-origin ${request.method} ${request.uri.path} " +
          s"(Origin: ${header(request, "Origin").getOrElse("-")}, Host: ${header(request, "Host").getOrElse("-")})")) *>
          IO.pure(Response[IO](Status.Forbidden).withEntity("Cross-origin request rejected"))
    }

  private[web] def isSameOrigin(request: Request[IO]): Boolean =
    header(request, "Origin") match
      case Some(origin) =>
        val hosts = Seq(header(request, "Host"), header(request, "X-Forwarded-Host").flatMap(_.split(',').headOption))
          .flatten.map(_.trim.toLowerCase)
        originAuthority(origin).exists(hosts.contains)
      case None =>
        // No Origin: only browsers send Sec-Fetch-Site, and anything but same-origin/none is a page elsewhere.
        header(request, "Sec-Fetch-Site").forall(site => site == "same-origin" || site == "none")

  /** "http://localhost:8080" -> "localhost:8080"; "null" or unparsable -> None. */
  private def originAuthority(origin: String): Option[String] =
    Try(URI(origin.trim)).toOption
      .filter(uri => uri.getScheme != null && uri.getHost != null)
      .map(uri => if uri.getPort == -1 then uri.getHost.toLowerCase else s"${uri.getHost.toLowerCase}:${uri.getPort}")

  private def header(request: Request[IO], name: String): Option[String] =
    request.headers.get(CIString(name)).map(_.head.value)
