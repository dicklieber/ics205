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

/**``
 * Represents a radio frequency and an optional frequency offset for transmission.
 *
 * @param rxFrequency The receive frequency (Rx), defining the base operating frequency in MHz.
 * @param offset      The frequency offset in MHz applied to calculate the transmission frequency (Tx). Defaults to zero.
 *
 *                    The `RxWithOffset` class provides utility for managing communication channels with a receive frequency (Rx) 
 *                    and a derived transmit frequency (Tx). The transmit frequency is computed by adding the offset to the receive frequency.
 *                    It also supports simplex communication by identifying whether the offset is zero.
 * @constructor Creates an instance of `RxWithOffset` with the specified receive frequency and offset.
 *
 *              Lazy values include:
 *
 *              - `rx`: The receive frequency, directly corresponding to the `rxFrequency` parameter.
 *              - `tx`: The transmit frequency, computed as the sum of the `rxFrequency` and the `offset`.
 * @note The `isSimplex` method returns `true` if the offset is zero, indicating simplex communication. */
case class RxWithOffset(rxFrequency: Frequency, offset: Frequency = Frequency(0)) derives Codec.AsObject:
  lazy val rx: Frequency = rxFrequency
  lazy val tx: Frequency = rxFrequency + offset
  val isSimplex: Boolean = offset.mhz == 0
