package ics205.model

enum RadioMode:
  case Fm
  case Am
  case Digital

enum Bandwidth:
  case Narrow
  case Wide

enum Signaling:
  case Ctcss(hz: BigDecimal)
  case Dcs(code: Int)
  case Nac(code: String)

enum DigitalParameters:
  case Dmr(colorCode: Int, timeSlot: Int, talkGroup: Int)
  case DStar(
      urCall: Option[String] = None,
      rpt1: Option[String] = None,
      rpt2: Option[String] = None
  )
  case P25(nac: Option[String] = None, talkGroup: Option[Int] = None)

enum Power:
  case Low
  case Medium
  case High

enum Scan:
  case Include
  case Skip

case class RadioPlan(name: String, memories: Seq[RadioMemory])

case class RadioMemory(
    sourceChannelId: String,
    channelName: String,
    assignment: String,
    preferredName: Option[String] = None,
    frequency: TxOffsetDir,
    mode: RadioMode = RadioMode.Fm,
    bandwidth: Option[Bandwidth] = None,
    transmitSignaling: Option[Signaling] = None,
    receiveSignaling: Option[Signaling] = None,
    digital: Option[DigitalParameters] = None,
    power: Option[Power] = None,
    scan: Scan = Scan.Include,
    comment: Option[String] = None
)
