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

package ics205.web.auth

import ics205.auth.{AuthenticatedUser, Role}

class ChangePasswordPageTests extends munit.FunSuite:

  val testUser = AuthenticatedUser(user = "testuser", session = Role.User)

  test("ChangePasswordPage renders change password form with all required fields"):
    val html = ChangePasswordPage.render(
      currentUser = Some(testUser),
      message = None,
      error = None
    )

    assert(html.contains("Change Password"))
    assert(html.contains("action=\"/change-password\""))
    assert(html.contains("method=\"post\""))
    assert(html.contains("name=\"currentPassword\""))
    assert(html.contains("name=\"newPassword\""))
    assert(html.contains("name=\"confirmPassword\""))
    assert(html.contains("id=\"currentPassword\""))
    assert(html.contains("id=\"newPassword\""))
    assert(html.contains("id=\"confirmPassword\""))
    assert(html.contains("Logged in as:"))
    assert(html.contains("testuser"))
    assert(html.contains("document.addEventListener('submit'"))

  test("ChangePasswordPage renders message and error alerts when present"):
    val htmlWithMsg = ChangePasswordPage.render(
      currentUser = Some(testUser),
      message = Some("Password updated successfully!"),
      error = None
    )
    assert(htmlWithMsg.contains("alert alert-success"))
    assert(htmlWithMsg.contains("Password updated successfully!"))

    val htmlWithErr = ChangePasswordPage.render(
      currentUser = Some(testUser),
      message = None,
      error = Some("Current password is incorrect.")
    )
    assert(htmlWithErr.contains("alert alert-error"))
    assert(htmlWithErr.contains("Current password is incorrect."))
