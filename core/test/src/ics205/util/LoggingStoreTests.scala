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

package ics205.util

import org.apache.logging.log4j.Level

class LoggingStoreTests extends munit.FunSuite:

  private def withTempStore(test: (os.Path, FileHelper, LoggingStore) => Unit): Unit =
    val tempDir = os.temp.dir()
    try
      val fileHelper = new FileHelper(tempDir)
      val store = new LoggingStore(fileHelper)
      test(tempDir, fileHelper, store)
    finally
      os.remove.all(tempDir)

  test("persists logger levels in Locus.config and dynamically sets Log4j2 level"):
    withTempStore { (dir, fileHelper, store) =>
      assertEquals(store.getAllConfigured(), Map.empty)

      val targetLogger = "ics205.util.LoggingStoreTests"
      store.setLevel(targetLogger, "DEBUG")

      // Verify returned level from store
      assertEquals(store.getLevel(targetLogger), Some("DEBUG"))
      assertEquals(store.getAllConfigured(), Map(targetLogger -> "DEBUG"))

      // Verify file written in Locus.config directory (dir / "config" / "loggers.json")
      val configPath = dir / "config" / "loggers.json"
      assert(os.exists(configPath), s"File $configPath should exist")
      val content = os.read(configPath)
      assert(content.contains("ics205.util.LoggingStoreTests"))
      assert(content.contains("DEBUG"))

      // Verify effective Log4j2 level
      assertEquals(store.getEffectiveLevel(targetLogger), "DEBUG")

      // Re-load with a new store instance pointing to same directory
      val newStore = new LoggingStore(fileHelper)
      assertEquals(newStore.getAllConfigured(), Map(targetLogger -> "DEBUG"))
    }

  test("resetLevel removes logger from config and resets in Log4j2"):
    withTempStore { (_, _, store) =>
      val targetLogger = "ics205.util.ResetTestLogger"
      store.setLevel(targetLogger, "TRACE")
      assertEquals(store.getLevel(targetLogger), Some("TRACE"))

      store.resetLevel(targetLogger)
      assertEquals(store.getLevel(targetLogger), None)
      assertEquals(store.getAllConfigured().get(targetLogger), None)
    }

  test("applyPersisted loads and applies all levels to Log4j2"):
    withTempStore { (dir, fileHelper, store) =>
      val l1 = "ics205.test.L1"
      val l2 = "ics205.test.L2"
      store.setLevel(l1, "WARN")
      store.setLevel(l2, "ERROR")

      // Create a fresh store instance and apply
      val freshStore = new LoggingStore(fileHelper)
      freshStore.applyPersisted()

      assertEquals(freshStore.getEffectiveLevel(l1), "WARN")
      assertEquals(freshStore.getEffectiveLevel(l2), "ERROR")
    }
