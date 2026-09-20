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

package ics205.metrics

import io.dropwizard.metrics5.{MetricRegistry, SharedMetricRegistries}

final class ApplicationMetrics(val registry: MetricRegistry):
  private val httpTransactions = registry.timer("http.transactions")

  /** Starts a transaction timer and returns its completion callback. */
  def startHttpTransaction(): () => Unit =
    val context = httpTransactions.time()
    () => { context.stop(); () }

  def renderPrometheus: String = PrometheusMetrics.render(registry)

object ApplicationMetrics:
  lazy val default: ApplicationMetrics =
    new ApplicationMetrics(SharedMetricRegistries.getOrCreate("default"))
