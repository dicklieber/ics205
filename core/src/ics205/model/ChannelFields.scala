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

/** The simple fields of an [[Ics205Channel]], with frequency flattened into rx, tx and offset. */
enum ChannelField(extract: Ics205Channel => String):
  case Id extends ChannelField(_.id)
  case ZoneGroup extends ChannelField(_.zoneGroup.getOrElse(""))
  case ChannelNumber extends ChannelField(_.channelNumber.getOrElse(""))
  case Function extends ChannelField(_.function)
  case Name extends ChannelField(_.name)
  case Assignment extends ChannelField(_.assignment)
  case Rx extends ChannelField(_.frequency.rx.toString)
  case Tx extends ChannelField(_.frequency.tx.toString)
  case Offset extends ChannelField(_.frequency.offset.toString)
  case Mode extends ChannelField(_.mode.toString)
  case Bandwidth extends ChannelField(_.bandwidth.map(_.toString).getOrElse(""))
  case CtcssFrequency extends ChannelField(_.ctcss.frequency.map(_.hz.toString).getOrElse(""))
  case CtcssMode extends ChannelField(_.ctcss.mode.toString)
  case Remarks extends ChannelField(_.remarks)

  def value(channel: Ics205Channel): String = extract(channel)

class ChannelFields(ics205Channel: Ics205Channel):
  def apply(field: ChannelField): String = field.value(ics205Channel)

  /** Every field, in enum order. */
  def all: Seq[(ChannelField, String)] = ChannelField.values.toSeq.map(f => f -> apply(f))
