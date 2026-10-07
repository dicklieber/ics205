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
import ics205.auth.{AuthConfig, User, UserId}
import ics205.util.{FileHelper, Locus}
import ics205.util.Ids.generateId
import ics205.util.Locus.admin
import io.circe.{Codec, Printer}
import io.circe.parser.*
import io.circe.syntax.*
import jakarta.inject.{Inject, Singleton}

import java.nio.file.NoSuchFileException

@Singleton class UserStore @Inject()(fileHelper: FileHelper,
                                     config: AuthConfig) extends LazyLogging:
  private val fileName: String = config.userFileName
  private var users: Seq[User] = loadFromDisk()

  def this(fileHelper: FileHelper) = this(fileHelper, AuthConfig())

  def all(): Seq[User] =
    users.sorted

  def findById(id: UserId): Option[User] =
    users.find(_.id == id)

  def findByUsername(username: String): Option[User] =
    users.find(_.username.equalsIgnoreCase(username))

  def add(user: User): Either[String, User] = synchronized {
    if users.exists(_.id == user.id) then
      Left(s"User with id '${user.id}' already exists")
    else if users.exists(_.username.equalsIgnoreCase(user.username)) then
      Left(s"Username '${user.username}' is already taken")
    else
      val updated = users :+ user
      persist(updated)
      users = updated
      logger.info(s"Added user '${user.username}' (id: '${user.id}', role: ${user.role}, enabled: ${user.enabled})")
      Right(user)
  }

  def save(user: User): Either[String, User] = synchronized {
    users.indexWhere(_.id == user.id) match
      case -1 => Left(s"User with id '${user.id}' not found")
      case idx =>
        val usernameTaken = users.zipWithIndex.exists { case (u, i) =>
          i != idx && u.username.equalsIgnoreCase(user.username)
        }
        if usernameTaken then
          Left(s"Username '${user.username}' is already taken by another user")
        else
          val updated = users.updated(idx, user)
          persist(updated)
          users = updated
          logger.info(s"Updated user '${user.username}' (id: '${user.id}', role: ${user.role}, enabled: ${user.enabled})")
          Right(user)
  }

  private def persist(usersToSave: Seq[User]): Unit = synchronized {
    fileHelper.save(admin, fileName, Users(usersToSave))
  }

  def delete(id: UserId): Boolean = synchronized {
    val maybeUser = users.find(_.id == id)
    val filtered = users.filterNot(_.id == id)
    if filtered.length != users.length then
      persist(filtered)
      users = filtered
      val username = maybeUser.map(_.username).getOrElse(id)
      logger.info(s"Deleted user '$username' (id: '$id')")
      true
    else
      false
  }

  def reload(): Unit = synchronized {
    users = loadFromDisk()
  }

  private def loadFromDisk(): Seq[User] =
    fileHelper.loadOrDefault[Users](admin, fileName)(Users()).users
  

case class Users(users: Seq[User] = Seq.empty) derives Codec.AsObject