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

import ics205.util.{Ids, UnauthorizedException}
import io.circe.Codec
import io.circe.derivation.{Configuration, ConfiguredCodec}

case class AuthenticatedUser(
  username: String,
  role: RolePermissions,
  id: UserId = Ids.generateId()
):
  def check(permission: Permission):Unit =
    if !hasPermission(permission) then throw new UnauthorizedException()
  def roles: Set[String] = Set(role.toString.toLowerCase)
  def hasPermission(permission: Permission): Boolean = role.hasPermission(permission)

object AuthenticatedUser:
  private given Configuration = Configuration.default.withDefaults
  given Codec.AsObject[AuthenticatedUser] = ConfiguredCodec.derived[AuthenticatedUser]
