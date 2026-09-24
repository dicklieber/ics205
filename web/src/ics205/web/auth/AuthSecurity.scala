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

package ics205.web.auth

import cats.effect.IO
import ics205.auth.{AuthConfig, AuthenticatedUser, AuthenticationService, AuthorizationService, Permission}
import jakarta.inject.{Inject, Singleton}
import sttp.model.StatusCode
import sttp.model.headers.Cookie.SameSite
import sttp.model.headers.CookieValueWithMeta
import sttp.tapir.*
import sttp.tapir.server.PartialServerEndpoint

@Singleton
class AuthSecurity @Inject()(
  val authService: AuthenticationService,
  val config: AuthConfig
):

  def sessionCookieMeta(sessionId: String): CookieValueWithMeta =
    CookieValueWithMeta.unsafeApply(
      value = sessionId,
      expires = Some(java.time.Instant.now().plus(config.sessionLifetime)),
      maxAge = Some(config.sessionLifetime.toSeconds),
      domain = None,
      path = Some("/"),
      secure = config.secureCookie,
      httpOnly = true,
      sameSite = Some(SameSite.Lax)
    )

  def expiredCookieMeta(): CookieValueWithMeta =
    CookieValueWithMeta.unsafeApply(
      value = "",
      expires = Some(java.time.Instant.ofEpochMilli(0)),
      maxAge = Some(0),
      domain = None,
      path = Some("/"),
      secure = config.secureCookie,
      httpOnly = true,
      sameSite = Some(SameSite.Lax)
    )

  val secureEndpoint: PartialServerEndpoint[Option[String], AuthenticatedUser, Unit, (StatusCode, String), Unit, Any, IO] =
    endpoint
      .securityIn(auth.apiKey(cookie[Option[String]](config.cookieName)))
      .errorOut(statusCode.and(stringBody))
      .serverSecurityLogic[AuthenticatedUser, IO] {
        case Some(sessionId) =>
          IO.pure(authService.authenticateSession(sessionId).left.map(e => (StatusCode(e.status), e.message)))
        case None =>
          IO.pure(Left((StatusCode.Unauthorized, "Authentication required: missing session cookie")))
      }

  def authorizedEndpoint(permission: Permission): PartialServerEndpoint[Option[String], AuthenticatedUser, Unit, (StatusCode, String), Unit, Any, IO] =
    endpoint
      .securityIn(auth.apiKey(cookie[Option[String]](config.cookieName)))
      .errorOut(statusCode.and(stringBody))
      .serverSecurityLogic[AuthenticatedUser, IO] {
        case Some(sessionId) =>
          IO.pure {
            for
              user <- authService.authenticateSession(sessionId)
              authorized <- AuthorizationService.authorize(user, permission)
            yield authorized
          }.map(_.left.map(e => (StatusCode(e.status), e.message)))
        case None =>
          IO.pure(Left((StatusCode.Unauthorized, "Authentication required: missing session cookie")))
      }
