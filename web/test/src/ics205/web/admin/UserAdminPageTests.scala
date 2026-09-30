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
