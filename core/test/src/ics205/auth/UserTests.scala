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

class UserTests extends munit.FunSuite:
  test("User ordering is case-insensitive by username"):
    val u1 = User(username = "alice", passwordHash = "", role = Role.User)
    val u2 = User(username = "Bob", passwordHash = "", role = Role.User)
    val u3 = User(username = "charlie", passwordHash = "", role = Role.User)
    val u4 = User(username = "DAVID", passwordHash = "", role = Role.User)

    val users = Seq(u4, u2, u1, u3)
    val sorted = users.sorted

    assertEquals(sorted.map(_.username), Seq("alice", "Bob", "charlie", "DAVID"))
