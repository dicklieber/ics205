package ics205.model

enum Direction:
  case Simplex
  case Plus
  case Minus

case class AmateurBandPlan(
    name: String,
    band: FrequencyRange,
    plus: FrequencyRange,
    minus: FrequencyRange,
    offset: Frequency
):
  def isBand(frequency: Frequency): Boolean = band.contains(frequency)

  def txOffsetDir(frequency: Frequency): TxOffsetDir =
    val direction =
      if plus.contains(frequency) then Direction.Plus
      else if minus.contains(frequency) then Direction.Minus
      else Direction.Simplex
    TxOffsetDir(frequency, offset, direction)

case class TxOffsetDir(frequency: Frequency, offset: Frequency, dir: Direction):
  lazy val rx: Frequency = frequency
  lazy val tx: Frequency =
    dir match
      case Direction.Simplex => frequency
      case Direction.Plus    => frequency + offset
      case Direction.Minus   => frequency - offset
