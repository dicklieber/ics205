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

import java.time.Duration

case class AuthConfig(
  userFileName: String = sys.env.getOrElse("AUTH_USERS_FILE", "users.json"),
  sessionFileName: String = sys.env.getOrElse("AUTH_SESSIONS_FILE", "sessions.json"),
  sessionLifetime: Duration = sys.env.get("AUTH_SESSION_LIFETIME_SECONDS")
    .flatMap(_.toLongOption)
    .map(Duration.ofSeconds)
    .getOrElse(Duration.ofHours(24)),
  cookieName: String = sys.env.getOrElse("AUTH_COOKIE_NAME", "session"),
  secureCookie: Boolean = sys.env.get("AUTH_COOKIE_SECURE")
    .flatMap(_.toBooleanOption)
    .getOrElse(false)
)
