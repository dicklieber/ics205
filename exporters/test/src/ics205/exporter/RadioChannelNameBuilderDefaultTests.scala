package ics205.exporter

import ics205.model.*

class RadioChannelNameBuilderDefaultTests extends munit.FunSuite:
  private val builder = new RadioChannelNameBuilderDefault()
  private def label(name: String, assignment: String): String =
    builder(Ics205Channel(function = "", name = name, assignment = assignment,
      frequency = RxWithOffset(Frequency(BigDecimal("146.52"))), id = "1"))

  test("keeps labels that fit, including the exact limit"):
    assertEquals(label("TAC1", "Operations"), "TAC1 Operations")
    assertEquals(label("TAC123", "Operations"), "TAC123 Ops")
    assertEquals(label("TAC12", "Operations"), "TAC12 Operations")

  test("abbreviates common words without changing the channel name"):
    assertEquals(label("1R20", "Medical Operations"), "1R20 Med Ops")
    assertEquals(label("4R21", "Medical LOG 16-20"), "4R21 Md LG 16-20")
    assertEquals(label("TAC1", "North Command"), "TAC1 N Cmd")

  test("compacts unfamiliar words and preserves numeric ranges"):
    assertEquals(label("TAC1", "Waterfront Coordination 16-20"), "TAC1 W C 16-20")
    assertEquals(label("LONGCHANNEL", "Coordination 16-20"), "LONGCHANNEL C16-")

  test("normalizes whitespace and handles absent or oversized components"):
    assertEquals(label(" TAC1  ", "  Medical\nOperations "), "TAC1 Med Ops")
    assertEquals(label("TAC1", "  "), "TAC1")
    assertEquals(label("", "Operations"), "Operations")
    assertEquals(label("", ""), "")
    assertEquals(label("1234567890123456", "Operations"), "1234567890123456")
    assertEquals(label("12345678901234567890", "Operations"), "1234567890123456")

  test("always respects the 16-character limit"):
    for
      nameLength <- 0 to 25
      assignment <- Seq("", "Medical Operations", "Waterfront Coordination 16-20", "12345678901234567890", "🚒" * 20)
    do
      val result = label("N" * nameLength, assignment)
      assert(result.length <= 16, result)
      assert(!result.endsWith(" "))
      assert(result.isEmpty || !Character.isHighSurrogate(result.last))
