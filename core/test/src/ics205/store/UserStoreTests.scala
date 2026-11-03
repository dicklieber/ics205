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

import ics205.auth.{AuthConfig, Role, User}
import ics205.util.FileHelper

class UserStoreTests extends munit.FunSuite:
  private def withDirectory(test: os.Path => Unit): Unit =
    val directory = os.temp.dir()
    try test(directory)
    finally os.remove.all(directory)

  private def helper(path: os.Path): FileHelper = new FileHelper(path)

  private val testUser = User(
    username = "admin",
    passwordHash = "hash123",
    role = Role.Admin,
    enabled = true,
    id = "user-1"
  )

  test("missing file loads empty user list without creating users.json on initialization"):
    withDirectory { dir =>
      val store = new UserStore(helper(dir))
      assertEquals(store.all(), Seq.empty)
      assertEquals(store.findById("user-1"), None)
      assertEquals(store.findByUsername("admin"), None)
      assert(!os.exists(dir / "admin" / "users.json"))
    }

  test("add user persists to JSON and is findable case-insensitively"):
    withDirectory { dir =>
      val store = new UserStore(helper(dir))
      assert(!os.exists(dir / "admin" / "users.json"))
      val added = store.add(testUser)
      assertEquals(added, Right(testUser))
      assert(os.exists(dir / "admin" / "users.json"))
      assertEquals(store.findById("user-1"), Some(testUser))
      assertEquals(store.findByUsername("admin"), Some(testUser))
      assertEquals(store.findByUsername("ADMIN"), Some(testUser))
      assertEquals(store.findByUsername("Admin"), Some(testUser))

      // Reloading from disk in a fresh store
      val freshStore = new UserStore(helper(dir))
      assertEquals(freshStore.findById("user-1"), Some(testUser))
      assertEquals(freshStore.findByUsername("admin"), Some(testUser))
    }

  test("duplicate username case-insensitively or duplicate ID is rejected"):
    withDirectory { dir =>
      val store = new UserStore(helper(dir))
      assertEquals(store.add(testUser), Right(testUser))

      val dupName = User("ADMIN", "hash2", Role.Viewer, enabled = true, id = "user-2")
      assert(store.add(dupName).isLeft)

      val dupId = User("other", "hash3", Role.Viewer, enabled = true, id = "user-1")
      assert(store.add(dupId).isLeft)
    }

  test("update user modifies state and persists to disk"):
    withDirectory { dir =>
      val store = new UserStore(helper(dir))
      store.add(testUser)
      val updated = testUser.copy(role = Role.Editor, enabled = false)
      assertEquals(store.save(updated), Right(updated))
      assertEquals(store.findById("user-1"), Some(updated))

      val freshStore = new UserStore(helper(dir))
      assertEquals(freshStore.findById("user-1"), Some(updated))
    }

  test("delete user removes user from state and disk"):
    withDirectory { dir =>
      val store = new UserStore(helper(dir))
      store.add(testUser)
      assert(store.delete("user-1"))
      assertEquals(store.findById("user-1"), None)
      assert(!store.delete("user-1"))

      val freshStore = new UserStore(helper(dir))
      assertEquals(freshStore.findById("user-1"), None)
    }

  test("corrupted or malformed user JSON prevents startup"):
    withDirectory { dir =>
      os.write(dir / "admin" / "users.json", "{ malformed json content }", createFolders = true)
      intercept[IllegalStateException](new UserStore(helper(dir)))
    }

  test("all returns users sorted by username case-insensitively"):
    withDirectory { dir =>
      val store = new UserStore(helper(dir))
      store.add(User("charlie", "hash", Role.Viewer, enabled = true, id = "u-3"))
      store.add(User("Alice", "hash", Role.Viewer, enabled = true, id = "u-1"))
      store.add(User("bob", "hash", Role.Viewer, enabled = true, id = "u-2"))
      assertEquals(store.all().map(_.username), Seq("Alice", "bob", "charlie"))
    }

  test("persists and reloads user groups"):
    withDirectory { dir =>
      val store = new UserStore(helper(dir))
      val userWithGroups = User("operator", "hash", Role.Viewer, enabled = true, id = "u-op", groups = Set("Ares Operations", "North Team"))
      store.add(userWithGroups)

      val loaded = store.findById("u-op").get
      assertEquals(loaded.groups, Set("Ares Operations", "North Team"))

      val freshStore = new UserStore(helper(dir))
      val reloaded = freshStore.findById("u-op").get
      assertEquals(reloaded.groups, Set("Ares Operations", "North Team"))
    }

  test("failed reload preserves users and does not enable bootstrap"):
    withDirectory { dir =>
      val store = new UserStore(helper(dir), AuthConfig())
      val token = store.initialSetupToken.get
      store.add(testUser)
      val path = dir / "admin" / "users.json"
      for content <- Seq("{bad", "{}", "[]") do
        os.write.over(path, content)
        intercept[IllegalStateException](store.reload())
        assertEquals(store.all(), Seq(testUser))
        assert(!store.canBootstrap(token))
      os.remove(path)
      intercept[java.nio.file.NoSuchFileException](store.reload())
      assertEquals(store.all(), Seq(testUser))
    }

  test("unreadable user database path prevents startup"):
    withDirectory { dir =>
      os.makeDir.all(dir / "admin" / "users.json")
      intercept[java.io.IOException](new UserStore(helper(dir)))
    }

  test("generated token is required, forces admin role, and is consumed after setup"):
    withDirectory { dir =>
      val store = new UserStore(helper(dir), AuthConfig())
      val token = store.initialSetupToken.get
      assert(token.matches("[0-9a-f]{64}"))
      assert(store.initializeAdmin(testUser, "").isLeft)
      assert(store.initializeAdmin(testUser, "wrong").isLeft)
      assertEquals(store.all(), Seq.empty)
      val candidate = testUser.copy(role = Role.Viewer, enabled = false)
      assertEquals(store.initializeAdmin(candidate, token), Right(testUser))
      assert(store.initializeAdmin(testUser.copy(id = "second", username = "second"), token).isLeft)
      assertEquals(store.initialSetupToken, None)
      store.delete(testUser.id)
      assert(!store.canBootstrap(token))
    }

  test("concurrent bootstrap requests can create only one administrator"):
    withDirectory { dir =>
      val store = new UserStore(helper(dir), AuthConfig())
      val token = store.initialSetupToken.get
      val start = new java.util.concurrent.CountDownLatch(1)
      val executor = java.util.concurrent.Executors.newFixedThreadPool(2)
      try
        val futures = (1 to 2).map { n =>
          executor.submit(new java.util.concurrent.Callable[Either[String, User]] {
            def call(): Either[String, User] =
              start.await()
              store.initializeAdmin(testUser.copy(id = s"user-$n", username = s"admin$n"), token)
          })
        }
        start.countDown()
        val results = futures.map(_.get(10, java.util.concurrent.TimeUnit.SECONDS))
        assertEquals(results.count(_.isRight), 1)
        assertEquals(store.all().size, 1)
        assertEquals(new UserStore(helper(dir)).all(), store.all())
      finally executor.shutdownNow()
    }

  test("restart before setup rotates token and an existing administrator suppresses generation"):
    withDirectory { dir =>
      val first = new UserStore(helper(dir))
      val oldToken = first.initialSetupToken.get
      val restarted = new UserStore(helper(dir))
      assert(restarted.initialSetupToken.get != oldToken)
      assert(!restarted.canBootstrap(oldToken))
      restarted.add(testUser)
      assertEquals(new UserStore(helper(dir)).initialSetupToken, None)
    }
