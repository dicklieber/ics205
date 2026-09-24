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

import ics205.store.UserStore
import ics205.util.FileHelper

import java.util.UUID

object UserAdminCli:
  def main(args: Array[String]): Unit =
    val fileHelper = new FileHelper()
    val config = AuthConfig()
    val userStore = new UserStore(fileHelper, config)
    val passwordService = new ScalaPassPasswordService()

    println("=== ICS-205 User Creation Tool ===")
    val console = System.console()

    val username = if console != null then
      console.readLine("Username: ").trim
    else
      val line = scala.io.StdIn.readLine("Username: ")
      if line == null then "" else line.trim

    if username.isEmpty then
      System.err.println("Error: Username cannot be empty.")
      sys.exit(1)

    val rolesInput = if console != null then
      console.readLine("Roles (comma-separated, default 'admin'): ").trim
    else
      val line = scala.io.StdIn.readLine("Roles (comma-separated, default 'admin'): ")
      if line == null then "" else line.trim

    val roles = if rolesInput.isEmpty then Set("admin") else rolesInput.split(",").map(_.trim).filter(_.nonEmpty).toSet

    val password = if console != null then
      val chars = console.readPassword("Password: ")
      if chars == null then "" else new String(chars)
    else
      val line = scala.io.StdIn.readLine("Password: ")
      if line == null then "" else line

    if password.isEmpty then
      System.err.println("Error: Password cannot be empty.")
      sys.exit(1)

    val hash = passwordService.hash(password)
    val user = User(
      id = UUID.randomUUID().toString,
      username = username,
      passwordHash = hash,
      roles = roles,
      enabled = true
    )

    userStore.add(user) match
      case Right(u) =>
        println(s"User '${u.username}' (id: ${u.id}) created successfully with roles: ${u.roles.mkString(", ")}")
      case Left(err) =>
        System.err.println(s"Error creating user: $err")
        sys.exit(1)
