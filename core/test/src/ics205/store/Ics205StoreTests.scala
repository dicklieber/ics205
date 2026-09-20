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
  private val plan = Ics205(
    incidentName = "Test incident",
    operationalPeriod = OperationalPeriod(now, now.plusHours(12)),
    channels = Seq(
      Ics205Channel(
        id = "1",
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
        remarks = Some("Test channel")
      )
    ),
    specialInstructions = Some("Monitor dispatch"),
    preparedBy = Some(PreparedBy("Operator", Some("WA9NNN"))),
    prepared = Some(now)
  )

  test("missing file loads a blank plan without writing it"):
    withDirectory { directory =>
      val store = new Ics205Store(helper(directory))
      assertEquals(store.ics205().incidentName, "")
      assertEquals(store.ics205().channels, Seq.empty)
      assertEquals(store.ics205().operationalPeriod.from, store.ics205().operationalPeriod.to)
      assert(!os.exists(directory / "ics205.json"))
    }

  test("save updates memory and a new store loads all model fields"):
    withDirectory { directory =>
      val store = new Ics205Store(helper(directory))
      store.save(plan)
      assertEquals(store.ics205(), plan)
      assertEquals(new Ics205Store(helper(directory)).ics205(), plan)
      os.write.over(directory / "ics205.json", "invalid json")
      assertEquals(store.ics205(), plan)
      assertEquals(new Ics205Store(helper(directory)).ics205().incidentName, "")
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
