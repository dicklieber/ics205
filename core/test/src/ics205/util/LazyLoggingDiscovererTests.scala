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

package ics205.util

class LazyLoggingDiscovererTests extends munit.FunSuite:

  test("discovers classes and objects implementing LazyLogging using ClassGraph"):
    val discovered = LazyLoggingDiscoverer.discoverLazyLoggers(Seq("ics205"))
    assert(discovered.nonEmpty, "Discovered loggers should not be empty")

    // FileHelper extends LazyLogging
    assert(
      discovered.contains("ics205.util.FileHelper"),
      s"Expected 'ics205.util.FileHelper' in discovered: $discovered"
    )

    // LoggingConfig$ or LoggingConfig implements LazyLogging
    assert(
      discovered.exists(_.contains("LoggingConfig")),
      s"Expected LoggingConfig in discovered: $discovered"
    )

  test("discovered loggers are sorted and distinct"):
    val discovered = LazyLoggingDiscoverer.discoverLazyLoggers(Seq("ics205"))
    assertEquals(discovered, discovered.distinct)
    assertEquals(discovered, discovered.sorted)
