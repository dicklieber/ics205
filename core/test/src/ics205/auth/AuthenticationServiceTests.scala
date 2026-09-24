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

import ics205.store.{InMemJsonSessionStore, UserStore}
import ics205.util.FileHelper

import java.time.Duration

class AuthenticationServiceTests extends munit.FunSuite:
  private def withDirectory(test: (os.Path, AuthenticationService, UserStore, InMemJsonSessionStore, PasswordService) => Unit): Unit =
    val directory = os.temp.dir()
    try
      val helper = new FileHelper(directory)
      val userStore = new UserStore(helper)
      val sessionStore = new InMemJsonSessionStore(helper)
      val passwordService = new ScalaPassPasswordService()
      val authService = new AuthenticationService(userStore, passwordService, sessionStore)
      test(directory, authService, userStore, sessionStore, passwordService)
    finally
      os.remove.all(directory)

  test("successful login creates valid session and authenticates session correctly"):
    withDirectory { (_, authService, userStore, _, passwordService) =>
      val hash = passwordService.hash("secret123")
      val user = User("user-1", "operator", hash, RolePermissions.Editor, enabled = true)
      userStore.add(user)

      val maybeSession = authService.authenticate("operator", "secret123")
      assert(maybeSession.isDefined)
      val session = maybeSession.get
      assertEquals(session.userId, "user-1")

      val authenticated = authService.authenticateSession(session.id)
      assertEquals(authenticated, Right(AuthenticatedUser("user-1", "operator", RolePermissions.Editor)))
    }

  test("failed login with unknown username or wrong password returns None without leaking details"):
    withDirectory { (_, authService, userStore, _, passwordService) =>
      val hash = passwordService.hash("secret123")
      val user = User("user-1", "operator", hash, RolePermissions.User, enabled = true)
      userStore.add(user)

      assertEquals(authService.authenticate("nonexistent", "secret123"), None)
      assertEquals(authService.authenticate("operator", "wrongpass"), None)
    }

  test("disabled user cannot authenticate via login"):
    withDirectory { (_, authService, userStore, _, passwordService) =>
      val hash = passwordService.hash("secret123")
      val user = User("user-1", "disabledUser", hash, RolePermissions.User, enabled = false)
      userStore.add(user)

      assertEquals(authService.authenticate("disabledUser", "secret123"), None)
    }

  test("nonexistent or expired session fails to authenticate"):
    withDirectory { (dir, _, userStore, _, passwordService) =>
      val hash = passwordService.hash("secret123")
      val user = User("user-1", "operator", hash, RolePermissions.User, enabled = true)
      userStore.add(user)

      val helper = new FileHelper(dir)
      val shortLifetimeConfig = AuthConfig(sessionLifetime = Duration.ofMillis(1))
      val shortSessionStore = new InMemJsonSessionStore(helper, shortLifetimeConfig)
      val authService = new AuthenticationService(userStore, passwordService, shortSessionStore)

      val session = shortSessionStore.create("user-1")
      Thread.sleep(10)

      assert(authService.authenticateSession(session.id).isLeft)
      assert(authService.authenticateSession("nonexistent-id").isLeft)
    }

  test("role changes in UserStore take effect immediately for existing sessions"):
    withDirectory { (_, authService, userStore, _, passwordService) =>
      val hash = passwordService.hash("secret123")
      val user = User("user-1", "operator", hash, RolePermissions.User, enabled = true)
      userStore.add(user)

      val session = authService.authenticate("operator", "secret123").get
      assertEquals(authService.authenticateSession(session.id), Right(AuthenticatedUser("user-1", "operator", RolePermissions.User)))

      // Change user role in UserStore
      userStore.update(user.copy(role = RolePermissions.Admin))
      assertEquals(authService.authenticateSession(session.id), Right(AuthenticatedUser("user-1", "operator", RolePermissions.Admin)))
    }

  test("disabling a user invalidates access immediately for existing sessions"):
    withDirectory { (_, authService, userStore, _, passwordService) =>
      val hash = passwordService.hash("secret123")
      val user = User("user-1", "operator", hash, RolePermissions.User, enabled = true)
      userStore.add(user)

      val session = authService.authenticate("operator", "secret123").get
      assert(authService.authenticateSession(session.id).isRight)

      // Disable user in UserStore
      userStore.update(user.copy(enabled = false))
      assert(authService.authenticateSession(session.id).isLeft)
    }
