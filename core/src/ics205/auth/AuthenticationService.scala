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

import com.typesafe.scalalogging.LazyLogging
import ics205.store.{SessionStore, UserStore}
import ics205.util.Ids.Id
import jakarta.inject.{Inject, Singleton}

@Singleton
class AuthenticationService @Inject()(
  userStore: UserStore,
  passwordService: PasswordService,
  sessionStore: SessionStore
) extends LazyLogging:

  def authenticate(username: String, password: String): Option[Session] =
    userStore.findByUsername(username) match
      case Some(user) if user.enabled && passwordService.verify(password, user.passwordHash) =>
        Some(sessionStore.create(user.id))
      case _ =>
        None

  def authenticateSession(sessionId: Id): Either[AuthError, AuthenticatedUser] =
    sessionStore.find(sessionId) match
      case Some(session) =>
        userStore.findById(session.userId) match
          case Some(user) if user.enabled =>
            Right(AuthenticatedUser(id = user.id, username = user.username, role = user.role))
          case _ =>
            Left(Unauthorized("User disabled or does not exist"))
      case None =>
        Left(Unauthorized("Invalid or expired session"))
