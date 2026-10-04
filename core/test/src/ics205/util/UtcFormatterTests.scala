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

import munit.FunSuite
import java.time.Instant

class UtcFormatterTests extends FunSuite:

  test("format formats Instant into UTC ISO-like compact string"):
    val instant = Instant.parse("2026-10-04T15:27:00Z")
    assertEquals(UtcFormatter.format(instant), "20261004T152700Z")
    assertEquals(UtcFormatter(instant), "20261004T152700Z")

  test("format formats default instant (now) matching expected pattern"):
    val formatted = UtcFormatter.format()
    assert(formatted.matches("""^\d{8}T\d{6}Z$"""))
    val applied = UtcFormatter()
    assert(applied.matches("""^\d{8}T\d{6}Z$"""))

  test("formatter exposes DateTimeFormatter"):
    assert(UtcFormatter.formatter != null)
