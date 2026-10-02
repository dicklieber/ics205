package ics205.web.admin

import ics205.auth.{AuthenticatedUser, RolePermissions, User}

class UserAdminPageTests extends munit.FunSuite:

  val adminUser = AuthenticatedUser(
    username = "admin",
    role = RolePermissions.Admin,
    id = "u-admin"
  )

  test("UserAdminPage does not use inline onsubmit for delete confirmation and uses data-confirm"):
    val maliciousUsername = "admin', (alert(document.cookie), true) || '"
    val user = User(
      username = maliciousUsername,
      passwordHash = "hash",
      role = RolePermissions.User,
      enabled = true,
      id = "u-malicious"
    )

    val html = UserAdminPage.render(
      currentUser = adminUser,
      users = Seq(user)
    )

    assert(!html.contains("onsubmit="), "Should not contain inline onsubmit handler")
    assert(html.contains("data-confirm="), "Should use data-confirm attribute")
    assert(html.contains("document.addEventListener('submit'"), "Should include unobtrusive submit listener")
    assert(!html.contains(s"confirm('Are you sure you want to delete user \\'$maliciousUsername\\'?'"), "Should not interpolate into confirm string literal")

  test("UserAdminPage renders two password fields for Add New User form"):
    val html = UserAdminPage.render(
      currentUser = Some(adminUser),
      users = Seq.empty
    )

    assert(html.contains("name=\"password\""), "Should contain password input")
    assert(html.contains("name=\"confirmPassword\""), "Should contain confirmPassword input")
    assert(html.contains("Confirm Password"), "Should contain label for Confirm Password")

  test("UserAdminPage renders two password fields for Edit User form"):
    val user = User(
      username = "testuser",
      passwordHash = "hash",
      role = RolePermissions.User,
      enabled = true,
      id = "u-test"
    )
    val html = UserAdminPage.render(
      currentUser = Some(adminUser),
      users = Seq(user),
      editingUserId = Some("u-test")
    )

    assert(html.contains("name=\"password\""), "Should contain password input")
    assert(html.contains("name=\"confirmPassword\""), "Should contain confirmPassword input")
    assert(html.contains("Confirm New Password"), "Should contain label for Confirm New Password")
