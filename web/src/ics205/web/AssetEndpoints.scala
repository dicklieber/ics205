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

/** Serve explicitly listed public assets from packaged classpath resources. */
@Singleton
class AssetEndpoints extends ApiEndpoints:
  override val endpoints: List[ServerEndpoint[Any, IO]] =
    List(
      ("css", "ics205.css", "text/css; charset=utf-8"),
      ("css", "ics205-editor.css", "text/css; charset=utf-8"),
      ("css", "admin.css", "text/css; charset=utf-8"),
      ("css", "radio.css", "text/css; charset=utf-8"),
      ("css", "navbar.css", "text/css; charset=utf-8"),
      ("icons", "trash.svg", "image/svg+xml"),
      ("icons", "copy.svg", "image/svg+xml")
    ).map { (directory, fileName, contentType) =>
      lazy val asset = Using.resource(Source.fromResource(s"$directory/$fileName", getClass.getClassLoader))(_.mkString)
      endpoint.get
        .in(directory / fileName)
        .out(stringBody)
        .out(header("Content-Type", contentType))
        .serverLogicSuccess[IO](_ => IO.blocking(asset))
    }
