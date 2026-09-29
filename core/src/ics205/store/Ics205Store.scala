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
import ics205.auth.Permission
import ics205.model.{Ics205, Ics205Event, Ics205Metadata, OperationalPeriod}
import ics205.util.FileHelper
import jakarta.inject.{Inject, Singleton}

import java.time.{Instant, LocalDateTime}

@Singleton
class Ics205Store @Inject()(fileHelper: FileHelper) extends LazyLogging:
  private val fileName = "ics205.json"

  private var current: Ics205Event = fileHelper.loadOrDefault[Ics205Event](fileName) {
    Ics205Event(
      Ics205(incidentName = "", operationalPeriod = OperationalPeriod(), channels = Seq.empty),
      Ics205Metadata()
    )
  }

  def event(): Ics205Event = synchronized { current }

  def ics205Event(): Ics205Event = synchronized { current }

  def ics205(): Ics205 = synchronized { current.ics205 }

  def metadata(): Ics205Metadata = synchronized { current.metadata }

  def save(value: Ics205, refreshPrepared: Boolean = true): Unit = synchronized {
    save(value, None, refreshPrepared)
  }

  def save(value: Ics205, userId: String): Unit = synchronized {
    save(value, Some(userId), refreshPrepared = true)
  }

  def save(value: Ics205, userId: String, refreshPrepared: Boolean): Unit = synchronized {
    save(value, Some(userId), refreshPrepared)
  }

  def save(value: Ics205, userId: Option[String]): Unit = synchronized {
    save(value, userId, refreshPrepared = true)
  }

  def save(value: Ics205, userId: Option[String], refreshPrepared: Boolean): Unit = synchronized {
    val preparedNow = if refreshPrepared then value.copy(prepared = LocalDateTime.now()) else value
    val updatedMetadata = current.metadata.copy(
      lastEditedBy = userId.orElse(current.metadata.lastEditedBy),
      savedAt = Instant.now()
    )
    val updatedEvent = current.copy(ics205 = preparedNow, metadata = updatedMetadata)
    fileHelper.save(fileName, updatedEvent)
    current = updatedEvent
  }

  def saveEvent(event: Ics205Event, refreshPrepared: Boolean = true): Unit = synchronized {
    val preparedNow = if refreshPrepared then event.ics205.copy(prepared = LocalDateTime.now()) else event.ics205
    val updatedMetadata = event.metadata.copy(savedAt = Instant.now())
    val updatedEvent = event.copy(ics205 = preparedNow, metadata = updatedMetadata)
    fileHelper.save(fileName, updatedEvent)
    current = updatedEvent
  }

  def updateMetadata(f: Ics205Metadata => Ics205Metadata): Ics205Metadata = synchronized {
    val updatedMetadata = f(current.metadata)
    val updatedEvent = current.copy(metadata = updatedMetadata)
    fileHelper.save(fileName, updatedEvent)
    current = updatedEvent
    updatedMetadata
  }

  def setUserPermission(userId: String, permission: Option[Permission]): Unit = synchronized {
    updateMetadata(_.withUserPermission(userId, permission))
  }

  def setUserPermission(userId: String, permission: Permission): Unit = synchronized {
    updateMetadata(_.withUserPermission(userId, permission))
  }

  def removeUserPermission(userId: String): Unit = synchronized {
    updateMetadata(_.withoutUserPermission(userId))
  }
