/*
 * Copyright (c) 2026. Dick Lieber, WA9NNN
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package ics205.model

import java.time.LocalDateTime
import io.circe.Codec
import io.circe.derivation.{Configuration, ConfiguredCodec}

case class Ics205(formatVersion: String = "1.0",
                  incidentName: String,
                  operationalPeriod: OperationalPeriod,
                  channels: Seq[Ics205Channel],
                  specialInstructions:String  = "",
                  preparedBy: Option[PreparedBy] = None,
                  prepared: LocalDateTime = LocalDateTime.now()) derives Codec.AsObject

case class OperationalPeriod(from: Option[LocalDateTime] = None, to: Option[LocalDateTime] = None) derives Codec.AsObject
case class PreparedBy(name: String, callsign: Option[String] = None) derives Codec.AsObject

case class Ics205Channel(id: String,
                         zoneGroup: Option[String] = None,
                         channelNumber: Option[String] = None,
                         function: String,
                         name: String,
                         assignment: String,
                         frequency: RxWithOffset,
                         mode: RadioMode = RadioMode.Fm,
                         bandwidth: Option[Bandwidth] = None,
                         transmitSignaling: Option[Signaling] = None,
                         receiveSignaling: Option[Signaling] = None,
                         remarks: String = "")

object Ics205Channel:
  // Older saved channels may omit remarks; use the model default when decoding.
  private given Configuration = Configuration.default.withDefaults
  given Codec.AsObject[Ics205Channel] = ConfiguredCodec.derived[Ics205Channel]
