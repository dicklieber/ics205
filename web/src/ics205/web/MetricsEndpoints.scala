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
import ics205.metrics.ApplicationMetrics
import jakarta.inject.Singleton
import sttp.tapir.*
import sttp.tapir.server.ServerEndpoint

/** Tapir endpoints for Prometheus scraping. */
@Singleton
final class MetricsEndpoints extends ApiEndpoints:

  private val applicationMetrics = ApplicationMetrics.default

  private val metrics: ServerEndpoint[Any, IO] =
    MetricsEndpoints.metricsDef
      .serverLogicSuccess[IO](_ =>
        IO.delay(
          MetricsEndpoints.contentType -> applicationMetrics.renderPrometheus
        )
      )

  override def endpoints: List[ServerEndpoint[Any, IO]] = List(
    metrics
  )

private object MetricsEndpoints:
  private val metricsBody =
    header[String]("Content-Type")
      .and(stringBody)

  private val metricsDef: PublicEndpoint[Unit, Unit, (String, String), Any] =
    endpoint
      .get
      .in("metrics")
      .out(metricsBody)
      .description("Prometheus metrics for the local node")

  private val contentType =
    "text/plain; version=0.0.4; charset=utf-8"
