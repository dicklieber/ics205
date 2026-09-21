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
import ics205.store.Ics205Store
import jakarta.inject.{Inject, Singleton}
import sttp.tapir.*
import sttp.tapir.server.ServerEndpoint

@Singleton
class IndexEndpoints @Inject() (val store: Ics205Store) extends ApiEndpoints:

  def index(): String = Ics205Page.render(store.ics205())

  private val indexEndpoint = endpoint.get
    .in("")
    .out(htmlBodyUtf8)
    .serverLogicSuccess[IO](_ => IO(index()))

  override val endpoints: List[ServerEndpoint[Any, IO]] = List(indexEndpoint)
