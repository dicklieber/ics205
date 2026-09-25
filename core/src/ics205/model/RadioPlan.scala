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

enum CtcssMode derives Codec.AsObject:
  case None
  case Tone
  case TSQL

/** CTCSS frequency is in Hz. None disables it, Tone encodes TX, TSQL also decodes RX. */
case class Ctcss(frequency: Option[CtcssFrequency] = None,
                 mode: CtcssMode = CtcssMode.None) derives Codec.AsObject

