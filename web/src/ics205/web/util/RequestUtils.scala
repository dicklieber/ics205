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

package ics205.web.util

import sttp.tapir.model.ServerRequest

object RequestUtils:
  def clientIp(request: ServerRequest): String =
    request.header("X-Forwarded-For")
      .flatMap(_.split(',').headOption.map(_.trim))
      .filter(_.nonEmpty)
      .orElse(request.header("X-Real-IP"))
      .orElse(request.connectionInfo.remote.collect {
        case isa: java.net.InetSocketAddress =>
          Option(isa.getAddress).map(_.getHostAddress).getOrElse(isa.getHostString)
      })
      .getOrElse("unknown")
