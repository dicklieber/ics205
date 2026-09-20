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

package ics205.model

class RxWithOffsetTests extends munit.FunSuite:
  test("RxWithOffset adds a positive offset to the receive frequency"):
    val f = RxWithOffset(mhz"442.725", mhz"5.000")
    assertEquals(f.rx, mhz"442.725")
    assertEquals(f.tx, mhz"447.725")
    assert(!f.isSimplex)

  test("RxWithOffset lowers the transmit frequency for a negative offset"):
    val f = RxWithOffset(mhz"147.750", mhz"-0.600")
    assertEquals(f.rx, mhz"147.750")
    assertEquals(f.tx, mhz"147.150")
    assert(!f.isSimplex)

  test("RxWithOffset defaults to simplex when the offset is omitted"):
    val f = RxWithOffset(mhz"146.520")
    assertEquals(f.offset, mhz"0")
    assertEquals(f.rx, mhz"146.520")
    assertEquals(f.rx, f.tx)
    assert(f.isSimplex)

  test("RxWithOffset treats an explicit decimal zero offset as simplex"):
    val f = RxWithOffset(mhz"441.050", mhz"0.000")
    assertEquals(f.rx, mhz"441.050")
    assertEquals(f.tx, mhz"441.050")
    assert(f.isSimplex)
