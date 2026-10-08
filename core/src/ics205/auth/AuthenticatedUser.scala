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

package ics205.auth

import ics205.model.EventId
import ics205.util.{Ids, UnauthorizedException}
import io.circe.Codec
import io.circe.derivation.{Configuration, ConfiguredCodec}

import java.time.Instant

case class AuthenticatedUser(user: User,
                             session: Session):
  def username: String = user.username
  def role: Role = user.role
  def id: UserId = user.id
  def currentIcs205: Option[EventId] = session.currentIcs205
  def check(permission: Permission):Unit =
    if !hasPermission(permission) then throw new UnauthorizedException()
  def roles: Set[String] = Set(session.toString.toLowerCase)
  def hasPermission(permission: Permission): Boolean = user.role.hasPermission(permission)

object AuthenticatedUser:
  def apply(user: User): AuthenticatedUser =
    val s = Session(userId = user.id, createdAt = Instant.now(), expiresAt = Instant.now().plusSeconds(3600))
    AuthenticatedUser(user, s)

  def apply(user: String, session: Role): AuthenticatedUser =
    val u = User(username = user, passwordHash = "", role = session)
    val s = Session(userId = u.id, createdAt = Instant.now(), expiresAt = Instant.now().plusSeconds(3600))
    AuthenticatedUser(u, s)

  private given Configuration = Configuration.default.withDefaults
  given Codec.AsObject[AuthenticatedUser] = ConfiguredCodec.derived[AuthenticatedUser]
