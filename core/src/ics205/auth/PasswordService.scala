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

import com.outr.scalapass.Argon2PasswordFactory
import com.typesafe.scalalogging.LazyLogging
import jakarta.inject.{Inject, Singleton}
import scala.util.control.NonFatal

trait PasswordService:
  def hash(password: String): String
  def verify(password: String, hash: String): Boolean

@Singleton
class ScalaPassPasswordService @Inject()() extends PasswordService with LazyLogging:
  private val factory = Argon2PasswordFactory()

  override def hash(password: String): String =
    factory.hash(password)

  override def verify(password: String, hash: String): Boolean =
    try
      factory.verify(password, hash)
    catch
      case NonFatal(e) =>
        logger.error("Failed to verify password with ScalaPass", e)
        false
