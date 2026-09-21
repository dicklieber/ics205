package ics205.model

import io.circe.{Codec, Decoder, Encoder}

/** The standard 50 CTCSS tones, expressed in Hz in ascending order. */
enum CtcssFrequency(val hz: BigDecimal):
  case Hz67_0 extends CtcssFrequency(BigDecimal("67.0"))
  case Hz69_3 extends CtcssFrequency(BigDecimal("69.3"))
  case Hz71_9 extends CtcssFrequency(BigDecimal("71.9"))
  case Hz74_4 extends CtcssFrequency(BigDecimal("74.4"))
  case Hz77_0 extends CtcssFrequency(BigDecimal("77.0"))
  case Hz79_7 extends CtcssFrequency(BigDecimal("79.7"))
  case Hz82_5 extends CtcssFrequency(BigDecimal("82.5"))
  case Hz85_4 extends CtcssFrequency(BigDecimal("85.4"))
  case Hz88_5 extends CtcssFrequency(BigDecimal("88.5"))
  case Hz91_5 extends CtcssFrequency(BigDecimal("91.5"))
  case Hz94_8 extends CtcssFrequency(BigDecimal("94.8"))
  case Hz97_4 extends CtcssFrequency(BigDecimal("97.4"))
  case Hz100_0 extends CtcssFrequency(BigDecimal("100.0"))
  case Hz103_5 extends CtcssFrequency(BigDecimal("103.5"))
  case Hz107_2 extends CtcssFrequency(BigDecimal("107.2"))
  case Hz110_9 extends CtcssFrequency(BigDecimal("110.9"))
  case Hz114_8 extends CtcssFrequency(BigDecimal("114.8"))
  case Hz118_8 extends CtcssFrequency(BigDecimal("118.8"))
  case Hz123_0 extends CtcssFrequency(BigDecimal("123.0"))
  case Hz127_3 extends CtcssFrequency(BigDecimal("127.3"))
  case Hz131_8 extends CtcssFrequency(BigDecimal("131.8"))
  case Hz136_5 extends CtcssFrequency(BigDecimal("136.5"))
  case Hz141_3 extends CtcssFrequency(BigDecimal("141.3"))
  case Hz146_2 extends CtcssFrequency(BigDecimal("146.2"))
  case Hz151_4 extends CtcssFrequency(BigDecimal("151.4"))
  case Hz156_7 extends CtcssFrequency(BigDecimal("156.7"))
  case Hz159_8 extends CtcssFrequency(BigDecimal("159.8"))
  case Hz162_2 extends CtcssFrequency(BigDecimal("162.2"))
  case Hz165_5 extends CtcssFrequency(BigDecimal("165.5"))
  case Hz167_9 extends CtcssFrequency(BigDecimal("167.9"))
  case Hz171_3 extends CtcssFrequency(BigDecimal("171.3"))
  case Hz173_8 extends CtcssFrequency(BigDecimal("173.8"))
  case Hz177_3 extends CtcssFrequency(BigDecimal("177.3"))
  case Hz179_9 extends CtcssFrequency(BigDecimal("179.9"))
  case Hz183_5 extends CtcssFrequency(BigDecimal("183.5"))
  case Hz186_2 extends CtcssFrequency(BigDecimal("186.2"))
  case Hz189_9 extends CtcssFrequency(BigDecimal("189.9"))
  case Hz192_8 extends CtcssFrequency(BigDecimal("192.8"))
  case Hz196_6 extends CtcssFrequency(BigDecimal("196.6"))
  case Hz199_5 extends CtcssFrequency(BigDecimal("199.5"))
  case Hz203_5 extends CtcssFrequency(BigDecimal("203.5"))
  case Hz206_5 extends CtcssFrequency(BigDecimal("206.5"))
  case Hz210_7 extends CtcssFrequency(BigDecimal("210.7"))
  case Hz218_1 extends CtcssFrequency(BigDecimal("218.1"))
  case Hz225_7 extends CtcssFrequency(BigDecimal("225.7"))
  case Hz229_1 extends CtcssFrequency(BigDecimal("229.1"))
  case Hz233_6 extends CtcssFrequency(BigDecimal("233.6"))
  case Hz241_8 extends CtcssFrequency(BigDecimal("241.8"))
  case Hz250_3 extends CtcssFrequency(BigDecimal("250.3"))
  case Hz254_1 extends CtcssFrequency(BigDecimal("254.1"))

object CtcssFrequency:
  def fromHz(hz: BigDecimal): Option[CtcssFrequency] = values.find(_.hz == hz)

  // Keep numeric JSON compatible with previously saved standard frequencies.
  given Codec[CtcssFrequency] = Codec.from(
    Decoder.decodeBigDecimal.emap(hz =>
      fromHz(hz).toRight(s"Unsupported CTCSS frequency: $hz Hz")
    ),
    Encoder.encodeBigDecimal.contramap(_.hz)
  )
