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
