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

import ics205.auth.AuthConfig
import ics205.util.FileHelper

import java.time.Duration
import java.util.concurrent.{CountDownLatch, Executors}
import scala.concurrent.{ExecutionContext, Future}

class SessionStoreTests extends munit.FunSuite:
  private def withDirectory(test: os.Path => Unit): Unit =
    val directory = os.temp.dir()
    try test(directory)
    finally os.remove.all(directory)

  private def helper(path: os.Path): FileHelper = new FileHelper(path)

  test("create session creates in-memory session and persists to JSON"):
    withDirectory { dir =>
      val store = new InMemJsonSessionStore(helper(dir))
      val session = store.create("user-123")
      assert(session.id.nonEmpty)
      assertEquals(session.userId, "user-123")
      assert(session.expiresAt.isAfter(session.createdAt))

      val found = store.find(session.id)
      assertEquals(found, Some(session))

      // Fresh store loads persisted session from disk
      val freshStore = new InMemJsonSessionStore(helper(dir))
      assertEquals(freshStore.find(session.id), Some(session))
    }

  test("delete session removes from memory and disk"):
    withDirectory { dir =>
      val store = new InMemJsonSessionStore(helper(dir))
      val session = store.create("user-123")
      store.delete(session.id)
      assertEquals(store.find(session.id), None)

      val freshStore = new InMemJsonSessionStore(helper(dir))
      assertEquals(freshStore.find(session.id), None)
    }

  test("deleteAllForUser removes all user sessions"):
    withDirectory { dir =>
      val store = new InMemJsonSessionStore(helper(dir))
      val s1 = store.create("user-1")
      val s2 = store.create("user-1")
      val s3 = store.create("user-2")

      store.deleteAllForUser("user-1")
      assertEquals(store.find(s1.id), None)
      assertEquals(store.find(s2.id), None)
      assertEquals(store.find(s3.id), Some(s3))

      val freshStore = new InMemJsonSessionStore(helper(dir))
      assertEquals(freshStore.find(s1.id), None)
      assertEquals(freshStore.find(s2.id), None)
      assertEquals(freshStore.find(s3.id), Some(s3))
    }

  test("expired sessions are not returned and are discarded on startup"):
    withDirectory { dir =>
      val shortLifetimeConfig = AuthConfig(sessionLifetime = Duration.ofMillis(1))
      val store = new InMemJsonSessionStore(helper(dir), shortLifetimeConfig)
      val session = store.create("user-1")
      Thread.sleep(10) // Ensure expiration

      assertEquals(store.find(session.id), None)

      // Restart store: expired session should be discarded during startup load
      val reloadedStore = new InMemJsonSessionStore(helper(dir))
      assertEquals(reloadedStore.find(session.id), None)
    }

  test("corrupted or malformed session JSON defaults gracefully to empty"):
    withDirectory { dir =>
      os.write(dir / "sessions.json", "{ not valid json }")
      val store = new InMemJsonSessionStore(helper(dir))
      assertEquals(store.all(), Seq.empty)
    }

  test("concurrent session creations and deletions remain safe and consistent"):
    withDirectory { dir =>
      val store = new InMemJsonSessionStore(helper(dir))
      val pool = Executors.newFixedThreadPool(8)
      implicit val ec: ExecutionContext = ExecutionContext.fromExecutor(pool)

      val count = 50
      val latch = new CountDownLatch(1)

      val futures = (0 until count).map { i =>
        Future {
          latch.await()
          val session = store.create(s"user-$i")
          if i % 2 == 0 then
            store.delete(session.id)
          session
        }
      }

      latch.countDown()
      val results = scala.concurrent.Await.result(Future.sequence(futures), scala.concurrent.duration.Duration.Inf)
      pool.shutdown()

      // Half should still exist
      val remaining = results.zipWithIndex.filter(_._2 % 2 != 0).map(_._1)
      val reloaded = new InMemJsonSessionStore(helper(dir))
      assertEquals(reloaded.all().length, remaining.length)
      remaining.foreach { s =>
        assertEquals(reloaded.find(s.id), Some(s))
      }
    }
