package ics205.model

import java.time.LocalDateTime

case class Ics205(
    formatVersion: String = "1.0",
    incidentName: String,
    operationalPeriod: OperationalPeriod,
    channels: Seq[Ics205Channel],
    specialInstructions: Option[String] = None,
    preparedBy: Option[PreparedBy] = None,
    prepared: Option[LocalDateTime] = None
)

case class OperationalPeriod(from: LocalDateTime, to: LocalDateTime)
case class PreparedBy(name: String, callsign: Option[String] = None)

case class Ics205Channel(
    id: String,
    zoneGroup: Option[String] = None,
    channelNumber: Option[String] = None,
    function: String,
    name: String,
    assignment: String,
    frequency: Frequency,
    mode: RadioMode = RadioMode.Fm,
    bandwidth: Option[Bandwidth] = None,
    transmitSignaling: Option[Signaling] = None,
    receiveSignaling: Option[Signaling] = None,
    digital: Option[DigitalParameters] = None,
    remarks: Option[String] = None
)
