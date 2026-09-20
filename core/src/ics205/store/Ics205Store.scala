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

package ics205.store

import com.typesafe.scalalogging.LazyLogging
import ics205.model.{Ics205, OperationalPeriod}
import ics205.util.FileHelper
import jakarta.inject.{Inject, Singleton}

import java.time.LocalDateTime

@Singleton
class Ics205Store @Inject()(fileHelper: FileHelper) extends LazyLogging:
  private val fileName = "ics205.json"

  private var current: Ics205 = fileHelper.loadOrDefault[Ics205](fileName) {
    Ics205(incidentName = "", operationalPeriod = OperationalPeriod(), channels = Seq.empty)
  }

  def ics205(): Ics205 = synchronized { current }

  def save(value: Ics205): Unit = synchronized {
    val preparedNow = value.copy(prepared = LocalDateTime.now())
    fileHelper.save(fileName, preparedNow)
    current = preparedNow
  }
