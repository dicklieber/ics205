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
import ics205.auth.{AuthConfig, User, UserDatabase, UserId}
import ics205.util.FileHelper
import ics205.util.Ids.generateId
import io.circe.parser.*
import io.circe.syntax.*
import io.circe.Printer
import jakarta.inject.{Inject, Singleton}

import java.nio.file.NoSuchFileException

@Singleton
class UserStore @Inject()(fileHelper: FileHelper, config: AuthConfig) extends LazyLogging:
  def this(fileHelper: FileHelper) = this(fileHelper, AuthConfig())

  private val fileName: String = config.userFileName

  private var usersState: Seq[User] = loadFromDisk()

  private def loadFromDisk(): Seq[User] = synchronized {
    val path = fileHelper.directory / fileName
    try
      if !os.exists(path) then Seq.empty
      else
        val content = os.read(path)
        parse(content).flatMap(_.as[UserDatabase]).fold(
          err =>
            logger.error(s"Failed to parse user JSON file: $fileName", err)
            Seq.empty,
          db => db.users
        )
    catch
      case _: NoSuchFileException =>
        Seq.empty
      case e: Exception =>
        logger.error(s"Failed to read user file: $fileName", e)
        Seq.empty
  }

  private def persist(users: Seq[User]): Unit = synchronized {
    val path = fileHelper.directory / fileName
    val tempPath = fileHelper.directory / s".$fileName.tmp.${generateId()}"
    val db = UserDatabase(users)
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
  }

  def all(): Seq[User] = synchronized { usersState }

  def findById(id: UserId): Option[User] = synchronized {
    usersState.find(_.id == id)
  }

  def findByUsername(username: String): Option[User] = synchronized {
    usersState.find(_.username.equalsIgnoreCase(username))
  }

  def add(user: User): Either[String, User] = synchronized {
    if usersState.exists(_.username.equalsIgnoreCase(user.username)) then
      Left(s"User with username '${user.username}' already exists")
    else if usersState.exists(_.id == user.id) then
      Left(s"User with id '${user.id}' already exists")
    else
      val updated = usersState :+ user
      persist(updated)
      usersState = updated
      logger.info(s"Added user '${user.username}' (id: '${user.id}', role: ${user.role}, enabled: ${user.enabled})")
      Right(user)
  }

  def update(user: User): Either[String, User] = synchronized {
    usersState.indexWhere(_.id == user.id) match
      case -1 => Left(s"User with id '${user.id}' not found")
      case idx =>
        val usernameTaken = usersState.zipWithIndex.exists { case (u, i) =>
          i != idx && u.username.equalsIgnoreCase(user.username)
        }
        if usernameTaken then
          Left(s"Username '${user.username}' is already taken by another user")
        else
          val updated = usersState.updated(idx, user)
          persist(updated)
          usersState = updated
          logger.info(s"Updated user '${user.username}' (id: '${user.id}', role: ${user.role}, enabled: ${user.enabled})")
          Right(user)
  }

  def delete(id: UserId): Boolean = synchronized {
    val maybeUser = usersState.find(_.id == id)
    val filtered = usersState.filterNot(_.id == id)
    if filtered.length != usersState.length then
      persist(filtered)
      usersState = filtered
      val username = maybeUser.map(_.username).getOrElse(id)
      logger.info(s"Deleted user '$username' (id: '$id')")
      true
    else
      false
  }

  def reload(): Unit = synchronized {
    usersState = loadFromDisk()
  }
