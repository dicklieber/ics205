package ics205.model

import io.circe.parser.decode
import io.circe.syntax.*

class CtcssFrequencyTests extends munit.FunSuite:
  test("standard tones are unique, sorted, and round trip through numeric JSON"):
    val tones = CtcssFrequency.values.toSeq
    assertEquals(tones.size, 50)
    assertEquals(tones.map(_.hz).distinct.size, 50)
    assertEquals(tones.map(_.hz), tones.map(_.hz).sorted)
    assertEquals(tones.head.hz, BigDecimal("67.0"))
    assertEquals(tones.last.hz, BigDecimal("254.1"))
    tones.foreach { tone =>
      assertEquals(decode[CtcssFrequency](tone.asJson.noSpaces), Right(tone))
      assertEquals(tone.asJson.asNumber.flatMap(_.toBigDecimal), Some(tone.hz))
    }

  test("existing numeric tones decode regardless of decimal scale"):
    assertEquals(decode[CtcssFrequency]("100"), Right(CtcssFrequency.Hz100_0))
    assertEquals(decode[CtcssFrequency]("100.00"), Right(CtcssFrequency.Hz100_0))
    assertEquals(decode[Ctcss]("""{"mode":{"None":{}}}"""), Right(Ctcss()))

  test("unsupported frequencies are rejected"):
    Seq("0", "-1", "100.1", "300").foreach(value =>
      assert(decode[CtcssFrequency](value).isLeft)
    )
