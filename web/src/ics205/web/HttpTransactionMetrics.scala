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

import cats.data.Kleisli
import cats.effect.{IO, Outcome}
import ics205.metrics.ApplicationMetrics
import org.http4s.HttpApp

private[web] object HttpTransactionMetrics:
  def apply(app: HttpApp[IO], metrics: ApplicationMetrics): HttpApp[IO] =
    Kleisli { request =>
      IO(metrics.startHttpTransaction()).bracketCase { stopTimer =>
        app(request).map(response =>
          response.withBodyStream(response.body.onFinalize(IO(stopTimer())))
        )
      } { (stopTimer, outcome) =>
        outcome match
          case Outcome.Succeeded(_) => IO.unit // The response body owns completion.
          case _ => IO(stopTimer())
      }
    }
