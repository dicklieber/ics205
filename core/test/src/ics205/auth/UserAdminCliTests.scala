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
import java.io.{BufferedReader, ByteArrayOutputStream, PrintStream, StringReader}

class UserAdminCliTests extends munit.FunSuite:

  private def withTempStore(test: (UserStore, PasswordService) => Unit): Unit =
    val dir = os.temp.dir()
    try
      val helper = new FileHelper(dir)
      val config = AuthConfig()
      val userStore = new UserStore(helper, config)
      val passwordService = new ScalaPassPasswordService()
      test(userStore, passwordService)
    finally
      os.remove.all(dir)

  test("UserAdminCli creates user via command-line arguments"):
    withTempStore { (userStore, passwordService) =>
      val out = new ByteArrayOutputStream()
      val err = new ByteArrayOutputStream()
      val exitCode = UserAdminCli.run(
        args = Array("--username", "adminuser", "--password", "secretpass123", "--role", "Admin"),
        userStore = userStore,
        passwordService = passwordService,
        out = new PrintStream(out),
        err = new PrintStream(err)
      )

      assertEquals(exitCode, 0)
      val created = userStore.findByUsername("adminuser")
      assert(created.isDefined)
      assertEquals(created.get.role, RolePermissions.Admin)
      assert(passwordService.verify("secretpass123", created.get.passwordHash))
      assert(out.toString.contains("created successfully"))
    }

  test("UserAdminCli supports --roles and --create-user flags"):
    withTempStore { (userStore, passwordService) =>
      val out = new ByteArrayOutputStream()
      val err = new ByteArrayOutputStream()
      val exitCode = UserAdminCli.run(
        args = Array("--create-user", "techuser", "--password", "techpass", "--roles", "Editor"),
        userStore = userStore,
        passwordService = passwordService,
        out = new PrintStream(out),
        err = new PrintStream(err)
      )

      assertEquals(exitCode, 0)
      val created = userStore.findByUsername("techuser")
      assert(created.isDefined)
      assertEquals(created.get.role, RolePermissions.Editor)
    }

  test("UserAdminCli supports -u flag"):
    withTempStore { (userStore, passwordService) =>
      val out = new ByteArrayOutputStream()
      val err = new ByteArrayOutputStream()
      val exitCode = UserAdminCli.run(
        args = Array("-u", "operator1", "--password", "oppass", "--role", "User"),
        userStore = userStore,
        passwordService = passwordService,
        out = new PrintStream(out),
        err = new PrintStream(err)
      )

      assertEquals(exitCode, 0)
      val created = userStore.findByUsername("operator1")
      assert(created.isDefined)
      assertEquals(created.get.role, RolePermissions.User)
    }

  test("UserAdminCli interactive prompt creates user and handles role fallback"):
    withTempStore { (userStore, passwordService) =>
      val input = "interactiveUser\nInvalidRole\nmyPassword123\n"
      val reader = new BufferedReader(new StringReader(input))
      val out = new ByteArrayOutputStream()
      val err = new ByteArrayOutputStream()

      val exitCode = UserAdminCli.run(
        args = Array.empty,
        userStore = userStore,
        passwordService = passwordService,
        inReader = Some(reader),
        out = new PrintStream(out),
        err = new PrintStream(err)
      )

      assertEquals(exitCode, 0)
      val created = userStore.findByUsername("interactiveUser")
      assert(created.isDefined)
      assertEquals(created.get.role, RolePermissions.Admin)
      assert(out.toString.contains("Unknown role 'InvalidRole', defaulting to 'Admin'"))
    }

  test("UserAdminCli interactive prompt with empty role defaults to Admin"):
    withTempStore { (userStore, passwordService) =>
      val input = "defaultRoleUser\n\nmyPassword123\n"
      val reader = new BufferedReader(new StringReader(input))
      val out = new ByteArrayOutputStream()
      val err = new ByteArrayOutputStream()

      val exitCode = UserAdminCli.run(
        args = Array.empty,
        userStore = userStore,
        passwordService = passwordService,
        inReader = Some(reader),
        out = new PrintStream(out),
        err = new PrintStream(err)
      )

      assertEquals(exitCode, 0)
      val created = userStore.findByUsername("defaultRoleUser")
      assert(created.isDefined)
      assertEquals(created.get.role, RolePermissions.Admin)
    }

  test("UserAdminCli rejects empty username"):
    withTempStore { (userStore, passwordService) =>
      val out = new ByteArrayOutputStream()
      val err = new ByteArrayOutputStream()
      val exitCode = UserAdminCli.run(
        args = Array("--username", "   ", "--password", "secret"),
        userStore = userStore,
        passwordService = passwordService,
        out = new PrintStream(out),
        err = new PrintStream(err)
      )

      assertEquals(exitCode, 1)
      assert(err.toString.contains("Username cannot be empty"))
    }

  test("UserAdminCli rejects empty password"):
    withTempStore { (userStore, passwordService) =>
      val out = new ByteArrayOutputStream()
      val err = new ByteArrayOutputStream()
      val exitCode = UserAdminCli.run(
        args = Array("--username", "validuser", "--password", ""),
        userStore = userStore,
        passwordService = passwordService,
        out = new PrintStream(out),
        err = new PrintStream(err)
      )

      assertEquals(exitCode, 1)
      assert(err.toString.contains("Password cannot be empty"))
    }

  test("UserAdminCli fails on duplicate username"):
    withTempStore { (userStore, passwordService) =>
      userStore.add(User("existinguser", passwordService.hash("pass"), RolePermissions.User, true))

      val out = new ByteArrayOutputStream()
      val err = new ByteArrayOutputStream()
      val exitCode = UserAdminCli.run(
        args = Array("--username", "existinguser", "--password", "secretpass"),
        userStore = userStore,
        passwordService = passwordService,
        out = new PrintStream(out),
        err = new PrintStream(err)
      )

      assertEquals(exitCode, 1)
      assert(err.toString.contains("Error creating user"))
    }

  test("UserAdminCli returns error when input stream is closed unexpectedly"):
    withTempStore { (userStore, passwordService) =>
      val reader = new BufferedReader(new StringReader(""))
      val out = new ByteArrayOutputStream()
      val err = new ByteArrayOutputStream()
      val exitCode = UserAdminCli.run(
        args = Array.empty,
        userStore = userStore,
        passwordService = passwordService,
        inReader = Some(reader),
        out = new PrintStream(out),
        err = new PrintStream(err)
      )

      assertEquals(exitCode, 1)
      assert(err.toString.contains("Standard input is unavailable"))
    }
