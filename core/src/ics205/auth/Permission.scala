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
  case ViewUsers, EditUsers, Debug, ViewPlans, EditPlans, DeletePlans, AllGroup

enum Role(val permissions: Set[Permission]):
  case Admin extends Role(Set(
    Permission.ViewUsers,
    Permission.EditUsers,
    Permission.Debug,
    Permission.ViewPlans,
    Permission.EditPlans,
    Permission.DeletePlans,
    Permission.AllGroup
  ))
  case Editor extends Role(Set(
    Permission.ViewPlans,
    Permission.EditPlans
  ))
  case Viewer extends Role(Set(
    Permission.ViewPlans
  ))

  def hasPermission(permission: Permission): Boolean =
    permissions.contains(permission)

object Permission:
  given Codec[Permission] = enumCodec(Permission.values)

object Role:
  given Codec[Role] = enumCodec(Role.values)

private def enumCodec[E <: scala.reflect.Enum](values: Array[E]): Codec[E] =
  Codec.from(
    Decoder.decodeString.emap(n => values.find(_.toString == n).toRight(s"Unknown value: $n")),
    Encoder.encodeString.contramap(_.toString)
  )
