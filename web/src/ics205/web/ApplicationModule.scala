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

package ics205.web

import com.google.inject.AbstractModule
import ics205.auth.{AuthConfig, AuthenticationService, PasswordService, ScalaPassPasswordService}
import ics205.store.{Ics205Store, InMemJsonSessionStore, SessionStore, UserStore}
import ics205.util.FileHelper
import ics205.web.auth.AuthSecurity
import net.codingwell.scalaguice.ScalaModule

class ApplicationModule extends AbstractModule with ScalaModule:
  override def configure(): Unit =
    val fileHelper = new FileHelper()
    bind[FileHelper].toInstance(fileHelper)

    val authConfig = AuthConfig()
    bind[AuthConfig].toInstance(authConfig)
    bind[PasswordService].to[ScalaPassPasswordService].asEagerSingleton()
    bind[UserStore].asEagerSingleton()
    bind[SessionStore].to[InMemJsonSessionStore].asEagerSingleton()
    bind[AuthenticationService].asEagerSingleton()
    bind[AuthSecurity].asEagerSingleton()

    AutoBind.bindAllImplementationsOf[ApiEndpoints](
      binder = binder(),
      packagesOnly = Seq("ics205.web"),
      asSingleton = true
    )
    bind[WebApplication]
