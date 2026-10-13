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
  private val admin = AuthenticatedUser("admin", Role.Admin)
  private val editor = AuthenticatedUser("editor", Role.Editor)
  private val user = AuthenticatedUser("user", Role.Viewer)
  private val viewer = AuthenticatedUser("viewer", Role.Viewer)

  test("admin has all system permissions"):
    assertEquals(AuthorizationService.authorize(admin, Permission.ViewUsers), Right(admin))
    assertEquals(AuthorizationService.authorize(admin, Permission.EditUsers), Right(admin))
    assertEquals(AuthorizationService.authorize(admin, Permission.Debug), Right(admin))
    assertEquals(AuthorizationService.authorize(admin, Permission.ViewPlans), Right(admin))
    assertEquals(AuthorizationService.authorize(admin, Permission.EditPlans), Right(admin))
    assertEquals(AuthorizationService.authorize(admin, Permission.AllGroup), Right(admin))

  test("editor has plan permissions but not user/system management or AllGroup permissions"):
    assertEquals(AuthorizationService.authorize(editor, Permission.ViewPlans), Right(editor))
    assertEquals(AuthorizationService.authorize(editor, Permission.EditPlans), Right(editor))
    assert(AuthorizationService.authorize(editor, Permission.ViewUsers).isLeft)
    assert(AuthorizationService.authorize(editor, Permission.Debug).isLeft)
    assert(AuthorizationService.authorize(editor, Permission.AllGroup).isLeft)

  test("viewer and user have view permission but not edit or AllGroup permission"):
    assertEquals(AuthorizationService.authorize(viewer, Permission.ViewPlans), Right(viewer))
    assert(AuthorizationService.authorize(viewer, Permission.EditPlans).isLeft)
    assert(AuthorizationService.authorize(viewer, Permission.ViewUsers).isLeft)
    assert(AuthorizationService.authorize(viewer, Permission.AllGroup).isLeft)
    assertEquals(AuthorizationService.authorize(user, Permission.ViewPlans), Right(user))
    assert(AuthorizationService.authorize(user, Permission.EditPlans).isLeft)
    assert(AuthorizationService.authorize(user, Permission.AllGroup).isLeft)

  test("authorizeGroup grants access to all groups when user has AllGroup permission"):
    assertEquals(AuthorizationService.authorizeGroup(admin, "Any Group"), Right(admin))
    assertEquals(AuthorizationService.authorizeGroup(admin, "Default"), Right(admin))

  test("authorizeGroup checks assigned groups when user lacks AllGroup permission"):
    val member = AuthenticatedUser(User("member", "pass", Role.Viewer, groups = Set("North Team", "Ares Ops")))
    assertEquals(AuthorizationService.authorizeGroup(member, "North Team"), Right(member))
    assertEquals(AuthorizationService.authorizeGroup(member, "north team"), Right(member))
    assertEquals(AuthorizationService.authorizeGroup(member, "Ares Ops"), Right(member))
    assert(AuthorizationService.authorizeGroup(member, "South Team").isLeft)
    assert(AuthorizationService.authorizeGroup(user, "Any Group").isLeft)
