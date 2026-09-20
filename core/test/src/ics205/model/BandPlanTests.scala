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

class BandPlanTests extends munit.FunSuite:
  test("plus offset"):
    val f = TxOffsetDir(Frequency(BigDecimal("442.725")), Frequency(BigDecimal("5.000")), Direction.Plus)
    assertEquals(f.rx, Frequency(BigDecimal("442.725")))
    assertEquals(f.tx, Frequency(BigDecimal("447.725")))

  test("minus offset"):
    val f = TxOffsetDir(Frequency(BigDecimal("147.750")), Frequency(BigDecimal("0.600")), Direction.Minus)
    assertEquals(f.tx, Frequency(BigDecimal("147.150")))

  test("simplex"):
    val f = TxOffsetDir(Frequency(BigDecimal("441.050")), Frequency(BigDecimal("5.000")), Direction.Simplex)
    assertEquals(f.rx, f.tx)
