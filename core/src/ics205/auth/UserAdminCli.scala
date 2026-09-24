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

    var parsedUsername: Option[String] = None
    var parsedRoles: Option[Set[String]] = None
    var parsedPassword: Option[String] = None

    var i = 0
    while i < args.length do
      args(i) match
        case "--username" if i + 1 < args.length =>
          parsedUsername = Some(args(i + 1).trim)
          i += 2
        case "--password" if i + 1 < args.length =>
          parsedPassword = Some(args(i + 1))
          i += 2
        case "--roles" if i + 1 < args.length =>
          val r = args(i + 1).split(",").map(_.trim).filter(_.nonEmpty).toSet
          parsedRoles = Some(r)
          i += 2
        case "--create-user" | "-u" if i + 1 < args.length && !args(i + 1).startsWith("-") =>
          if parsedUsername.isEmpty then
            parsedUsername = Some(args(i + 1).trim)
            i += 2
          else
            i += 1
        case _ =>
          i += 1

    val console = System.console()

    val username = parsedUsername.getOrElse {
      if console != null then
        val input = console.readLine("Username: ")
        if input == null then
          System.err.println("Error: Standard input is unavailable. If invoking via Mill, use the -i flag: mill -i core.runMain ics205.auth.UserAdminCli")
          sys.exit(1)
        input.trim
      else
        val line = scala.io.StdIn.readLine("Username: ")
        if line == null then
          System.err.println("Error: Standard input is unavailable. If invoking via Mill, use the -i flag: mill -i core.runMain ics205.auth.UserAdminCli")
          sys.exit(1)
        line.trim
    }

    if username.isEmpty then
      System.err.println("Error: Username cannot be empty.")
      sys.exit(1)

    val roles = parsedRoles.getOrElse {
      val rolesInput = if console != null then
        val input = console.readLine("Roles (comma-separated, default 'admin'): ")
        if input == null then "" else input.trim
      else
        val line = scala.io.StdIn.readLine("Roles (comma-separated, default 'admin'): ")
        if line == null then "" else line.trim

      if rolesInput.isEmpty then Set("admin")
      else rolesInput.split(",").map(_.trim).filter(_.nonEmpty).toSet
    }

    val password = parsedPassword.getOrElse {
      if console != null then
        val chars = console.readPassword("Password: ")
        if chars == null then
          System.err.println("Error: Standard input is unavailable. If invoking via Mill, use the -i flag: mill -i core.runMain ics205.auth.UserAdminCli")
          sys.exit(1)
        new String(chars)
      else
        val line = scala.io.StdIn.readLine("Password: ")
        if line == null then
          System.err.println("Error: Standard input is unavailable. If invoking via Mill, use the -i flag: mill -i core.runMain ics205.auth.UserAdminCli")
          sys.exit(1)
        line
    }

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
