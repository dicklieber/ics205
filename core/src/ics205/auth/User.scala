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

import io.circe.Codec
import io.circe.derivation.{Configuration, ConfiguredCodec}

case class User(
  id: String,
  username: String,
  passwordHash: String,
  roles: Set[String],
  enabled: Boolean = true
)

object User:
  private given Configuration = Configuration.default.withDefaults
  given Codec.AsObject[User] = ConfiguredCodec.derived[User]

case class UserDatabase(
  users: Seq[User] = Seq.empty
)

object UserDatabase:
  private given Configuration = Configuration.default.withDefaults
  given Codec.AsObject[UserDatabase] = ConfiguredCodec.derived[UserDatabase]
