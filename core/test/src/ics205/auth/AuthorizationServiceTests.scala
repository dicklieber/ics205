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

class AuthorizationServiceTests extends munit.FunSuite:
  private val admin = AuthenticatedUser("admin", RolePermissions.Admin, id = "1")
  private val editor = AuthenticatedUser("editor", RolePermissions.Editor, id = "2")
  private val user = AuthenticatedUser("user", RolePermissions.User, id = "3")
  private val viewer = AuthenticatedUser("viewer", RolePermissions.Viewer, id = "4")

  test("admin has all system permissions"):
    assertEquals(AuthorizationService.authorize(admin, Permission.ViewUsers), Right(admin))
    assertEquals(AuthorizationService.authorize(admin, Permission.EditUsers), Right(admin))
    assertEquals(AuthorizationService.authorize(admin, Permission.ConfigureSystem), Right(admin))
    assertEquals(AuthorizationService.authorize(admin, Permission.ViewPlans), Right(admin))
    assertEquals(AuthorizationService.authorize(admin, Permission.EditPlans), Right(admin))

  test("editor has plan permissions but not user/system management permissions"):
    assertEquals(AuthorizationService.authorize(editor, Permission.ViewPlans), Right(editor))
    assertEquals(AuthorizationService.authorize(editor, Permission.EditPlans), Right(editor))
    assert(AuthorizationService.authorize(editor, Permission.ViewUsers).isLeft)
    assert(AuthorizationService.authorize(editor, Permission.ConfigureSystem).isLeft)

  test("viewer and user have view permission but not edit permission"):
    assertEquals(AuthorizationService.authorize(viewer, Permission.ViewPlans), Right(viewer))
    assert(AuthorizationService.authorize(viewer, Permission.EditPlans).isLeft)
    assert(AuthorizationService.authorize(viewer, Permission.ViewUsers).isLeft)
    assertEquals(AuthorizationService.authorize(user, Permission.ViewPlans), Right(user))
    assert(AuthorizationService.authorize(user, Permission.EditPlans).isLeft)
