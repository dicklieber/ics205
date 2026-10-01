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

import ics205.model.Ics205Channel

trait RadioQuirksDetail(channel: Ics205Channel):
  def manufacturer: String
  def offset: String = channel.frequency.offsetAbs.toString
  def direction: String

class QuirksIcom(channel: Ics205Channel) extends RadioQuirksDetail(channel):
  def manufacturer: String = "Icom"
  def direction: String =
    if channel.frequency.isSimplex || channel.frequency.offset.mhz == BigDecimal(0) then "Simplex"
    else if channel.frequency.offset.mhz < BigDecimal(0) then "DUP-"
    else "DUP+"

class QuirksYaseu(channel: Ics205Channel) extends RadioQuirksDetail(channel):
  def manufacturer: String = "Yaesu"
  def direction: String =
    if channel.frequency.isSimplex || channel.frequency.offset.mhz == BigDecimal(0) then "Simplex"
    else if channel.frequency.offset.mhz < BigDecimal(0) then "Minus"
    else "Plus"

class QuirksBao(channel: Ics205Channel) extends QuirksYaseu(channel):
  override def manufacturer: String = "Baofeng"

class QuirksKenwood(channel: Ics205Channel) extends QuirksYaseu(channel):
  override def manufacturer: String = "Kenwood"
