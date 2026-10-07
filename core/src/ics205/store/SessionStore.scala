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
import ics205.auth.{AuthConfig, Session, SessionDatabase, SessionId, UserId}
import ics205.util.FileHelper
import ics205.util.Ids.generateId
import io.circe.parser.*
import io.circe.syntax.*
import io.circe.Printer
import jakarta.inject.{Inject, Singleton}

import java.nio.file.NoSuchFileException
import java.security.SecureRandom
import java.time.Instant
import scala.collection.mutable

trait SessionStore:
  def create(userId: UserId): Session
  def find(sessionId: SessionId): Option[Session]
  def get(sessionId: SessionId): Option[Session] = find(sessionId)
  def delete(sessionId: SessionId): Unit
  def deleteAllForUser(userId: UserId): Unit
  def all(): Seq[Session]
  def cleanExpired(): Unit
  def save(session: Session): Unit = ()
  def reload(): Unit = ()

@Singleton
class InMemJsonSessionStore @Inject()(fileHelper: FileHelper, config: AuthConfig) extends SessionStore with LazyLogging:
  def this(fileHelper: FileHelper) = this(fileHelper, AuthConfig())

  private val fileName: String = config.sessionFileName
  private val secureRandom = new SecureRandom()

  private val sessions: mutable.Map[SessionId, Session] = mutable.Map.empty

  // Initialize on startup: load persisted sessions, discard expired sessions, populate in-memory store
  synchronized {
    val loaded = loadFromDisk()
    val now = Instant.now()
    val valid = loaded.filter(_.expiresAt.isAfter(now))
    valid.foreach(s => sessions.put(s.id, s))
    if valid.length != loaded.length then
      persist()
  }

  private def generateSessionId(): SessionId =
    val bytes = new Array[Byte](32)
    secureRandom.nextBytes(bytes)
    java.util.HexFormat.of().formatHex(bytes)

  private def loadFromDisk(): Seq[Session] =
    val path = fileHelper.directory / fileName
    try
      if !os.exists(path) then Seq.empty
      else
        val content = os.read(path)
        parse(content).flatMap(_.as[SessionDatabase]).fold(
          err =>
            logger.error(s"Failed to parse session JSON file: $fileName", err)
            Seq.empty,
          db => db.sessions
        )
    catch
      case _: NoSuchFileException =>
        Seq.empty
      case e: Exception =>
        logger.error(s"Failed to read session file: $fileName", e)
        Seq.empty

  private def persist(): Unit =
    val path = fileHelper.directory / fileName
    val tempPath = fileHelper.directory / s".$fileName.tmp.${generateId()}"
    val db = SessionDatabase(sessions.values.toSeq)
    val json = db.asJson.printWith(Printer.indented("  ").copy(dropNullValues = true))
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

  private def cleanExpiredInternal(): Boolean =
    val now = Instant.now()
    val expired = sessions.filter(_._2.expiresAt.isBefore(now)).keys.toList
    if expired.nonEmpty then
      expired.foreach(sessions.remove)
      true
    else
      false

  override def create(userId: UserId): Session = synchronized {
    cleanExpiredInternal()
    val now = Instant.now()
    val expiresAt = now.plus(config.sessionLifetime)
    val session = Session(
      userId = userId,
      createdAt = now,
      expiresAt = expiresAt,
      id = generateSessionId()
    )
    sessions.put(session.id, session)
    persist()
    session
  }

  override def find(sessionId: SessionId): Option[Session] = synchronized {
    sessions.get(sessionId) match
      case Some(session) =>
        if session.expiresAt.isBefore(Instant.now()) then
          sessions.remove(sessionId)
          persist()
          None
        else
          Some(session)
      case None =>
        None
  }

  override def delete(sessionId: SessionId): Unit = synchronized {
    if sessions.remove(sessionId).isDefined then
      persist()
  }

  override def deleteAllForUser(userId: UserId): Unit = synchronized {
    val toRemove = sessions.filter(_._2.userId == userId).keys.toList
    if toRemove.nonEmpty then
      toRemove.foreach(sessions.remove)
      persist()
  }

  override def all(): Seq[Session] = synchronized {
    val now = Instant.now()
    sessions.values.filter(_.expiresAt.isAfter(now)).toSeq
  }

  override def cleanExpired(): Unit = synchronized {
    if cleanExpiredInternal() then
      persist()
  }

  override def save(session: Session): Unit = synchronized {
    sessions.put(session.id, session)
    persist()
  }

  override def reload(): Unit = synchronized {
    sessions.clear()
    val loaded = loadFromDisk()
    val now = Instant.now()
    val valid = loaded.filter(_.expiresAt.isAfter(now))
    valid.foreach(s => sessions.put(s.id, s))
    if valid.length != loaded.length then
      persist()
  }
