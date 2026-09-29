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

package ics205

import io.dropwizard.metrics5.SharedMetricRegistries

class SampleStatsSource extends StatsSource:
  val sampleCounter = addCounter("counter")
  val sampleHistogram = addHistogram("histogram")
  val sampleMeter = addMeter("meter")
  val sampleTimer = addTimer("timer")
  val sampleGauge = addGauge("gauge")(42)

class StatsSourceTests extends munit.FunSuite:

  test("StatsSource registers metrics prefixed with the class name in default registry"):
    val source = new SampleStatsSource()
    val registry = SharedMetricRegistries.getOrCreate("default")

    val className = source.getClass.getName.stripSuffix("$")

    source.sampleCounter.inc(10)
    assertEquals(registry.counter(s"$className.counter").getCount, 10L)

    source.sampleHistogram.update(50)
    assertEquals(registry.histogram(s"$className.histogram").getCount, 1L)

    source.sampleMeter.mark(2)
    assertEquals(registry.meter(s"$className.meter").getCount, 2L)

    source.sampleTimer.time().stop()
    assertEquals(registry.timer(s"$className.timer").getCount, 1L)

    val gauge = registry.getGauges.get(io.dropwizard.metrics5.MetricName.build(s"$className.gauge"))
    assert(gauge != null)
    assertEquals(gauge.getValue.asInstanceOf[Any], 42)
