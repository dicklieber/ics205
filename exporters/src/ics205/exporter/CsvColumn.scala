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

package ics205.exporter

/**
 * Standard CSV Column enumeration derived from radio programming software exports.
 * The order of enum cases defines the default natural column ordering.
 *
 * @param header The string emitted in the CSV header row.
 */
enum CsvColumn(val header: String):
  case ChannelNumber extends CsvColumn("Channel Number")
  case Bank extends CsvColumn("Bank")
  case ReceiveFrequency extends CsvColumn("Receive Frequency")
  case TransmitFrequency extends CsvColumn("Transmit Frequency")
  case OffsetFrequency extends CsvColumn("Offset Frequency")
  case OffsetDirection extends CsvColumn("Offset Direction")
  case OperatingMode extends CsvColumn("Operating Mode")
  case Ams extends CsvColumn("AMS")
  case Name extends CsvColumn("Name")
  case ToneMode extends CsvColumn("Tone Mode")
  case Ctcss extends CsvColumn("CTCSS")
  case RxCtcss extends CsvColumn("Rx CTCSS")
  case Dcs extends CsvColumn("DCS")
  case RxDcs extends CsvColumn("Rx DCS")
  case DcsPolarity extends CsvColumn("DCS Polarity")
  case Lockout extends CsvColumn("Lockout")
  case Skip extends CsvColumn("Skip")
  case ScanAdd extends CsvColumn("Scan Add")
  case Step extends CsvColumn("Step")
  case FineStepEnable extends CsvColumn("Fine Step Enable")
  case FineStep extends CsvColumn("Fine Step")
  case DigitalSquelch extends CsvColumn("Digital Squelch")
  case DigitalCode extends CsvColumn("Digital Code")
  case RxDgid extends CsvColumn("RX DGID")
  case TxDgid extends CsvColumn("TX DGID")
  case UserCtcss extends CsvColumn("User CTCSS")
  case TxPower extends CsvColumn("Tx Power")
  case ClockShift extends CsvColumn("Clock Shift")
  case MemoryGroup extends CsvColumn("Memory Group")
  case BusyLock extends CsvColumn("Busy Lock")
  case PttId extends CsvColumn("PTT ID")
  case SignalCode extends CsvColumn("Signal Code")
  case YourCallsign extends CsvColumn("Your Callsign")
  case Rpt1Callsign extends CsvColumn("Rpt-1 Callsign")
  case Rpt2Callsign extends CsvColumn("Rpt-2 Callsign")
  case Group extends CsvColumn("Group")
  case Comment extends CsvColumn("Comment")

object CsvColumn:
  private lazy val byHeaderMap: Map[String, CsvColumn] =
    CsvColumn.values.map(c => c.header -> c).toMap

  def fromHeader(header: String): Option[CsvColumn] =
    byHeaderMap.get(header)
