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

enum RadioMode derives Codec.AsObject:
  case Fm
  case Am
  case Digital

enum Bandwidth derives Codec.AsObject:
  case Narrow
  case Wide

enum Signaling derives Codec.AsObject:
  case Ctcss(hz: BigDecimal)
  case Dcs(code: Int)
  case Nac(code: String)

enum Power:
  case Low
  case Medium
  case High

enum Scan:
  case Include
  case Skip

case class RadioPlan(name: String, memories: Seq[RadioMemory])

case class RadioMemory(sourceChannelId: String,
                       channelName: String,
                       assignment: String,
                       preferredName: Option[String] = None,
                       frequency: RxWithOffset,
                       mode: RadioMode = RadioMode.Fm,
                       bandwidth: Option[Bandwidth] = None,
                       transmitSignaling: Option[Signaling] = None,
                       receiveSignaling: Option[Signaling] = None,
                       remarks: String = "")
