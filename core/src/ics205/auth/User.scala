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

import ics205.util.Ids
import ics205.util.Ids.Id
import io.circe.{Codec, Decoder, Encoder, HCursor, Json, JsonObject}
import io.circe.derivation.{Configuration, ConfiguredCodec}

type UserId = Id

case class User(
  username: String,
  passwordHash: String,
  role: RolePermissions,
  enabled: Boolean = true,
  id: UserId = Ids.generateId()
):
  def roles: Set[String] = Set(role.toString.toLowerCase)

object User:
  given Codec.AsObject[User] = Codec.AsObject.from(
    (c: HCursor) => {
      for
        id <- c.downField("id").as[String]
        username <- c.downField("username").as[String]
        passwordHash <- c.downField("passwordHash").as[String]
        role <- c.downField("role").as[RolePermissions].orElse(
          c.downField("roles").as[Seq[String]].map(roles =>
            roles.headOption.flatMap(RolePermissions.fromString).getOrElse(RolePermissions.User)
          ).orElse(Right(RolePermissions.User))
        )
        enabled <- c.downField("enabled").as[Option[Boolean]].map(_.getOrElse(true))
      yield User(username = username, passwordHash = passwordHash, role = role, enabled = enabled, id = id)
    },
    (u: User) => JsonObject(
      "id" -> Json.fromString(u.id),
      "username" -> Json.fromString(u.username),
      "passwordHash" -> Json.fromString(u.passwordHash),
      "role" -> Json.fromString(u.role.toString.toLowerCase),
      "enabled" -> Json.fromBoolean(u.enabled)
    )
  )

case class UserDatabase(
  users: Seq[User] = Seq.empty
)

object UserDatabase:
  private given Configuration = Configuration.default.withDefaults
  given Codec.AsObject[UserDatabase] = ConfiguredCodec.derived[UserDatabase]
