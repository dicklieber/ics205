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

import ics205.model.EventId
import com.typesafe.scalalogging.LazyLogging
import ics205.auth.{AuthenticatedUser, Permission, Unauthorized, UserId}
import ics205.auth.Permission.EditPlans
import ics205.model.{EventId, Ics205, Ics205Event, Ics205Metadata, OperationalPeriod}
import ics205.util.{FileHelper, UnauthorizedException, UtcFormatter}
import ics205.util.Ids.generateId
import io.circe.{Codec, Printer}
import io.circe
import io.circe.parser.*
import io.circe.syntax.*
import jakarta.inject.{Inject, Singleton}

import java.nio.file.NoSuchFileException
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAccessor
import java.time.{Instant, LocalDateTime, ZoneOffset}
import scala.collection.concurrent.TrieMap

@Singleton class Ics205Store @Inject()(fileHelper: FileHelper) extends LazyLogging:
  private val eventsDirectory: os.Path = fileHelper.directory / "events"

  private var allEvents: TrieMap[EventId, Ics205Event] = loadFromDisk()

  private def loadFromDisk(): TrieMap[EventId, Ics205Event] = synchronized {
    val newMap = TrieMap.empty[EventId, Ics205Event]
    for file <- os.list(eventsDirectory) 
      if file.last.endsWith(Ics205Event.extension) 
      ics205Event <- parseEventFile(file) 
    yield
      newMap.put(ics205Event.id, ics205Event)
    newMap
  }

  private def parseEventFile(path: os.Path): Option[Ics205Event] = 
    val sJson = os.read(path)
    decode[Ics205Event](sJson) match 
      case Left(err) => 
        logger.error("Failed to decode Ics205Event from file" )
        None
      case Right(event) => 
        Some(event)


//
//  def insertTimestamp(fileName: String,
//                      timestamp: TemporalAccessor = Instant.now()): String = Ics205Store.insertTimestamp(fileName,
//    timestamp)
//
//  def timestampedFileName(fileName: String,
//                          timestamp: TemporalAccessor = Instant.now()): String = Ics205Store.timestampedFileName(
//    fileName,
//    timestamp)

  def save(event: Ics205Event,
           authenticatedUser: AuthenticatedUser): Unit = synchronized { // add to in-memory store
    allEvents.put(event.id, event)
    // then copy current file to the backup directory for this event
    val path = eventsDirectory / event.fileName
    if os.isFile(path) then 
      val bakDir = eventsDirectory / event.id / "bak"
      os.makeDir.all(bakDir)
      val bakFileName = event.bakFileName
      val bakPath = bakDir / bakFileName
      os.copy(path, bakPath, replaceExisting = true, createFolders = true)
      // then update metadata
      val withUpdateMetadata = event.update(authenticatedUser)
  
      // lastly write the file
      os.write
        .over(path,
          withUpdateMetadata.asJson.printWith(Printer.indented("  ").copy(dropNullValues = true)),
          createFolders = true)
  }
  
  def listEvents(): Seq[Ics205Event] = synchronized {
    allEvents.values.toSeq
  }
  
  
  def getEvent(id: EventId): Option[Ics205Event] = synchronized {
    allEvents.get(id)
  }
  
  
  def deleteEvent(id: EventId,
                  authenticatedUser: AuthenticatedUser): Boolean = synchronized {
    authenticatedUser.check(EditPlans)
  
    allEvents.remove(id) match 
      case Some(removed) => 
        val path = eventsDirectory / removed.fileName
        os.remove(path)
        logger.info(s"Event with id $id deleted")
        true
      case None =>
        false
  }
  
  

  
  def reload(): Unit = synchronized {
    allEvents = loadFromDisk()
  }

