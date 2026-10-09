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

import ics205.auth.{AuthenticatedUser, Role, Session, User}
import ics205.util.FileHelper
import ics205.model.*
import io.circe.Json
import io.circe.syntax.*

import java.time.{Instant, LocalDateTime}

class Ics205StoreTests extends munit.FunSuite:
  private def withDirectory(test: os.Path => Unit): Unit =
    val directory = os.temp.dir()
    try test(directory)
    finally os.remove.all(directory)

  private def helper(path: os.Path): FileHelper = new FileHelper(path)

  private val now = LocalDateTime.of(2026, 9, 20, 12, 30)
  private val plan = Ics205(incidentName = "Test incident",
    operationalPeriod = OperationalPeriod(Some(now), Some(now.plusHours(12))),
    channels = Seq(Ics205Channel(id = "1",
      zoneGroup = Some("Command"),
      channelNumber = Some("1"),
      function = "Dispatch",
      name = "Repeater",
      assignment = "All teams",
      frequency = RxWithOffset(mhz"146.520"),
      mode = RadioMode.Digital,
      bandwidth = Bandwidth.Narrow,
      ctcss = Ctcss(Some(CtcssFrequency.Hz100_0), CtcssMode.Tone),
      remarks = "Test channel")),
    specialInstructions = "Monitor dispatch",
    preparedBy = Some(PreparedBy("Operator", Some("WA9NNN"))),
    prepared = now)

  test("missing file starts with no events"):
    withDirectory { directory =>
      val store = new Ics205Store(helper(directory))
      assertEquals(store.events().size, 0)
      assertEquals(store.currentEvent(), None)
      assertEquals(store.ics205().incidentName, "")
      assertEquals(store.ics205().channels, Seq.empty)
      assertEquals(store.ics205().operationalPeriod.from, None)
      assertEquals(store.ics205().operationalPeriod.to, None)
      assert(!os.exists(directory / "events") && !os.exists(directory / "ics205.json"))
    }

  test("file modified time comes from the event file and handles missing files"):
    withDirectory { directory =>
      val store = new Ics205Store(helper(directory))
      store.save(plan)
      val path = directory / "events" / store.findByName("Test incident").get.fileName
      val modified = Instant.parse("2026-10-06T15:30:45Z")
      java.nio.file.Files.setLastModifiedTime(path.toNIO, java.nio.file.attribute.FileTime.from(modified))
      assertEquals(store.fileModifiedAt("Test incident"), Some(modified))
      assertEquals(store.fileModifiedAt("Unknown"), None)
      os.remove(path)
      assertEquals(store.fileModifiedAt("Test incident"), None)
    }

  test("save updates memory and a new store loads all model fields"):
    withDirectory { directory =>
      val store = new Ics205Store(helper(directory))
      val event = Ics205Event(plan)
      store.save(event)
      val saved = store.ics205()
      assertEquals(saved.copy(prepared = plan.prepared), plan)
      assertEquals(new Ics205Store(helper(directory)).ics205(), saved)
      val eventFile = directory / "events" / event.fileName
      assert(os.exists(eventFile))
      os.write.over(eventFile, "invalid json")
      assertEquals(store.ics205(), saved)
      assertEquals(new Ics205Store(helper(directory)).ics205().incidentName, "")
    }

  test("save omits absent optional fields and reloads them as None"):
    withDirectory { directory =>
      val store = new Ics205Store(helper(directory))
      val sparse = plan.copy(
        operationalPeriod = OperationalPeriod(),
        preparedBy = None,
        channels = Seq(plan.channels.head.copy(remarks = ""))
      )
      val event = Ics205Event(sparse)
      store.save(event)
      val json = io.circe.parser.parse(os.read(directory / "events" / event.fileName)).toOption.get
      val ics205Cursor = json.hcursor.downField("ics205")
      assert(!ics205Cursor.keys.get.toSet.contains("preparedBy"))
      assertEquals(ics205Cursor.downField("operationalPeriod").focus, Some(io.circe.Json.obj()))
      val channel = ics205Cursor.downField("channels").downArray
      assertEquals(channel.get[String]("remarks"), Right(""))
      assert(!channel.keys.get.toSet.contains("digital"))
      assertEquals(new Ics205Store(helper(directory)).ics205(), store.ics205())
    }

  test("save with AuthenticatedUser saves the event to disk"):
    withDirectory { directory =>
      val store = new Ics205Store(helper(directory))
      val u = User("user-123", "hash", Role.Admin, enabled = true, id = "user-123")
      val user = AuthenticatedUser(
        u,
        Session(userId = u.id, createdAt = Instant.now(), expiresAt = Instant.now().plusSeconds(3600))
      )
      store.save(Ics205Event(plan), user)
      assertEquals(store.events().size, 1)

      val reloaded = new Ics205Store(helper(directory))
      assertEquals(reloaded.events().size, 1)
      assertEquals(reloaded.currentEvent().get.ics205.incidentName, "Test incident")
    }

  test("saving event preserves group across reloads"):
    withDirectory { directory =>
      val store = new Ics205Store(helper(directory))
      val event = Ics205Event("FieldOps", plan.copy(incidentName = "Field Ops Incident"), group = "Ares Ops")
      store.save(event)
      assertEquals(store.getEvent("FieldOps").get.group, "Ares Ops")

      val reloaded = new Ics205Store(helper(directory))
      assertEquals(reloaded.getEvent("FieldOps").get.group, "Ares Ops")
    }

  test("legacy ics205.json in base directory is ignored and not read"):
    withDirectory { directory =>
      val rawPlanJson = plan.asJson.noSpaces
      os.write(directory / "ics205.json", rawPlanJson)
      val store = new Ics205Store(helper(directory))
      assertEquals(store.events().size, 0)
      assertEquals(store.currentEvent(), None)
      assertEquals(store.ics205().incidentName, "")
      assertEquals(store.ics205().channels, Seq.empty)
    }

  test("raw plan JSON in events directory is not loaded as Ics205Event"):
    withDirectory { directory =>
      val rawPlanJson = plan.asJson.noSpaces
      os.write(directory / "events" / "Drill.json", rawPlanJson, createFolders = true)
      val store = new Ics205Store(helper(directory))
      assertEquals(store.events().size, 0)
      assertEquals(store.currentEvent(), None)
    }

  test("valid Ics205Event JSON in events directory loads successfully"):
    withDirectory { directory =>
      val event = Ics205Event("Drill", plan)
      os.write(directory / "events" / "Drill.json", event.asJson.noSpaces, createFolders = true)
      val store = new Ics205Store(helper(directory))
      assertEquals(store.events().size, 1)
      assertEquals(store.getEvent("Drill").map(_.eventName), Some(plan.incidentName))
      assertEquals(store.getEvent("Drill").map(_.ics205.incidentName), Some(plan.incidentName))
    }

  test("undecodable JSON in events directory loads the default"):
    withDirectory { directory =>
      os.write(directory / "events" / "corrupted.json", "{}", createFolders = true)
      assertEquals(new Ics205Store(helper(directory)).ics205().channels, Seq.empty)
    }

  test("failed save preserves the previous in-memory plan"):
    withDirectory { directory =>
      val store = new Ics205Store(helper(directory))
      val event = Ics205Event("Test incident", plan)
      store.save(event)
      val original = store.ics205()
      os.remove.all(directory / "events")
      os.write(directory / "events", "not a directory")
      intercept[java.io.IOException] { store.save(event) }
      assertEquals(store.ics205(), original)
    }

  test("legacy channel JSON ignores digital parameters and defaults absent or null remarks"):
    val expected = plan.channels.head.copy(remarks = "")
    val legacy = expected.asJson.mapObject(_.remove("remarks").add(
      "digital", Json.obj("Dmr" -> Json.obj(
        "colorCode" -> Json.fromInt(1), "timeSlot" -> Json.fromInt(2), "talkGroup" -> Json.fromInt(123)
      ))
    ))
    assertEquals(legacy.as[Ics205Channel], Right(expected))
    assertEquals(legacy.mapObject(_.add("remarks", Json.Null)).as[Ics205Channel], Right(expected))
    assert(!expected.asJson.hcursor.keys.get.toSet.contains("digital"))

  test("event and ics205Event return full event"):
    withDirectory { directory =>
      val store = new Ics205Store(helper(directory))
      assertEquals(store.event(), None)
      assertEquals(store.ics205Event(), None)
      val event = Ics205Event("Test Event", plan)
      store.save(event)
      assertEquals(store.event().get.ics205.incidentName, "Test incident")
      assertEquals(store.ics205Event().get.ics205.incidentName, "Test incident")
    }

  test("Ics205Store supports multiple events, lookup by eventName, adding and deleting events"):
    withDirectory { directory =>
      val store = new Ics205Store(helper(directory))
      val event1 = Ics205Event("Field Day", plan.copy(incidentName = "Field Day 2026"))
      val event2 = Ics205Event("Marathon", plan.copy(incidentName = "City Marathon"))

      store.save(event1)
      store.save(event2)

      assert(os.exists(directory / "events" / event1.fileName))
      assert(os.exists(directory / "events" / event2.fileName))

      assertEquals(store.events().size, 2)
      assertEquals(store.listEvents().map(_.eventName).sorted, Seq("City Marathon", "Field Day 2026"))

      val retrieved1 = store.getEvent("Field Day")
      assert(retrieved1.isDefined)
      assertEquals(retrieved1.get.ics205.incidentName, "Field Day 2026")

      val retrieved2 = store.findByName("city marathon")
      assert(retrieved2.isDefined)
      assertEquals(retrieved2.get.ics205.incidentName, "City Marathon")

      // Persistence across reloads
      val reloaded = new Ics205Store(helper(directory))
      assertEquals(reloaded.events().size, 2)

      // Delete event removes its file
      assert(store.deleteEvent("Field Day"))
      assert(!os.exists(directory / "events" / event1.fileName))
      assert(os.exists(directory / "events" / event2.fileName))
      assertEquals(store.events().size, 1)
      assertEquals(store.getEvent("Field Day"), None)
      assertEquals(store.getEvent("Marathon").isDefined, true)
    }

  test("each Ics205Event has its own file and event renaming cleans up old file"):
    withDirectory { directory =>
      val store = new Ics205Store(helper(directory))
      val event = Ics205Event("Alpha Event", plan.copy(incidentName = "Alpha Incident"))
      store.save(event)
      assert(os.exists(directory / "events" / event.fileName))

      // Rename event: update event id, delete old event, and save new event
      val renamed = event.copy(id = "Beta Event")
      store.deleteEvent("Alpha Event")
      store.save(renamed)
      assert(!os.exists(directory / "events" / event.fileName))
      assert(os.exists(directory / "events" / renamed.fileName))

      val reloaded = new Ics205Store(helper(directory))
      assertEquals(reloaded.events().size, 1)
      assertEquals(reloaded.events().head.id, "Beta Event")
    }

  test("insertTimestamp formats UTC timestamp before trailing .json extension"):
    val fixedInstant = Instant.parse("2026-10-02T18:01:05Z")
    assertEquals(Ics205Store.insertTimestamp("xyxxy.json", fixedInstant), "xyxxy.20261002T180105Z.json")
    assertEquals(Ics205Store.insertTimestamp("Field Day.json", fixedInstant), "Field Day.20261002T180105Z.json")
    assertEquals(Ics205Store.insertTimestamp("complex.name.with.dots.json", fixedInstant), "complex.name.with.dots.20261002T180105Z.json")
    assertEquals(Ics205Store.insertTimestamp("no_extension", fixedInstant), "no_extension.20261002T180105Z.json")

  test("saving an existing event copies previous version to backup directory with timestamp"):
    withDirectory { directory =>
      val store = new Ics205Store(helper(directory))
      val user = AuthenticatedUser("user-1", Role.Admin)
      val initialPlan = plan.copy(incidentName = "Field Day 2026", specialInstructions = "Version 1")
      val eventV1 = Ics205Event("Field Day", initialPlan)

      // First save: no backup should be created since file didn't exist
      store.save(eventV1, user)
      val eventFile = directory / "events" / eventV1.fileName
      val bakDir = directory / "events" / eventV1.id / "bak"
      assert(os.exists(eventFile))
      assert(!os.exists(bakDir))

      val v1Content = os.read(eventFile)

      // Second save: previous file should be copied to backup directory with timestamp
      val updatedPlan = plan.copy(incidentName = "Field Day 2026", specialInstructions = "Version 2")
      val eventV2 = Ics205Event("Field Day", updatedPlan)
      store.save(eventV2, user)

      assert(os.exists(bakDir) && os.isDir(bakDir))
      val backupFiles = os.list(bakDir).filter(p => os.isFile(p) && p.last.endsWith(Ics205Event.extension))
      assertEquals(backupFiles.size, 1)
      val backupFile = backupFiles.head
      assert(backupFile.last.startsWith("Field Day-"))
      assert(backupFile.last.endsWith(s".${Ics205Event.extension}"))

      // The backup file must have the content of Version 1
      assertEquals(os.read(backupFile), v1Content)

      // The active file must have the content of Version 2
      val v2Content = os.read(eventFile)
      assertNotEquals(v2Content, v1Content)

      // Ensure store listEvents ignores the bak directory
      val reloaded = new Ics205Store(helper(directory))
      assertEquals(reloaded.events().size, 1)
      assertEquals(reloaded.events().head.id, "Field Day")
      assertEquals(reloaded.events().head.ics205.specialInstructions, "Version 2")
    }

  test("deleting an event cleans up its backup directory"):
    withDirectory { directory =>
      val store = new Ics205Store(helper(directory))
      val user = AuthenticatedUser("user-1", Role.Admin)
      val eventV1 = Ics205Event("Campout", plan.copy(incidentName = "Campout"))
      store.save(eventV1, user)
      val eventV2 = Ics205Event("Campout", plan.copy(incidentName = "Campout", specialInstructions = "V2"))
      store.save(eventV2, user)

      val eventFile = directory / "events" / eventV1.fileName
      val bakDir = directory / "events" / eventV1.id
      assert(os.exists(eventFile))
      assert(os.exists(bakDir))

      assert(store.deleteEvent("Campout"))
      assert(!os.exists(eventFile))
      assert(!os.exists(bakDir))
    }

  test("uniqueEventName differentiates names by adding suffix"):
    withDirectory { directory =>
      val store = new Ics205Store(helper(directory))
      store.save(Ics205Event("Field Day", plan.copy(incidentName = "Field Day")))
      assertEquals(store.uniqueEventName("Field Day"), "Field Day (1)")

      store.save(Ics205Event("Field Day (1)", plan.copy(incidentName = "Field Day (1)")))
      assertEquals(store.uniqueEventName("Field Day"), "Field Day (2)")
      assertEquals(store.uniqueEventName("Field Day (1)"), "Field Day (2)")

      store.save(Ics205Event("Field Day (2)", plan.copy(incidentName = "Field Day (2)")))
      assertEquals(store.uniqueEventName("Field Day"), "Field Day (3)")

      // Non-existing name remains unchanged
      assertEquals(store.uniqueEventName("Marathon"), "Marathon")
    }

  test("importEvent saves new event or adds suffix if duplicate"):
    withDirectory { directory =>
      val store = new Ics205Store(helper(directory))
      val event1 = Ics205Event("Skywarn", plan.copy(incidentName = "Skywarn"))
      val imported1 = store.importEvent(event1, userId = Some("admin-1"))
      assertEquals(imported1.id, "Skywarn")
      assertEquals(store.getEvent("Skywarn").isDefined, true)

      // Import same event again -> should be suffixed
      val imported2 = store.importEvent(event1, userId = Some("admin-1"))
      assertEquals(imported2.id, "Skywarn (1)")
      assertEquals(store.getEvent("Skywarn (1)").isDefined, true)
      assertEquals(store.events().size, 2)

      // Import with empty eventName derives from incidentName
      val unnamedEvent = Ics205Event("", plan.copy(incidentName = "Skywarn Drill"))
      val imported3 = store.importEvent(unnamedEvent, userId = Some("admin-1"))
      assertEquals(imported3.eventName, "Skywarn Drill")
      assertEquals(store.getEvent("Skywarn Drill").isDefined, true)
    }
