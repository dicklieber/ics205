/*
 * Copyright (c) 2026. Dick Lieber, WA9NNN
 *
 * This program is free software: you can redistribute it and/or modify 
 * it under the terms of the GNU General Public License as published by 
 * the Free Software Foundation, either version 3 of the License, or    
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
import ics205.auth.{Permission, UserId}
import ics205.model.{Ics205, Ics205Event, Ics205Metadata, OperationalPeriod}
import ics205.util.{FileHelper, UtcFormatter}
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

@Singleton
class Ics205Store @Inject()(fileHelper: FileHelper) extends LazyLogging:
  val eventsDirectory: os.Path = fileHelper.directory / "events"

  private var eventsState: Seq[Ics205Event] = loadFromDisk().sortBy(_.eventName)
  private var activeEventName: Option[String] = eventsState.headOption.map(_.eventName).filter(_.nonEmpty)

  private def fileNameFor(name: String): String =
    val trimmed = name.trim
    val sanitized = trimmed.replaceAll("""[\\/:*?"<>|]""", "_")
    s"$sanitized.json"

  private def parseEventFile(path: os.Path): Option[Ics205Event] =
    decode[Ics205Event](os.read(path)) match
      case Left(err) =>
        logger.error(s"Failed to parse JSON file: ${path.last}", err)
        None
      case Right(json) =>
        logger.debug("Parsed JSON file {}: to {}", path, json)
        Some(json)

    
  private def loadFromDisk(): Seq[Ics205Event] = synchronized {
    if !os.isDir(eventsDirectory) then
      os.makeDir.all(eventsDirectory)
    for
      file <- os.list(eventsDirectory)
      if os.isFile(file)
      if file.last.endsWith(".json")
      parsed <- parseEventFile(file)
    yield
      parsed
  }

  private val UtcFormatter: DateTimeFormatter = Ics205Store.UtcFormatter

  def insertTimestamp(fileName: String, timestamp: TemporalAccessor = Instant.now()): String =
    Ics205Store.insertTimestamp(fileName, timestamp)

  def timestampedFileName(fileName: String, timestamp: TemporalAccessor = Instant.now()): String =
    Ics205Store.timestampedFileName(fileName, timestamp)

  private def writeEventFile(event: Ics205Event): Unit = synchronized {
    val eventName = if event.eventName.nonEmpty then event.eventName else event.ics205.incidentName
    val fileName = fileNameFor(eventName)
    val path = eventsDirectory / fileName

    if os.exists(path) && os.isFile(path) then
      try
        val bakDirName = if fileName.endsWith(".json") then fileName.stripSuffix(".json") + ".bak" else s"$fileName.bak"
        val bakDir = eventsDirectory / bakDirName
        os.makeDir.all(bakDir)
        val bakFileName = insertTimestamp(fileName)
        val bakPath = bakDir / bakFileName
        os.copy(path, bakPath, replaceExisting = true, createFolders = true)
      catch
        case e: Exception =>
          logger.error(s"Failed to backup existing event file: $fileName", e)

    val tempPath = eventsDirectory / s".$fileName.tmp.${generateId()}"
    val json = event.asJson.printWith(Printer.indented("  ").copy(dropNullValues = true))
    os.write.over(tempPath, json, createFolders = true)
    try
      try
        os.move(tempPath, path, replaceExisting = true, atomicMove = true)
      catch
        case _: Exception =>
          os.move(tempPath, path, replaceExisting = true, atomicMove = false)
    finally
      if os.exists(tempPath) then
        try os.remove(tempPath) catch case _: Exception => ()
  }

  def events(): Seq[Ics205Event] = synchronized { eventsState }

  def fileModifiedAt(eventName: String): Option[Instant] = synchronized {
    getEvent(eventName).flatMap { event =>
      val path = eventsDirectory / fileNameFor(event.eventName)
      try
        Some(java.nio.file.Files.getLastModifiedTime(path.toNIO).toInstant)
      catch
        case _: NoSuchFileException => None
    }
  }

  def listEvents(): Seq[Ics205Event] = synchronized { eventsState }

  def all(): Seq[Ics205Event] = synchronized { eventsState }

  def getEvent(eventName: String): Option[Ics205Event] = synchronized {
    val trimmed = eventName.trim
    if trimmed.isEmpty then None
    else
      eventsState.find(_.eventName.equalsIgnoreCase(trimmed))
        .orElse(eventsState.find(_.ics205.incidentName.equalsIgnoreCase(trimmed)))
  }

  def findByName(eventName: String): Option[Ics205Event] = getEvent(eventName)

  def event(eventName: String): Option[Ics205Event] = getEvent(eventName)

  def currentEvent(): Option[Ics205Event] = synchronized {
    activeEventName.flatMap(getEvent)
      .orElse(eventsState.headOption)
  }

  def currentEventName: Option[String] = synchronized {
    currentEvent().map(_.eventName)
  }

  def setCurrentEvent(eventName: String): Boolean = synchronized {
    getEvent(eventName) match
      case Some(ev) =>
        activeEventName = Some(ev.eventName)
        true
      case None =>
        false
  }

  def uniqueEventName(desiredName: String): String = synchronized {
    val trimmed = desiredName.trim
    val baseName = if trimmed.isEmpty then "Event" else trimmed
    def exists(name: String): Boolean =
      eventsState.exists(_.eventName.equalsIgnoreCase(name))

    if !exists(baseName) then baseName
    else
      val pattern = """^(.*?)\s*\((\d+)\)$""".r
      val (base, startIdx) = baseName match
        case pattern(b, n) if b.trim.nonEmpty => (b.trim, n.toIntOption.getOrElse(0) + 1)
        case _ => (baseName, 1)

      var idx = startIdx
      var candidate = s"$base ($idx)"
      while exists(candidate) do
        idx += 1
        candidate = s"$base ($idx)"
      candidate
  }

  def importEvent(event: Ics205Event, userId: Option[UserId] = None): Ics205Event = synchronized {
    val rawName = if event.eventName.trim.nonEmpty then event.eventName.trim
    else if event.ics205.incidentName.trim.nonEmpty then event.ics205.incidentName.trim
    else "Imported Event"
    val uniqueName = uniqueEventName(rawName)
    val updatedIncidentName = if event.ics205.incidentName.trim.isEmpty then uniqueName else event.ics205.incidentName
    val updatedMetadata = event.metadata.copy(
      lastEditedBy = userId.orElse(event.metadata.lastEditedBy),
      savedAt = Instant.now()
    )
    val updatedEvent = event.copy(
      eventName = uniqueName,
      ics205 = event.ics205.copy(incidentName = updatedIncidentName),
      metadata = updatedMetadata
    )
    saveEvent(updatedEvent, userId, refreshPrepared = false)
    updatedEvent
  }

  def addEvent(event: Ics205Event): Either[String, Ics205Event] = synchronized {
    val name = if event.eventName.nonEmpty then event.eventName.trim else event.ics205.incidentName.trim
    if name.isEmpty then
      Left("Event name cannot be empty")
    else if getEvent(name).isDefined then
      Left(s"Event '$name' already exists")
    else
      val updatedEvent = if event.eventName != name then event.copy(eventName = name) else event
      saveEvent(updatedEvent, refreshPrepared = false)
      Right(updatedEvent)
  }

  def renameEvent(oldName: String, newName: String): Either[String, Ics205Event] = synchronized {
    val trimmedNew = newName.trim
    if trimmedNew.isEmpty then
      Left("Event name cannot be empty")
    else if getEvent(trimmedNew).isDefined && !oldName.equalsIgnoreCase(trimmedNew) then
      Left(s"Event '$trimmedNew' already exists")
    else
      getEvent(oldName) match
        case Some(ev) =>
          val oldFileName = fileNameFor(ev.eventName)
          val oldPath = eventsDirectory / oldFileName
          if os.exists(oldPath) then
            try os.remove(oldPath) catch case _: Exception => ()
          val oldBakDir = eventsDirectory / (if oldFileName.endsWith(".json") then oldFileName.stripSuffix(".json") + ".bak" else s"$oldFileName.bak")
          val newFileName = fileNameFor(trimmedNew)
          val newBakDir = eventsDirectory / (if newFileName.endsWith(".json") then newFileName.stripSuffix(".json") + ".bak" else s"$newFileName.bak")
          if os.exists(oldBakDir) && oldBakDir != newBakDir then
            try os.move(oldBakDir, newBakDir, replaceExisting = true) catch case _: Exception => ()
          val updated = ev.copy(eventName = trimmedNew)
          writeEventFile(updated)
          val idx = eventsState.indexWhere(e => e.eventName.equalsIgnoreCase(oldName) || e.ics205.incidentName.equalsIgnoreCase(oldName))
          eventsState = if idx >= 0 then eventsState.updated(idx, updated) else eventsState :+ updated
          if activeEventName.exists(_.equalsIgnoreCase(oldName)) then
            activeEventName = Some(trimmedNew)
          Right(updated)
        case None =>
          Left(s"Event '$oldName' not found")
  }

  def deleteEvent(eventName: String): Boolean = synchronized {
    val toDelete = eventsState.find(e => e.eventName.equalsIgnoreCase(eventName) || e.ics205.incidentName.equalsIgnoreCase(eventName))
    toDelete match
      case Some(ev) =>
        val fileName = fileNameFor(ev.eventName)
        val path = eventsDirectory / fileName
        if os.exists(path) then
          try os.remove(path) catch case _: Exception => ()
        val bakDir = eventsDirectory / (if fileName.endsWith(".json") then fileName.stripSuffix(".json") + ".bak" else s"$fileName.bak")
        if os.exists(bakDir) then
          try os.remove.all(bakDir) catch case _: Exception => ()

        val filtered = eventsState.filterNot(e => e.eventName.equalsIgnoreCase(ev.eventName) || e.ics205.incidentName.equalsIgnoreCase(ev.eventName))
        eventsState = filtered
        if activeEventName.exists(_.equalsIgnoreCase(ev.eventName)) then
          activeEventName = eventsState.headOption.map(_.eventName).filter(_.nonEmpty)
        true
      case None =>
        false
  }

  def saveEvent(event: Ics205Event, refreshPrepared: Boolean = true): Unit = synchronized {
    saveEvent(event, None, refreshPrepared)
  }

  def saveEvent(event: Ics205Event, userId: UserId): Unit = synchronized {
    saveEvent(event, Some(userId), refreshPrepared = true)
  }

  def saveEvent(event: Ics205Event, userId: UserId, refreshPrepared: Boolean): Unit = synchronized {
    saveEvent(event, Some(userId), refreshPrepared)
  }

  def saveEvent(event: Ics205Event, userId: Option[UserId], refreshPrepared: Boolean): Unit = synchronized {
    val preparedNow = if refreshPrepared then event.ics205.copy(prepared = LocalDateTime.now()) else event.ics205
    val updatedMetadata = event.metadata.copy(
      lastEditedBy = userId.orElse(event.metadata.lastEditedBy),
      savedAt = Instant.now()
    )
    val name = if event.eventName.nonEmpty then event.eventName.trim else preparedNow.incidentName.trim
    val finalName = if name.nonEmpty then name else "Untitled Event"
    val updatedEvent = event.copy(eventName = finalName, ics205 = preparedNow, metadata = updatedMetadata)
    val idx = eventsState.indexWhere(e => e.eventName.equalsIgnoreCase(finalName))

    if idx >= 0 then
      val oldEvent = eventsState(idx)
      val oldFileName = fileNameFor(oldEvent.eventName)
      val newFileName = fileNameFor(finalName)
      if !oldFileName.equalsIgnoreCase(newFileName) then
        val oldPath = eventsDirectory / oldFileName
        if os.exists(oldPath) then
          try os.remove(oldPath) catch case _: Exception => ()

    writeEventFile(updatedEvent)

    val newEvents = if idx >= 0 then
      eventsState.updated(idx, updatedEvent)
    else
      eventsState :+ updatedEvent

    eventsState = newEvents
    activeEventName = Some(finalName)
  }

  def save(event: Ics205Event): Unit = synchronized {
    saveEvent(event)
  }

  def save(event: Ics205Event, userId: Option[UserId]): Unit = synchronized {
    saveEvent(event, userId, refreshPrepared = true)
  }

  def save(event: Ics205Event, userId: Option[UserId], refreshPrepared: Boolean): Unit = synchronized {
    saveEvent(event, userId, refreshPrepared)
  }

  def updateMetadata(eventName: String, f: Ics205Metadata => Ics205Metadata): Option[Ics205Metadata] = synchronized {
    getEvent(eventName).map { ev =>
      val updatedMetadata = f(ev.metadata)
      val updatedEvent = ev.copy(metadata = updatedMetadata)
      saveEvent(updatedEvent, refreshPrepared = false)
      updatedMetadata
    }
  }

  def setUserPermission(eventName: String, userId: UserId, permission: Option[Permission]): Unit = synchronized {
    updateMetadata(eventName, _.withUserPermission(userId, permission))
  }

  def setUserPermission(eventName: String, userId: UserId, permission: Permission): Unit = synchronized {
    updateMetadata(eventName, _.withUserPermission(userId, permission))
  }

  def removeUserPermission(eventName: String, userId: UserId): Unit = synchronized {
    updateMetadata(eventName, _.withoutUserPermission(userId))
  }

  // Backward compatibility methods
  def event(): Option[Ics205Event] = currentEvent()

  def ics205Event(): Option[Ics205Event] = currentEvent()

  def ics205(): Ics205 = currentEvent().map(_.ics205).getOrElse(Ics205(incidentName = "", operationalPeriod = OperationalPeriod(), channels = Seq.empty))

  def metadata(): Ics205Metadata = currentEvent().map(_.metadata).getOrElse(Ics205Metadata())

  def save(value: Ics205, refreshPrepared: Boolean = true): Unit = synchronized {
    save(value, None, refreshPrepared)
  }

  def save(value: Ics205, userId: UserId): Unit = synchronized {
    save(value, Some(userId), refreshPrepared = true)
  }

  def save(value: Ics205, userId: UserId, refreshPrepared: Boolean): Unit = synchronized {
    save(value, Some(userId), refreshPrepared)
  }

  def save(value: Ics205, userId: Option[UserId]): Unit = synchronized {
    save(value, userId, refreshPrepared = true)
  }

  def save(value: Ics205, userId: Option[UserId], refreshPrepared: Boolean): Unit = synchronized {
    currentEvent() match
      case Some(curr) =>
        val updatedEvent = curr.copy(ics205 = value)
        saveEvent(updatedEvent, userId, refreshPrepared)
      case None =>
        val name = if value.incidentName.trim.nonEmpty then value.incidentName.trim else "Event"
        val newEvent = Ics205Event(name, value, Ics205Metadata())
        saveEvent(newEvent, userId, refreshPrepared)
  }

  def updateMetadata(f: Ics205Metadata => Ics205Metadata): Option[Ics205Metadata] = synchronized {
    currentEvent() match
      case Some(curr) =>
        val updatedMetadata = f(curr.metadata)
        val updatedEvent = curr.copy(metadata = updatedMetadata)
        saveEvent(updatedEvent, refreshPrepared = false)
        Some(updatedMetadata)
      case None =>
        val newEvent = Ics205Event("Event", Ics205(incidentName = "", operationalPeriod = OperationalPeriod(), channels = Seq.empty), f(Ics205Metadata()))
        saveEvent(newEvent, refreshPrepared = false)
        Some(newEvent.metadata)
  }

  def setUserPermission(userId: UserId, permission: Option[Permission]): Unit = synchronized {
    updateMetadata(_.withUserPermission(userId, permission))
  }

  def setUserPermission(userId: UserId, permission: Permission): Unit = synchronized {
    updateMetadata(_.withUserPermission(userId, permission))
  }

  def removeUserPermission(userId: UserId): Unit = synchronized {
    updateMetadata(_.withoutUserPermission(userId))
  }

  def reload(): Unit = synchronized {
    eventsState = loadFromDisk()
    if !activeEventName.exists(name => eventsState.exists(_.eventName.equalsIgnoreCase(name))) then
      activeEventName = eventsState.headOption.map(_.eventName).filter(_.nonEmpty)
  }

object Ics205Store:
  val UtcFormatter: DateTimeFormatter = ics205.util.UtcFormatter.formatter

  def insertTimestamp(fileName: String, timestamp: TemporalAccessor = Instant.now()): String =
    val ts = ics205.util.UtcFormatter.format(timestamp)
    if fileName.toLowerCase.endsWith(".json") then
      val base = fileName.substring(0, fileName.length - 5)
      val ext = fileName.substring(fileName.length - 5)
      s"$base.$ts$ext"
    else
      s"$fileName.$ts.json"

  def timestampedFileName(fileName: String, timestamp: TemporalAccessor = Instant.now()): String =
    insertTimestamp(fileName, timestamp)
