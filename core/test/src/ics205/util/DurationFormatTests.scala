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

import java.time.{Duration, Instant}

class DurationFormatTests extends munit.FunSuite:

  test("formats milliseconds"):
    assertEquals(DurationFormat(Duration.ofMillis(500)), "500 ms")
    assertEquals(DurationFormat(Duration.ofMillis(0)), "0 ms")

  test("formats 1 second"):
    assertEquals(DurationFormat(Duration.ofMillis(1000)), "1 sec")

  test("formats seconds and milliseconds"):
    assertEquals(DurationFormat(Duration.ofMillis(1500)), "1 sec 500 ms")
    assertEquals(DurationFormat(Duration.ofMillis(59999)), "59 sec 999 ms")

  test("formats 1 minute"):
    assertEquals(DurationFormat(Duration.ofMinutes(1)), "1 min")

  test("formats minutes and seconds"):
    assertEquals(DurationFormat(Duration.ofSeconds(125)), "2 min 5 sec")
    assertEquals(DurationFormat(Duration.ofMinutes(59).plusSeconds(59)), "59 min 59 sec")

  test("formats hours and minutes"):
    assertEquals(DurationFormat(Duration.ofHours(2).plusMinutes(15)), "2 hours 15 min")
    assertEquals(DurationFormat(Duration.ofHours(23).plusMinutes(59)), "23 hours 59 min")

  test("formats days, hours, and minutes"):
    assertEquals(DurationFormat(Duration.ofDays(2).plusHours(3).plusMinutes(15)), "2 day 3 hour 15 min")
    assertEquals(DurationFormat(Duration.ofDays(2).plusHours(3)), "2 day 3 hour")

  test("formats from Instant"):
    val past = Instant.now().minus(Duration.ofMinutes(2).plusSeconds(5))
    val formatted = DurationFormat(past)
    assert(formatted.contains("min") || formatted.contains("sec"))
