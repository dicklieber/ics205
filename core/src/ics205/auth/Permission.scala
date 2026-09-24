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

enum Permission:
  case ViewUsers
  case EditUsers
  case ConfigureSystem
  case ViewPlans
  case EditPlans

object RolePermissions:
  val roleToPermissions: Map[String, Set[Permission]] = Map(
    "admin" -> Set(
      Permission.ViewUsers,
      Permission.EditUsers,
      Permission.ConfigureSystem,
      Permission.ViewPlans,
      Permission.EditPlans
    ),
    "editor" -> Set(
      Permission.ViewPlans,
      Permission.EditPlans
    ),
    "user" -> Set(
      Permission.ViewPlans
    ),
    "viewer" -> Set(
      Permission.ViewPlans
    )
  )

  def permissionsForRoles(roles: Set[String]): Set[Permission] =
    roles.flatMap(role => roleToPermissions.getOrElse(role.toLowerCase, Set.empty))

  def hasPermission(roles: Set[String], permission: Permission): Boolean =
    permissionsForRoles(roles).contains(permission)
