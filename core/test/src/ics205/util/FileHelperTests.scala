package ics205.util

import ics205.BuildInfo
import io.circe.generic.auto.*

case class TestPayload(message: String, count: Int)

class FileHelperTests extends munit.FunSuite:

  test("isTestExecution detects test runner context"):
    assert(FileHelper.isTestExecution)

  test("new FileHelper() in unit tests never uses the production app directory"):
    val helper1 = new FileHelper()
    val helper2 = new FileHelper()

    val prodHome = FileHelper.appHome(BuildInfo.appName, BuildInfo.productName)

    assertNotEquals(helper1.directory, prodHome)
    assertNotEquals(helper2.directory, prodHome)
    assertNotEquals(helper1.directory, helper2.directory)
    assert(helper1.directory.toString.contains("ics205-test-"))
    assert(helper2.directory.toString.contains("ics205-test-"))

  test("new FileHelper(customPath) uses the specified directory"):
    val tempDir = os.temp.dir()
    try
      val helper = new FileHelper(tempDir)
      assertEquals(helper.directory, tempDir)
    finally
      os.remove.all(tempDir)

  test("FileHelper save, loadOrDefault, and remove work in isolated directory"):
    val helper = new FileHelper()
    try
      val payload = TestPayload("hello", 42)
      assertEquals(helper.loadOrDefault[TestPayload](Locus.admin, "payload.json")(TestPayload("default", 0)), TestPayload("default", 0))

      helper.save(Locus.admin, "payload.json", payload)
      assertEquals(helper.loadOrDefault[TestPayload](Locus.admin, "payload.json")(TestPayload("default", 0)), payload)
      assert(os.exists(helper.directory / "admin" / "payload.json"))

      helper.remove(Locus.admin, "payload.json")
      assert(!os.exists(helper.directory / "admin" / "payload.json"))
      assertEquals(helper.loadOrDefault[TestPayload](Locus.admin, "payload.json")(TestPayload("default", 0)), TestPayload("default", 0))
    finally
      if os.exists(helper.directory) then os.remove.all(helper.directory)

  test("appHome and configHome always use .ics205 in user home directory regardless of OS"):
    val dataHome = FileHelper.appHome("ICS-205", "ics205")
    val cfgHome = FileHelper.configHome("ICS-205", "ics205")
    assertEquals(dataHome, os.home / ".ics205")
    assertEquals(cfgHome, os.home / ".ics205" / "config")

  test("logDirectory is within FileHelper directory and creates directory"):
    val tempDir = os.temp.dir()
    try
      val helper = new FileHelper(tempDir)
      assertEquals(helper.logDirectory, tempDir / "log")
      assert(os.exists(tempDir / "log"))
    finally
      os.remove.all(tempDir)

  test("FileHelperLookup resolves directory, config, and log directory"):
    val tempDir = os.temp.dir()
    val tempConfigDir = os.temp.dir()
    try
      val helper = new FileHelper(tempDir, tempConfigDir)
      val lookup = new FileHelperLookup()
      assertEquals(lookup.lookup("directory"), tempDir.toString)
      assertEquals(lookup.lookup("dir"), tempDir.toString)
      assertEquals(lookup.lookup("datadir"), tempDir.toString)
      assertEquals(lookup.lookup("config"), tempConfigDir.toString)
      assertEquals(lookup.lookup("configDir"), tempConfigDir.toString)
      assertEquals(lookup.lookup("log"), (tempDir / "log").toString)
      assertEquals(lookup.lookup("logDir"), (tempDir / "log").toString)
      assertEquals(lookup.lookup("logDirectory"), (tempDir / "log").toString)
    finally
      os.remove.all(tempDir)
      os.remove.all(tempConfigDir)

  test("zipDirectory archives all files and subdirectories correctly"):
    val tempDir = os.temp.dir(prefix = "zip-test-")
    try
      val helper = new FileHelper(tempDir)
      os.write(tempDir / "root.txt", "root content")
      os.makeDir.all(tempDir / "events")
      os.write(tempDir / "events" / "event1.json", "{\"name\": \"event1\"}")
      os.makeDir.all(tempDir / "emptyFolder")

      val zipBytes = helper.zipDirectory()
      assert(zipBytes.nonEmpty)

      val entries = collection.mutable.Map[String, String]()
      val bais = new java.io.ByteArrayInputStream(zipBytes)
      val zis = new java.util.zip.ZipInputStream(bais)
      var entry = zis.getNextEntry
      while entry != null do
        val name = entry.getName
        val content = if entry.isDirectory then "" else new String(zis.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
        entries(name) = content
        zis.closeEntry()
        entry = zis.getNextEntry
      zis.close()

      assertEquals(entries.get("root.txt"), Some("root content"))
      assertEquals(entries.get("events/event1.json"), Some("{\"name\": \"event1\"}"))
      assert(entries.contains("emptyFolder/"))
    finally
      os.remove.all(tempDir)
