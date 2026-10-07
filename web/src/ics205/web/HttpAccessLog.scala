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
import cats.effect.{IO, Outcome, Ref}
import cats.syntax.all.*
import ics205.auth.{AuthConfig, AuthenticationService}
import org.http4s.{HttpApp, Request, Response, Status}
import org.slf4j.{Logger, LoggerFactory}
import org.typelevel.ci.CIString

import java.nio.charset.StandardCharsets
import java.time.format.DateTimeFormatter
import java.time.{Instant, ZoneId}
import java.util.{Base64, Locale}

/**
 * Provides functionality for logging HTTP access logs in the Common Log Format (CLF).
 * The object contains utilities to log client requests, extract relevant information,
 * and format it according to standard conventions.
 *
 * It allows integration of request logging into an HTTP application by instrumenting
 * responses and emitting detailed logs. It also supports extracting client information
 * such as the IP address and authenticated user from incoming HTTP requests.
 *
 * The main traits of this object include:
 * - Formatting logs according to the Common Log Format (CLF).
 * - Extracting client IP from request headers.
 * - Extracting authenticated user information based on basic authorization or session cookies.
 * - Instrumenting the application to generate request logs with meta-information such as request method,
 * URI, status codes, and response size.
 *
 * Logs can be customized with the following:
 * - Custom logging backend (default is SLF4J Logger).
 * - Timezone to format timestamps in logs.
 * - Authentication service and session cookie configuration for extracting user authentication details.
 *
 * Thread safety and immutability properties must be upheld while using this object as part
 * of an HTTP application. */
object HttpAccessLog:
  val defaultLogger: Logger = LoggerFactory.getLogger(classOf[HttpAccessLog])

  private val clfDateFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("dd/MMM/yyyy:HH:mm:ss Z", Locale.US).withZone(ZoneId.systemDefault())

  def formatClf(
    clientIp: String,
    ident: String,
    authUser: String,
    timeStr: String,
    method: String,
    uri: String,
    httpVersion: String,
    statusCode: Int,
    bytes: Long
  ): String =
    val ip = if clientIp.trim.isEmpty then "-" else clientIp.trim
    val id = if ident.trim.isEmpty then "-" else ident.trim
    val user = if authUser.trim.isEmpty then "-" else authUser.trim
    val bytesStr = if bytes > 0 then bytes.toString else "-"
    s"""$ip $id $user [$timeStr] "$method $uri $httpVersion" $statusCode $bytesStr"""

  def extractClientIp(request: Request[IO]): String =
    request.headers.get(CIString("X-Forwarded-For"))
      .flatMap(_.head.value.split(',').headOption.map(_.trim))
      .filter(_.nonEmpty)
      .orElse(request.headers.get(CIString("X-Real-IP")).map(_.head.value.trim).filter(_.nonEmpty))
      .orElse(request.from.map(_.toString))
      .orElse(request.remote.map(_.toString))
      .getOrElse("-")

  def extractAuthUser(
    request: Request[IO],
    authService: Option[AuthenticationService],
    cookieName: String
  ): String =
    // 1. Check HTTP Basic Authentication
    val basicAuthUser = request.headers.get(CIString("Authorization"))
      .map(_.head.value.trim)
      .filter(_.startsWith("Basic "))
      .flatMap { raw =>
        try
          val token = raw.stripPrefix("Basic ").trim
          val decoded = new String(Base64.getDecoder.decode(token), StandardCharsets.UTF_8)
          decoded.split(':').headOption.filter(_.nonEmpty)
        catch
          case _: Exception => None
      }

    // 2. Check Session Cookie via AuthenticationService
    val cookieAuthUser = basicAuthUser.orElse {
      for
        auth <- authService
        cookieHeader <- request.headers.get(CIString("Cookie"))
        cookieStr = cookieHeader.head.value
        sessionId <- cookieStr.split(';')
          .map(_.trim)
          .find(_.startsWith(s"$cookieName="))
          .map(_.stripPrefix(s"$cookieName=").trim)
          .filter(_.nonEmpty)
        user <- auth.authenticateSession(sessionId).toOption
      yield user.username
    }

    cookieAuthUser.getOrElse("-")

  def apply(
    app: HttpApp[IO],
    authService: Option[AuthenticationService] = None,
    cookieName: String = AuthConfig.default.cookieName,
    logger: Logger = defaultLogger,
    logSink: Option[String => Unit] = None,
    zoneId: ZoneId = ZoneId.systemDefault()
  ): HttpApp[IO] =
    val dateFormatter = DateTimeFormatter.ofPattern("dd/MMM/yyyy:HH:mm:ss Z", Locale.US).withZone(zoneId)

    Kleisli { request =>
      val requestTime = Instant.now()
      val timeStr = dateFormatter.format(requestTime)
      val clientIp = extractClientIp(request)
      val authUser = extractAuthUser(request, authService, cookieName)
      val method = request.method.name
      val uri = request.uri.renderString
      val httpVersion = request.httpVersion.renderString

      def writeLog(statusCode: Int, bytes: Long): IO[Unit] = IO {
        val line = formatClf(
          clientIp = clientIp,
          ident = "-",
          authUser = authUser,
          timeStr = timeStr,
          method = method,
          uri = uri,
          httpVersion = httpVersion,
          statusCode = statusCode,
          bytes = bytes
        )
        logSink match
          case Some(sink) => sink(line)
          case None => logger.info(line)
      }

      for
        byteCounter <- Ref[IO].of(0L)
        responseOutcome <- app(request).attempt
        response <- responseOutcome match
          case Right(resp) =>
            val instrumentedBody = resp.body.chunks.evalTap { chunk =>
              byteCounter.update(_ + chunk.size)
            }.unchunks

            val finalizedBody = instrumentedBody.onFinalizeCase { _ =>
              byteCounter.get.flatMap { countedBytes =>
                val totalBytes = if countedBytes > 0 then countedBytes else resp.contentLength.getOrElse(0L)
                writeLog(resp.status.code, totalBytes)
              }
            }
            IO.pure(resp.withBodyStream(finalizedBody))

          case Left(ex) =>
            writeLog(Status.InternalServerError.code, 0L) *> IO.raiseError(ex)
      yield response
    }

class HttpAccessLog
