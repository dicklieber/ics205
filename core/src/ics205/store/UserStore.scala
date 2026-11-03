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
import ics205.auth.{AuthConfig, Role, User, UserId}
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
  @volatile private var users: Seq[User] = loadFromDisk(allowMissing = true)

  private var bootstrapToken: Option[String] =
    if users.nonEmpty then None
    else
      val bytes = new Array[Byte](12)
      new java.security.SecureRandom().nextBytes(bytes)
      val token = java.util.Base64.getUrlEncoder.withoutPadding().encodeToString(bytes)
      System.err.println(s"Initial ICS-205 setup token: $token\nSave this token and enter it on your initial connection to create the administrator. It stops working once the first user is created. Restart before setup to generate a new token.")
      Some(token)

  private[ics205] def initialSetupToken: Option[String] = synchronized { bootstrapToken }

  def this(fileHelper: FileHelper) = this(fileHelper, AuthConfig())

  def all(): Seq[User] =
    users.sorted

  def findById(id: UserId): Option[User] =
    users.find(_.id == id)

  def findByUsername(username: String): Option[User] =
    users.find(_.username.equalsIgnoreCase(username))

  def canBootstrap(token: String): Boolean = synchronized {
    users.isEmpty && bootstrapToken.exists { expected =>
      java.security.MessageDigest.isEqual(
        expected.getBytes(java.nio.charset.StandardCharsets.UTF_8),
        token.getBytes(java.nio.charset.StandardCharsets.UTF_8)
      )
    }
  }

  def initializeAdmin(user: User, token: String): Either[String, User] = synchronized {
    if !canBootstrap(token) then Left("Initial setup is unavailable or the bootstrap token is invalid.")
    else add(user.copy(role = Role.Admin, enabled = true))
  }

  def add(user: User): Either[String, User] = synchronized {
    if users.exists(_.id == user.id) then
      Left(s"User with id '${user.id}' already exists")
    else if users.exists(_.username.equalsIgnoreCase(user.username)) then
      Left(s"Username '${user.username}' is already taken")
    else
      val updated = users :+ user
      persist(updated)
      users = updated
      bootstrapToken = None
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
    val path = fileHelper.directory / admin.toString / fileName
    val tempPath = path / os.up / s".$fileName.${generateId()}.tmp"
    val json = Users(usersToSave).asJson.printWith(Printer.indented("  "))
    os.makeDir.all(path / os.up)
    os.write(tempPath, json, perms = "rw-------")
    try os.move(tempPath, path, replaceExisting = true, atomicMove = true)
    finally if os.exists(tempPath) then os.remove(tempPath)
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
    users = loadFromDisk(allowMissing = false)
    if users.nonEmpty then bootstrapToken = None
  }

  private def loadFromDisk(allowMissing: Boolean): Seq[User] =
    val path = fileHelper.directory / admin.toString / fileName
    try
      decode[Users](os.read(path)).fold(
        error => throw new IllegalStateException(s"Invalid user database: $path", error),
        _.users
      )
    catch
      case _: NoSuchFileException if allowMissing => Seq.empty

  

case class Users(users: Seq[User] = Seq.empty) derives Codec.AsObject
