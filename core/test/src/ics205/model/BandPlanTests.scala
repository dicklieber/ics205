package ics205.model

class BandPlanTests extends munit.FunSuite:
  test("plus offset"):
    val f = TxOffsetDir(Frequency(BigDecimal("442.725")), Frequency(BigDecimal("5.000")), Direction.Plus)
    assertEquals(f.rx, Frequency(BigDecimal("442.725")))
    assertEquals(f.tx, Frequency(BigDecimal("447.725")))

  test("minus offset"):
    val f = TxOffsetDir(Frequency(BigDecimal("147.750")), Frequency(BigDecimal("0.600")), Direction.Minus)
    assertEquals(f.tx, Frequency(BigDecimal("147.150")))

  test("simplex"):
    val f = TxOffsetDir(Frequency(BigDecimal("441.050")), Frequency(BigDecimal("5.000")), Direction.Simplex)
    assertEquals(f.rx, f.tx)
