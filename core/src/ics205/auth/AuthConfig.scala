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

import com.github.andyglow.config.*
import com.typesafe.config.{Config, ConfigFactory}
import java.time.Duration

case class AuthConfig(
  userFileName: String = AuthConfig.default.userFileName,
  sessionFileName: String = AuthConfig.default.sessionFileName,
  sessionLifetime: Duration = AuthConfig.default.sessionLifetime,
  cookieName: String = AuthConfig.default.cookieName,
  secureCookie: Boolean = AuthConfig.default.secureCookie
)

object AuthConfig:
  def fromConfig(config: Config): AuthConfig =
    val authConf = if config.hasPath("auth") then config.getConfig("auth") else config
    AuthConfig(
      userFileName = if authConf.hasPath("userFileName") then authConf.getString("userFileName") else "users.json",
      sessionFileName = if authConf.hasPath("sessionFileName") then authConf.getString("sessionFileName") else "sessions.json",
      sessionLifetime = if authConf.hasPath("sessionLifetime") then authConf.getDuration("sessionLifetime") else Duration.ofHours(24),
      cookieName = if authConf.hasPath("cookieName") then authConf.getString("cookieName") else "session",
      secureCookie = if authConf.hasPath("secureCookie") then authConf.getBoolean("secureCookie") else false
    )

  def apply(config: Config): AuthConfig = fromConfig(config)

  lazy val default: AuthConfig = fromConfig(ConfigFactory.load())
