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
import sttp.model.StatusCode
import sttp.tapir.server.ServerEndpoint

@Singleton
class IndexEndpoints @Inject() (val store: Ics205Store) extends ApiEndpoints:

  def index(): String = Ics205Page.render(store.ics205())

  private val indexEndpoint = endpoint.get
    .in("")
    .in(query[Option[String]]("saved"))
    .out(htmlBodyUtf8)
    .serverLogicSuccess[IO](saved => IO(Ics205Editor.render(store.ics205(), saved = saved.contains("1"))))

  private val saveEndpoint = endpoint.post
    .in("")
    .in(formBody[Map[String, String]])
    .errorOut(statusCode.and(htmlBodyUtf8))
    .out(statusCode.and(header[String]("Location")))
    .serverLogic[IO](data => IO.blocking {
      val current = store.ics205()
      Ics205Form.decode(data, current) match
        case Left(message) =>
          Left((StatusCode.UnprocessableEntity, Ics205Editor.render(current, Some(data), Some(message))))
        case Right(plan) =>
          try
            store.save(plan, refreshPrepared = false)
            Right((StatusCode.SeeOther, "/?saved=1"))
          catch
            case _: java.io.IOException =>
              Left((StatusCode.InternalServerError, Ics205Editor.render(current, Some(data),
                Some("The plan could not be saved. Check that the data directory is writable and try again."))))
    })

  private val previewEndpoint = endpoint.post
    .in("preview")
    .in(formBody[Map[String, String]])
    .out(statusCode.and(htmlBodyUtf8))
    .serverLogicSuccess[IO](data => IO {
      val current = store.ics205()
      Ics205Form.decode(data, current) match
        case Left(message) =>
          (StatusCode.UnprocessableEntity, Ics205Editor.render(current, Some(data), Some(message)))
        case Right(plan) => (StatusCode.Ok, Ics205Page.renderPrintable(plan))
    })

  override val endpoints: List[ServerEndpoint[Any, IO]] = List(indexEndpoint, saveEndpoint, previewEndpoint)
