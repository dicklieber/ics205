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

import io.circe.{Codec, Decoder, Encoder}

enum Permission:
  case ViewUsers
  case EditUsers
  case Debug
  case ViewPlans
  case EditPlans

object Permission:
  def fromString(name: String): Option[Permission] =
    values.find(_.toString.equalsIgnoreCase(name.trim))

  given Codec[Permission] = Codec.from(
    Decoder.decodeString.emap(str => fromString(str).toRight(s"Unknown permission: $str")),
    Encoder.encodeString.contramap(_.toString)
  )

  given io.circe.KeyEncoder[Permission] = io.circe.KeyEncoder.encodeKeyString.contramap(_.toString)
  given io.circe.KeyDecoder[Permission] = io.circe.KeyDecoder.instance(fromString)

enum RolePermissions(val permissions: Set[Permission]):
  case Admin extends RolePermissions(Set(
    Permission.ViewUsers,
    Permission.EditUsers,
    Permission.Debug,
    Permission.ViewPlans,
    Permission.EditPlans
  ))
  case Editor extends RolePermissions(Set(
    Permission.ViewPlans,
    Permission.EditPlans
  ))
  case User extends RolePermissions(Set(
    Permission.ViewPlans
  ))
  case Viewer extends RolePermissions(Set(
    Permission.ViewPlans
  ))

  def hasPermission(permission: Permission): Boolean =
    permissions.contains(permission)

object RolePermissions:
  def fromString(name: String): Option[RolePermissions] =
    values.find(_.toString.equalsIgnoreCase(name.trim))

  def hasPermission(role: RolePermissions, permission: Permission): Boolean =
    role.hasPermission(permission)

  def hasPermission(roleName: String, permission: Permission): Boolean =
    fromString(roleName).exists(_.hasPermission(permission))


  given Codec[RolePermissions] = Codec.from(
    Decoder.decodeString.emap(str => fromString(str).toRight(s"Unknown role: $str")),
    Encoder.encodeString.contramap(_.toString.toLowerCase)
  )

type Role = RolePermissions
val Role: RolePermissions.type = RolePermissions
