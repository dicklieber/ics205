package ics205.model

import io.circe.Codec

case class Frequency(mhz: BigDecimal) derives Codec.AsObject:
  def +(other: Frequency): Frequency = Frequency(mhz + other.mhz)
  def -(other: Frequency): Frequency = Frequency(mhz - other.mhz)
  override def toString: String = mhz.toString

case class FrequencyRange(start: Frequency, end: Frequency):
  def contains(frequency: Frequency): Boolean =
    start.mhz <= frequency.mhz && frequency.mhz <= end.mhz
