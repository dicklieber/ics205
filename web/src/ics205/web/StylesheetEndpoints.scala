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

package ics205.web

import cats.effect.IO
import jakarta.inject.Singleton
import scala.io.Source
import scala.util.Using
import sttp.tapir.*
import sttp.tapir.server.ServerEndpoint

/** Serve only the public stylesheets from packaged classpath resources. */
@Singleton
class StylesheetEndpoints extends ApiEndpoints:
  override val endpoints: List[ServerEndpoint[Any, IO]] =
    List("ics205.css", "ics205-editor.css").map { fileName =>
      lazy val css = Using.resource(Source.fromResource(s"css/$fileName", getClass.getClassLoader))(_.mkString)
      endpoint.get
        .in("css" / fileName)
        .out(stringBody)
        .out(header("Content-Type", "text/css; charset=utf-8"))
        .serverLogicSuccess[IO](_ => IO.blocking(css))
    }
