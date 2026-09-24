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

import ics205.auth.{AuthConfig, User}
import ics205.util.FileHelper

class UserStoreTests extends munit.FunSuite:
  private def withDirectory(test: os.Path => Unit): Unit =
    val directory = os.temp.dir()
    try test(directory)
    finally os.remove.all(directory)

  private def helper(path: os.Path): FileHelper = new FileHelper:
    override val directory: os.Path = path

  private val testUser = User(
    id = "user-1",
    username = "admin",
    passwordHash = "hash123",
    roles = Set("admin"),
    enabled = true
  )

  test("missing file loads empty user list without error"):
    withDirectory { dir =>
      val store = new UserStore(helper(dir))
      assertEquals(store.all(), Seq.empty)
      assertEquals(store.findById("user-1"), None)
      assertEquals(store.findByUsername("admin"), None)
    }

  test("add user persists to JSON and is findable case-insensitively"):
    withDirectory { dir =>
      val store = new UserStore(helper(dir))
      val added = store.add(testUser)
      assertEquals(added, Right(testUser))
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

      val dupName = User("user-2", "ADMIN", "hash2", Set("user"))
      assert(store.add(dupName).isLeft)

      val dupId = User("user-1", "other", "hash3", Set("user"))
      assert(store.add(dupId).isLeft)
    }

  test("update user modifies state and persists to disk"):
    withDirectory { dir =>
      val store = new UserStore(helper(dir))
      store.add(testUser)
      val updated = testUser.copy(roles = Set("admin", "editor"), enabled = false)
      assertEquals(store.update(updated), Right(updated))
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

  test("corrupted or malformed user JSON defaults gracefully to empty"):
    withDirectory { dir =>
      os.write(dir / "users.json", "{ malformed json content }")
      val store = new UserStore(helper(dir))
      assertEquals(store.all(), Seq.empty)
    }
