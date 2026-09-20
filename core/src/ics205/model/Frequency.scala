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

import io.circe.Codec

/**
 * Allow val 
 * ```
 * f:Frequency = mhz"224.52"
 * ````
 */
extension (context: StringContext)
  /** Parses a frequency in MHz, preserving decimal precision. */
  def mhz(args: Any*): Frequency = Frequency(BigDecimal(context.s(args*)))

case class Frequency(mhz: BigDecimal) derives Codec.AsObject:
  def +(other: Frequency): Frequency = Frequency(mhz + other.mhz)
  def -(other: Frequency): Frequency = Frequency(mhz - other.mhz)
  override def toString: String = mhz.toString

case class FrequencyRange(start: Frequency, end: Frequency):
  def contains(frequency: Frequency): Boolean =
    start.mhz <= frequency.mhz && frequency.mhz <= end.mhz
