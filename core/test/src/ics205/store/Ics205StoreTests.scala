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

import ics205.util.FileHelper
import ics205.model.*
import io.circe.Json
import io.circe.syntax.*

import java.time.LocalDateTime

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

  test("save updates memory and a new store loads all model fields"):
    withDirectory { directory =>
      val store = new Ics205Store(helper(directory))
      store.save(plan)
      val saved = store.ics205()
      assertEquals(saved.copy(prepared = plan.prepared), plan)
      assertEquals(new Ics205Store(helper(directory)).ics205(), saved)
      val eventFile = directory / "events" / "Test incident.json"
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
      store.save(sparse)
      val json = io.circe.parser.parse(os.read(directory / "events" / "Test incident.json")).toOption.get
      val ics205Cursor = json.hcursor.downField("ics205")
      assert(!ics205Cursor.keys.get.toSet.contains("preparedBy"))
      assertEquals(ics205Cursor.downField("operationalPeriod").focus, Some(io.circe.Json.obj()))
      val channel = ics205Cursor.downField("channels").downArray
      assertEquals(channel.get[String]("remarks"), Right(""))
      assert(!channel.keys.get.toSet.contains("digital"))
      assertEquals(new Ics205Store(helper(directory)).ics205(), store.ics205())
    }

  test("save records lastEditedBy and savedAt in metadata"):
    withDirectory { directory =>
      val store = new Ics205Store(helper(directory))
      store.save(plan, userId = "user-123")
      assertEquals(store.metadata().lastEditedBy, Some("user-123"))
      assert(store.metadata().savedAt.toEpochMilli > 0)

      val reloaded = new Ics205Store(helper(directory))
      assertEquals(reloaded.metadata().lastEditedBy, Some("user-123"))
      assertEquals(reloaded.metadata().savedAt, store.metadata().savedAt)
    }

  test("setUserPermission and metadata updates persist correctly"):
    withDirectory { directory =>
      val store = new Ics205Store(helper(directory))
      store.setUserPermission("user-1", ics205.auth.Permission.EditPlans)
      store.setUserPermission("user-2", ics205.auth.Permission.ViewPlans)
      assertEquals(store.metadata().permissions.get("user-1"), Some(ics205.auth.Permission.EditPlans))
      assertEquals(store.metadata().permissions.get("user-2"), Some(ics205.auth.Permission.ViewPlans))

      val reloaded = new Ics205Store(helper(directory))
      assertEquals(reloaded.metadata().permissions.get("user-1"), Some(ics205.auth.Permission.EditPlans))
      assertEquals(reloaded.metadata().permissions.get("user-2"), Some(ics205.auth.Permission.ViewPlans))

      store.removeUserPermission("user-1")
      assertEquals(store.metadata().permissions.get("user-1"), None)
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
      assertEquals(store.getEvent("Drill").map(_.eventName), Some("Drill"))
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
      store.saveEvent(event)
      val original = store.ics205()
      os.remove.all(directory / "events")
      os.write(directory / "events", "not a directory")
      intercept[java.io.IOException] { store.save(plan) }
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

  test("event and ics205Event return full event and metadata"):
    withDirectory { directory =>
      val store = new Ics205Store(helper(directory))
      assertEquals(store.event(), None)
      assertEquals(store.ics205Event(), None)
      val event = Ics205Event("Test Event", plan)
      store.saveEvent(event)
      assertEquals(store.event().get.ics205.incidentName, "Test incident")
      assertEquals(store.ics205Event().get.ics205.incidentName, "Test incident")
    }

  test("save overloads correctly handle refreshPrepared and userId variations"):
    withDirectory { directory =>
      val store = new Ics205Store(helper(directory))
      val initialTime = LocalDateTime.of(2020, 1, 1, 0, 0)
      val fixedPlan = plan.copy(prepared = initialTime)

      store.save(fixedPlan, refreshPrepared = false)
      assertEquals(store.ics205().prepared, initialTime)

      store.save(fixedPlan, userId = "user-a", refreshPrepared = false)
      assertEquals(store.ics205().prepared, initialTime)
      assertEquals(store.metadata().lastEditedBy, Some("user-a"))

      store.save(fixedPlan, userId = Some("user-b"), refreshPrepared = false)
      assertEquals(store.ics205().prepared, initialTime)
      assertEquals(store.metadata().lastEditedBy, Some("user-b"))

      store.save(fixedPlan, userId = Some("user-c"))
      assertNotEquals(store.ics205().prepared, initialTime)
      assertEquals(store.metadata().lastEditedBy, Some("user-c"))
    }

  test("saveEvent persists event and handles refreshPrepared flag"):
    withDirectory { directory =>
      val store = new Ics205Store(helper(directory))
      val initialTime = LocalDateTime.of(2020, 1, 1, 0, 0)
      val event = Ics205Event(plan.copy(prepared = initialTime), Ics205Metadata())

      store.saveEvent(event, refreshPrepared = false)
      assertEquals(store.ics205().prepared, initialTime)

      store.saveEvent(event, refreshPrepared = true)
      assertNotEquals(store.ics205().prepared, initialTime)
    }

  test("setUserPermission with Option[Permission] sets and clears permissions"):
    withDirectory { directory =>
      val store = new Ics205Store(helper(directory))
      store.setUserPermission("user-opt", Some(ics205.auth.Permission.EditUsers))
      assertEquals(store.metadata().permissions.get("user-opt"), Some(ics205.auth.Permission.EditUsers))

      store.setUserPermission("user-opt", None)
      assertEquals(store.metadata().permissions.get("user-opt"), None)
    }

  test("Ics205Store supports multiple events, lookup by eventName, adding and deleting events"):
    withDirectory { directory =>
      val store = new Ics205Store(helper(directory))
      val event1 = Ics205Event("Field Day", plan.copy(incidentName = "Field Day 2026"))
      val event2 = Ics205Event("Marathon", plan.copy(incidentName = "City Marathon"))

      store.saveEvent(event1)
      store.saveEvent(event2)

      assert(os.exists(directory / "events" / "Field Day.json"))
      assert(os.exists(directory / "events" / "Marathon.json"))

      assertEquals(store.events().size, 2)
      assertEquals(store.listEvents().map(_.eventName), Seq("Field Day", "Marathon"))

      val retrieved1 = store.getEvent("Field Day")
      assert(retrieved1.isDefined)
      assertEquals(retrieved1.get.ics205.incidentName, "Field Day 2026")

      val retrieved2 = store.findByName("marathon")
      assert(retrieved2.isDefined)
      assertEquals(retrieved2.get.ics205.incidentName, "City Marathon")

      // Switching active event
      store.setCurrentEvent("Marathon")
      assertEquals(store.currentEventName, Some("Marathon"))
      assertEquals(store.ics205().incidentName, "City Marathon")

      // Setting per-event user permission
      store.setUserPermission("Field Day", "user-fd", ics205.auth.Permission.EditPlans)
      assertEquals(store.getEvent("Field Day").get.metadata.permissions.get("user-fd"), Some(ics205.auth.Permission.EditPlans))
      assertEquals(store.getEvent("Marathon").get.metadata.permissions.get("user-fd"), None)

      // Persistence across reloads
      val reloaded = new Ics205Store(helper(directory))
      assertEquals(reloaded.events().size, 2)
      assertEquals(reloaded.getEvent("Field Day").get.metadata.permissions.get("user-fd"), Some(ics205.auth.Permission.EditPlans))

      // Delete event removes its file
      assert(store.deleteEvent("Field Day"))
      assert(!os.exists(directory / "events" / "Field Day.json"))
      assert(os.exists(directory / "events" / "Marathon.json"))
      assertEquals(store.events().size, 1)
      assertEquals(store.getEvent("Field Day"), None)
      assertEquals(store.getEvent("Marathon").isDefined, true)
    }

  test("each Ics205Event has its own file and event renaming cleans up old file"):
    withDirectory { directory =>
      val store = new Ics205Store(helper(directory))
      val event = Ics205Event("Alpha Event", plan.copy(incidentName = "Alpha Incident"))
      store.saveEvent(event)
      assert(os.exists(directory / "events" / "Alpha Event.json"))

      // Rename event
      assert(store.renameEvent("Alpha Event", "Beta Event").isRight)
      assert(!os.exists(directory / "events" / "Alpha Event.json"))
      assert(os.exists(directory / "events" / "Beta Event.json"))

      val reloaded = new Ics205Store(helper(directory))
      assertEquals(reloaded.events().size, 1)
      assertEquals(reloaded.events().head.eventName, "Beta Event")
    }

  test("Ics205Store sanitizes special characters in event file names"):
    withDirectory { directory =>
      val store = new Ics205Store(helper(directory))
      val event = Ics205Event("Drill: North/South? <Special>", plan.copy(incidentName = "Special Drill"))
      store.saveEvent(event)
      assert(os.exists(directory / "events" / "Drill_ North_South_ _Special_.json"))

      val reloaded = new Ics205Store(helper(directory))
      assertEquals(reloaded.events().size, 1)
      assertEquals(reloaded.getEvent("Drill: North/South? <Special>").isDefined, true)
    }
