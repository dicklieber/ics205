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
      assertEquals(helper.loadOrDefault[TestPayload]("payload.json")(TestPayload("default", 0)), TestPayload("default", 0))

      helper.save("payload.json", payload)
      assertEquals(helper.loadOrDefault[TestPayload]("payload.json")(TestPayload("default", 0)), payload)

      helper.remove("payload.json")
      assert(!os.exists(helper.directory / "payload.json"))
      assertEquals(helper.loadOrDefault[TestPayload]("payload.json")(TestPayload("default", 0)), TestPayload("default", 0))
    finally
      if os.exists(helper.directory) then os.remove.all(helper.directory)

  test("appHome provides OS-specific base directory for web and cli applications"):
    val home = FileHelper.appHome("ICS-205", "ics205")
    val osName = System.getProperty("os.name", "").toLowerCase
    if osName.contains("mac") then
      assertEquals(home, os.home / "Library" / "Application Support" / "ICS-205")
    else if osName.contains("win") then
      assertEquals(home, os.home / "AppData" / "Local" / "ICS-205")
    else
      assertEquals(home, os.Path("/var/lib/ics205"))
