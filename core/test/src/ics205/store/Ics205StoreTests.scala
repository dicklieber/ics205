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

import java.time.LocalDateTime

class Ics205StoreTests extends munit.FunSuite:
  private def withDirectory(test: os.Path => Unit): Unit =
    val directory = os.temp.dir()
    try test(directory)
    finally os.remove.all(directory)

  private def helper(path: os.Path): FileHelper = new FileHelper:
    override val directory: os.Path = path

  private val now = LocalDateTime.of(2026, 9, 20, 12, 30)
  private val plan = Ics205(incidentName = "Test incident",
    operationalPeriod = OperationalPeriod(Some(now), Some(now.plusHours(12))),
    channels = Seq(Ics205Channel(id = "1",
      zoneGroup = Some("Command"),
      channelNumber = Some("1"),
      function = "Dispatch",
      name = "Repeater",
      assignment = "All teams",
      frequency = Frequency(BigDecimal("146.520")),
      mode = RadioMode.Digital,
      bandwidth = Some(Bandwidth.Narrow),
      transmitSignaling = Some(Signaling.Ctcss(BigDecimal("100.0"))),
      receiveSignaling = Some(Signaling.Dcs(23)),
      digital = Some(DigitalParameters.Dmr(1, 2, 123)),
      remarks = Some("Test channel"))),
    specialInstructions = "Monitor dispatch",
    preparedBy = Some(PreparedBy("Operator", Some("WA9NNN"))),
    prepared = now)

  test("missing file loads a blank plan without writing it"):
    withDirectory { directory =>
      val store = new Ics205Store(helper(directory))
      assertEquals(store.ics205().incidentName, "")
      assertEquals(store.ics205().channels, Seq.empty)
      assertEquals(store.ics205().operationalPeriod.from, None)
      assertEquals(store.ics205().operationalPeriod.to, None)
      assert(!os.exists(directory / "ics205.json"))
    }

  test("save updates memory and a new store loads all model fields"):
    withDirectory { directory =>
      val store = new Ics205Store(helper(directory))
      store.save(plan)
      val saved = store.ics205()
      assertEquals(saved.copy(prepared = plan.prepared), plan)
      assertEquals(new Ics205Store(helper(directory)).ics205(), saved)
      os.write.over(directory / "ics205.json", "invalid json")
      assertEquals(store.ics205(), saved)
      assertEquals(new Ics205Store(helper(directory)).ics205().incidentName, "")
    }

  test("save omits absent optional fields and reloads them as None"):
    withDirectory { directory =>
      val store = new Ics205Store(helper(directory))
      val sparse = plan.copy(
        operationalPeriod = OperationalPeriod(),
        preparedBy = None,
        channels = Seq(plan.channels.head.copy(
          remarks = None,
          digital = Some(DigitalParameters.P25())
        ))
      )
      store.save(sparse)
      val json = io.circe.parser.parse(os.read(directory / "ics205.json")).toOption.get
      assert(!json.hcursor.keys.get.toSet.contains("preparedBy"))
      assertEquals(json.hcursor.downField("operationalPeriod").focus, Some(io.circe.Json.obj()))
      val channel = json.hcursor.downField("channels").downArray
      assert(!channel.keys.get.toSet.contains("remarks"))
      assertEquals(channel.downField("digital").downField("P25").focus, Some(io.circe.Json.obj()))
      assertEquals(new Ics205Store(helper(directory)).ics205(), store.ics205())
    }

  test("undecodable JSON loads the default"):
    withDirectory { directory =>
      os.write(directory / "ics205.json", "{}")
      assertEquals(new Ics205Store(helper(directory)).ics205().channels, Seq.empty)
    }

  test("failed save preserves the previous in-memory plan"):
    withDirectory { directory =>
      val store = new Ics205Store(helper(directory))
      val original = store.ics205()
      os.makeDir(directory / "ics205.json")
      intercept[java.io.IOException] { store.save(plan) }
      assertEquals(store.ics205(), original)
    }
