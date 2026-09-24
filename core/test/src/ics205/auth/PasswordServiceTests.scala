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

package ics205.auth

class PasswordServiceTests extends munit.FunSuite:
  private val service = new ScalaPassPasswordService()

  test("hashing and verification of password succeeds"):
    val password = "SuperSecretPassword123!"
    val hash = service.hash(password)
    assert(hash.nonEmpty)
    assert(service.verify(password, hash))

  test("verification with incorrect password fails"):
    val password = "CorrectPassword"
    val hash = service.hash(password)
    assert(!service.verify("WrongPassword", hash))

  test("verification with malformed hash fails safely"):
    assert(!service.verify("Password", "invalid-hash-string"))
