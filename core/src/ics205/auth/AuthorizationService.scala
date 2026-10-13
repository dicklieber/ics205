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

import ics205.model.{EventAccess, Ics205Event}

object AuthorizationService:
  extension (event: Ics205Event)
    /** Access a user has to this event; decided only by permissions (group access via [[Permission.AllGroup]] or membership). */
    def accessBy(user: UserContext): Option[EventAccess] =
      if !user.canAccessGroup(event.group) then None
      else if user.hasPermission(Permission.EditPlans) then Some(EventAccess.Edit)
      else if user.hasPermission(Permission.ViewPlans) then Some(EventAccess.ReadOnly)
      else None

    def viewableBy(user: UserContext): Boolean = accessBy(user).isDefined

    def editableBy(user: UserContext): Boolean = accessBy(user).contains(EventAccess.Edit)

  def authorize(user: AuthenticatedUser, permission: Permission): Either[AuthError, AuthenticatedUser] =
    if user.hasPermission(permission) then Right(user)
    else Left(Forbidden(s"User '${user.username}' lacks permission '$permission'"))

  def authorizeEvent(user: AuthenticatedUser, permission: Permission): Either[AuthError, AuthenticatedUser] =
    authorize(user, permission)

  def authorizeGroup(user: AuthenticatedUser, groupName: String): Either[AuthError, AuthenticatedUser] =
    if user.canAccessGroup(groupName) then Right(user)
    else Left(Forbidden(s"User '${user.username}' lacks access to group '$groupName'"))
