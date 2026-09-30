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
import ics205.util.{FileHelper, Ids}

object UserAdminCli:
  def main(args: Array[String]): Unit =
    val fileHelper = new FileHelper()
    val config = AuthConfig()
    val userStore = new UserStore(fileHelper, config)
    val passwordService = new ScalaPassPasswordService()
    val exitCode = run(args, userStore, passwordService)
    if exitCode != 0 then sys.exit(exitCode)

  def run(
    args: Array[String],
    userStore: UserStore,
    passwordService: PasswordService,
    inReader: Option[java.io.BufferedReader] = None,
    out: java.io.PrintStream = System.out,
    err: java.io.PrintStream = System.err
  ): Int =
    out.println("=== ICS-205 User Creation Tool ===")

    var parsedUsername: Option[String] = None
    var parsedRole: Option[RolePermissions] = None
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
        case "--role" | "--roles" if i + 1 < args.length =>
          parsedRole = RolePermissions.fromString(args(i + 1).trim)
          i += 2
        case "--create-user" | "-u" if i + 1 < args.length && !args(i + 1).startsWith("-") =>
          if parsedUsername.isEmpty then
            parsedUsername = Some(args(i + 1).trim)
            i += 2
          else
            i += 1
        case _ =>
          i += 1

    def readLine(prompt: String): Option[String] =
      inReader match
        case Some(reader) =>
          out.print(prompt)
          Option(reader.readLine()).map(_.trim)
        case None =>
          val console = System.console()
          if console != null then
            Option(console.readLine(prompt)).map(_.trim)
          else
            Option(scala.io.StdIn.readLine(prompt)).map(_.trim)

    def readPassword(prompt: String): Option[String] =
      inReader match
        case Some(reader) =>
          out.print(prompt)
          Option(reader.readLine())
        case None =>
          val console = System.console()
          if console != null then
            Option(console.readPassword(prompt)).map(new String(_))
          else
            Option(scala.io.StdIn.readLine(prompt))

    val username = parsedUsername.orElse(readLine("Username: ")) match
      case None =>
        err.println("Error: Standard input is unavailable. If invoking via Mill, use the -i flag: mill -i core.runMain ics205.auth.UserAdminCli")
        return 1
      case Some(u) => u

    if username.isEmpty then
      err.println("Error: Username cannot be empty.")
      return 1

    val role = parsedRole.getOrElse {
      val roleInput = readLine(s"Role [${RolePermissions.values.mkString(", ")}] (default 'Admin'): ").getOrElse("")
      if roleInput.isEmpty then RolePermissions.Admin
      else RolePermissions.fromString(roleInput).getOrElse {
        out.println(s"Unknown role '$roleInput', defaulting to 'Admin'")
        RolePermissions.Admin
      }
    }

    val password = parsedPassword.orElse(readPassword("Password: ")) match
      case None =>
        err.println("Error: Standard input is unavailable. If invoking via Mill, use the -i flag: mill -i core.runMain ics205.auth.UserAdminCli")
        return 1
      case Some(p) => p

    if password.isEmpty then
      err.println("Error: Password cannot be empty.")
      return 1

    if password.length < 8 then
      err.println("Error: Password must be at least 8 characters.")
      return 1

    val hash = passwordService.hash(password)
    val user = User(
      username = username,
      passwordHash = hash,
      role = role,
      enabled = true
    )

    userStore.add(user) match
      case Right(u) =>
        out.println(s"User '${u.username}' (id: ${u.id}) created successfully with role: ${u.role}")
        0
      case Left(error) =>
        err.println(s"Error creating user: $error")
        1
