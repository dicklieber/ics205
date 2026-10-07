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

import ics205.model.Ics205Metadata

object AuthorizationService:
  def authorize(user: AuthenticatedUser, permission: Permission): Either[AuthError, AuthenticatedUser] =
    if user.hasPermission(permission) then Right(user)
    else Left(Forbidden(s"User '${user.username}' lacks permission '$permission'"))

  def authorizeEvent(user: AuthenticatedUser, metadata: Ics205Metadata, permission: Permission): Either[AuthError, AuthenticatedUser] =
    if user.user.role == Role.Admin then Right(user)
    else if user.hasPermission(permission) then Right(user)
    else Left(Forbidden(s"User '${user.username}' lacks permission '$permission' for this event"))
