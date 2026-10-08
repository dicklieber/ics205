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
    if os.exists(eventsDirectory) then
      for file <- os.list(eventsDirectory) 
        if file.last.endsWith(Ics205Event.extension) || file.last.endsWith(".json") || file.last.endsWith(".ics205")
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

  def save(event: Ics205Event,
           authenticatedUser: AuthenticatedUser): Unit = synchronized { // add to in-memory store
    val withUpdateMetadata = event.update(authenticatedUser)
    allEvents.put(withUpdateMetadata.id, withUpdateMetadata)
    // then copy current file to the backup directory for this event
    val path = eventsDirectory / withUpdateMetadata.fileName
    if os.isFile(path) then 
      val bakDir = eventsDirectory / withUpdateMetadata.id / "bak"
      os.makeDir.all(bakDir)
      val bakFileName = withUpdateMetadata.bakFileName
      val bakPath = bakDir / bakFileName
      os.copy(path, bakPath, replaceExisting = true, createFolders = true)

    // lastly write the file
    os.write
      .over(path,
        withUpdateMetadata.asJson.printWith(Printer.indented("  ").copy(dropNullValues = true)),
        createFolders = true)
  }

  def save(event: Ics205Event): Unit = synchronized {
    allEvents.put(event.id, event)
    val path = eventsDirectory / event.fileName
    os.write
      .over(path,
        event.asJson.printWith(Printer.indented("  ").copy(dropNullValues = true)),
        createFolders = true)
  }

  def save(ics205: Ics205): Unit = synchronized {
    val ev = currentEvent().getOrElse(Ics205Event(ics205 = ics205))
    save(ev.copy(ics205 = ics205))
  }

  def save(ics205: Ics205,
           authenticatedUser: AuthenticatedUser): Unit = synchronized {
    val ev = currentEvent().getOrElse(Ics205Event(ics205 = ics205))
    save(ev.copy(ics205 = ics205), authenticatedUser)
  }


  def events(): Seq[Ics205Event] = listEvents()
  
  def listEvents(): Seq[Ics205Event] = synchronized {
    allEvents.values.toSeq
  }
  
  def currentEvent(): Option[Ics205Event] = listEvents().headOption

  def currentEventName: Option[String] = currentEvent().map(_.eventName)

  def event(): Option[Ics205Event] = currentEvent()

  def ics205Event(): Option[Ics205Event] = currentEvent()

  def ics205(): Ics205 = currentEvent().map(_.ics205).getOrElse(Ics205(incidentName = "", operationalPeriod = OperationalPeriod(), channels = Seq.empty))
  
  def metadata(): Ics205Metadata = currentEvent().map(_.metadata).getOrElse(Ics205Metadata())

  def setUserPermission(userId: String, perm: Permission): Unit = synchronized {
    currentEvent().foreach { ev =>
      val updated = ev.copy(metadata = ev.metadata.copy(permissions = ev.metadata.permissions + (userId -> perm)))
      save(updated)
    }
  }

  def setUserPermission(userId: String, perm: Option[Permission]): Unit = synchronized {
    perm match
      case Some(p) => setUserPermission(userId, p)
      case None => removeUserPermission(userId)
  }

  def removeUserPermission(userId: String): Unit = synchronized {
    currentEvent().foreach { ev =>
      val updated = ev.copy(metadata = ev.metadata.copy(permissions = ev.metadata.permissions - userId))
      save(updated)
    }
  }
  
  
  def getEvent(id: EventId): Option[Ics205Event] = synchronized {
    allEvents.get(id)
  }

  def findByName(name: String): Option[Ics205Event] = synchronized {
    getEvent(name).orElse(listEvents().find(_.eventName.equalsIgnoreCase(name)))
  }

  def setCurrentEvent(name: String): Unit = ()

  def setUserPermission(eventName: String, userId: String, perm: Permission): Unit = synchronized {
    getEvent(eventName).orElse(findByName(eventName)).foreach { ev =>
      val updated = ev.copy(metadata = ev.metadata.copy(permissions = ev.metadata.permissions + (userId -> perm)))
      save(updated)
    }
  }

  def setUserPermission(eventName: String, userId: String, perm: Option[Permission]): Unit = perm match {
    case Some(p) => setUserPermission(eventName, userId, p)
    case None => removeUserPermission(eventName, userId)
  }

  def removeUserPermission(eventName: String, userId: String): Unit = synchronized {
    getEvent(eventName).orElse(findByName(eventName)).foreach { ev =>
      val updated = ev.copy(metadata = ev.metadata.copy(permissions = ev.metadata.permissions - userId))
      save(updated)
    }
  }

  def uniqueEventName(name: String): String = synchronized {
    val cleanName = if name.matches(""".* \(\d+\)$""") then name.replaceFirst(""" \(\d+\)$""", "") else name
    if getEvent(name).isEmpty && !listEvents().exists(_.eventName.equalsIgnoreCase(name)) then
      name
    else
      var counter = 1
      while getEvent(s"$cleanName ($counter)").isDefined || listEvents().exists(_.eventName.equalsIgnoreCase(s"$cleanName ($counter)")) do
        counter += 1
      s"$cleanName ($counter)"
  }

  def importEvent(event: Ics205Event, userId: Option[UserId] = None): Ics205Event = synchronized {
    val baseName = if event.eventName.trim.nonEmpty then event.eventName.trim else "Imported Event"
    val finalName = if getEvent(baseName).isDefined || listEvents().exists(_.eventName.equalsIgnoreCase(baseName)) then
      uniqueEventName(baseName)
    else
      baseName
    val toSave = event.copy(id = finalName, metadata = event.metadata.copy(lastEditedBy = userId, savedAt = Instant.now()))
    save(toSave)
    toSave
  }

  def deleteEvent(id: EventId): Boolean = synchronized {
    allEvents.remove(id) match
      case Some(removed) =>
        val path = eventsDirectory / removed.fileName
        if os.exists(path) then os.remove(path)
        val eventDir = eventsDirectory / removed.id
        if os.exists(eventDir) then os.remove.all(eventDir)
        val oldBakDir = eventsDirectory / s"${removed.id}.bak"
        if os.exists(oldBakDir) then os.remove.all(oldBakDir)
        true
      case None =>
        findByName(id) match
          case Some(ev) =>
            allEvents.remove(ev.id)
            val path = eventsDirectory / ev.fileName
            if os.exists(path) then os.remove(path)
            val eventDir = eventsDirectory / ev.id
            if os.exists(eventDir) then os.remove.all(eventDir)
            val oldBakDir = eventsDirectory / s"${ev.id}.bak"
            if os.exists(oldBakDir) then os.remove.all(oldBakDir)
            true
          case None =>
            false
  }
  
  def deleteEvent(id: EventId,
                  authenticatedUser: AuthenticatedUser): Boolean = synchronized {
    authenticatedUser.check(EditPlans)
    deleteEvent(id)
  }
  
  def reload(): Unit = synchronized {
    allEvents = loadFromDisk()
  }

object Ics205Store:
  private val timestampFormatter = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC)

  def insertTimestamp(fileName: String, timestamp: TemporalAccessor = Instant.now()): String =
    val ts = timestampFormatter.format(timestamp)
    val dotIndex = fileName.lastIndexOf('.')
    if dotIndex > 0 then
      val base = fileName.substring(0, dotIndex)
      val ext = fileName.substring(dotIndex)
      s"$base.$ts$ext"
    else
      s"$fileName.$ts.json"

