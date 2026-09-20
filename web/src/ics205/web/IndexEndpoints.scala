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
import ics205.model.{Ics205, PreparedBy}
import ics205.store.Ics205Store
import jakarta.inject.{Inject, Singleton}
import scalatags.Text.all.*
import sttp.tapir.*
import sttp.tapir.server.ServerEndpoint

@Singleton
class IndexEndpoints @Inject() (val store: Ics205Store) extends ApiEndpoints:

  private val initial: Ics205 = store.ics205()
  val updated = initial.copy(preparedBy = Option(PreparedBy("Dick", Option("WA9NNN"))))
  store.save(updated)

  def index(): String =
    doctype("html")(
      html(
        head(
          meta(charset := "utf-8"),
          scalatags.Text.tags2.title("ICS-205")
        ),
        body(
          h1("ICS-205"),
          p("Incident Radio Communications Plan")
        )
      )
    ).render

  private val indexEndpoint = endpoint.get
    .in("")
    .out(htmlBodyUtf8)
    .serverLogicSuccess[IO](_ => IO(index()))

  override val endpoints: List[ServerEndpoint[Any, IO]] = List(indexEndpoint)
