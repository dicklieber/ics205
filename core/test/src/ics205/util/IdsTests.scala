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

import ics205.util.Ids.Id

class IdsTests extends munit.FunSuite:

  override def afterEach(context: AfterEach): Unit =
    Ids.revertToRandom()

  test("generateId generates random url-safe base64 id by default"):
    Ids.revertToRandom()
    val id1: Id = Ids.generateId()
    val id2: Id = Ids.generateId()
    assert(id1.nonEmpty)
    assert(id2.nonEmpty)
    assertNotEquals(id1, id2)
    assertEquals(id1.length, Ids.IdSize)

  test("useSeqentialStartingAt generates predictable sequence of IDs"):
    Ids.useSeqentialStartingAt(1)
    assertEquals(Ids.generateId(), "1")
    assertEquals(Ids.generateId(), "2")
    assertEquals(Ids.generateId(), "3")

    Ids.useSeqentialStartingAt(100)
    assertEquals(Ids.generateId(), "100")
    assertEquals(Ids.generateId(), "101")

  test("useSequentialStartingAt alias generates predictable sequence of IDs"):
    Ids.useSequentialStartingAt(50)
    assertEquals(Ids.generateId(), "50")
    assertEquals(Ids.generateId(), "51")

  test("revertToRandom restores random generation"):
    Ids.useSeqentialStartingAt(1)
    assertEquals(Ids.generateId(), "1")
    Ids.revertToRandom()
    val randomId = Ids.generateId()
    assertNotEquals(randomId, "2")
    assertEquals(randomId.length, Ids.IdSize)

  test("generateInstanceId creates 3-character compact id"):
    val instanceId = Ids.generateInstanceId()
    assert(instanceId.nonEmpty)
    assertEquals(instanceId.length, 3)
