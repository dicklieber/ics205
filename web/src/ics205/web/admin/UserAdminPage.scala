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

package ics205.web.admin

import ics205.auth.{AuthenticatedUser, Role, User}
import ics205.web.NavigationBar
import scalatags.Text.all.*
import scalatags.Text.tags2.{details, summary}

object UserAdminPage:

  def render(
    currentUser: AuthenticatedUser,
    users: Seq[User]
  ): String = render(Some(currentUser), users, None, None, None, Set("Default"))

  def render(
    currentUser: AuthenticatedUser,
    users: Seq[User],
    editingUserId: Option[String],
    message: Option[String],
    error: Option[String]
  ): String = render(Some(currentUser), users, editingUserId, message, error, Set("Default"))

  def render(
    currentUser: Option[AuthenticatedUser],
    users: Seq[User],
    editingUserId: Option[String] = None,
    message: Option[String] = None,
    error: Option[String] = None,
    knownGroups: Set[String] = Set("Default")
  ): String =
    val editingUser = editingUserId.flatMap(id => users.find(_.id == id))
    val isEdit = editingUser.isDefined

    doctype("html")(
      html(lang := "en")(
        head(
          meta(charset := "utf-8"),
          meta(name := "viewport", content := "width=device-width, initial-scale=1"),
          scalatags.Text.tags2.title("ICS 205 — User Administration"),
          link(rel := "stylesheet", href := "/css/navbar.css"),
          link(rel := "stylesheet", href := "/css/admin.css")
        ),
        body(
          NavigationBar.render(NavigationBar.ActivePage.UserAdmin, currentUser),
          div(cls := "admin-container")(
            div(cls := "admin-header")(
              div(
                h1("User Administration")
              )
            ),

            message.filter(_.nonEmpty).map(msg =>
              div(cls := "alert alert-success", attr("role") := "status")(msg)
            ),

            error.filter(_.nonEmpty).map(err =>
              div(cls := "alert alert-error", attr("role") := "alert")(err)
            ),

            div(cls := "admin-grid")(
              div(cls := "card")(
                h2("Users"),
                table(cls := "users-table")(
                  thead(
                    tr(
                      th("Username"),
                      th("Role"),
                      th("Groups"),
                      th("Status"),
                      th("Actions")
                    )
                  ),
                  tbody(
                    if users.isEmpty then
                      tr(td(colspan := 5, style := "text-align: center; color: #6b778c; padding: 16px;")("No users found."))
                    else
                      users.map { user =>
                        tr(
                          td(strong(user.username)),
                          td(span(cls := "badge badge-role")(user.role.toString)),
                          td(
                            if user.role == Role.Admin then
                              span(style := "color: #5e6c84; font-style: italic;")("All (Admin)")
                            else if user.groups.isEmpty then
                              span(style := "color: #6b778c; font-style: italic;")("None")
                            else
                              user.groups.toSeq.sorted.map(g =>
                                span(cls := "badge badge-role", style := "margin-right: 4px; display: inline-block;")(g)
                              )
                          ),
                          td(
                            if user.enabled then
                              span(cls := "badge badge-active")("Active")
                            else
                              span(cls := "badge badge-disabled")("Disabled")
                          ),
                          td(cls := "actions-cell")(
                            a(href := s"/admin/users?edit=${user.id}#user-form", cls := "btn btn-secondary btn-sm")("Edit"),
                            form(
                              method := "post",
                              action := "/admin/users/delete",
                              attr("data-confirm") := s"Are you sure you want to delete user '${user.username}'?"
                            )(
                              input(tpe := "hidden", name := "id", value := user.id),
                              button(tpe := "submit", cls := "btn btn-danger btn-sm")("Delete")
                            )
                          )
                        )
                      }
                  )
                )
              ),

              div(cls := "card", id := "user-form")(
                h2(if isEdit then s"Edit User: ${editingUser.get.username}" else "Add New User"),
                form(
                  method := "post",
                  action := (if isEdit then "/admin/users/edit" else "/admin/users/create")
                )(
                  editingUser.map(u => input(tpe := "hidden", name := "id", value := u.id)),

                  div(cls := "form-group")(
                    label(attr("for") := "username")("Username"),
                    input(
                      tpe := "text",
                      id := "username",
                      name := "username",
                      required,
                      value := editingUser.map(_.username).getOrElse("")
                    )
                  ),

                  div(cls := "form-group")(
                    label(attr("for") := "password")(if isEdit then "New Password" else "Password"),
                    input(
                      tpe := "password",
                      id := "password",
                      name := "password",
                      if !isEdit then required else cls := ""
                    ),
                    p(cls := "form-help")(
                      if isEdit then "Leave blank to keep current password."
                      else "Enter a secure password for the new account."
                    )
                  ),

                  div(cls := "form-group")(
                    label(attr("for") := "confirmPassword")(if isEdit then "Confirm New Password" else "Confirm Password"),
                    input(
                      tpe := "password",
                      id := "confirmPassword",
                      name := "confirmPassword",
                      if !isEdit then required else cls := ""
                    ),
                    p(cls := "form-help")(
                      if isEdit then "Leave blank to keep current password."
                      else "Re-enter the password to confirm."
                    )
                  ),

                  div(cls := "form-group")(
                    label(attr("for") := "role")("Role"),
                    select(
                      id := "role",
                      name := "role"
                    )(
                      Role.values.map { r =>
                        option(
                          value := r.toString.toLowerCase,
                          if editingUser.exists(_.role == r) || (editingUser.isEmpty && r == (if users.isEmpty then Role.Admin else Role.User)) then selected else cls := ""
                        )(r.toString)
                      }
                    ),
                    details(cls := "role-permissions-info", style := "margin-top: 6px; padding: 6px 10px; background: #f4f5f7; border: 1px solid #ebecf0; border-radius: 4px; font-size: 8.5pt;")(
                      summary(style := "cursor: pointer; color: #0052cc; font-weight: 600;")("Role Permissions Reference:"),
                      ul(style := "margin: 4px 0 0 0; padding-left: 18px;")(
                        Role.values.map { r =>
                          li(
                            strong(r.toString),
                            ": ",
                            span(style := "color: #42526e;")(r.permissions.map(_.toString).toSeq.sorted.mkString(", "))
                          )
                        }
                      )
                    )
                  ),

                  div(cls := "form-group", id := "groups-form-group")(
                    label("Groups"),
                    p(cls := "form-help")(
                      "Assign this user to zero or more groups (non-admin users only)."
                    ),
                    div(cls := "groups-checkbox-list", style := "display: flex; flex-wrap: wrap; gap: 10px; margin-bottom: 8px;")(
                      knownGroups.toSeq.sorted.map { g =>
                        label(cls := "checkbox-inline", style := "display: inline-flex; align-items: center; gap: 4px; margin-right: 8px;")(
                          input(
                            tpe := "checkbox",
                            name := s"group_$g",
                            value := "true",
                            if editingUser.exists(_.groups.contains(g)) then checked else cls := ""
                          ),
                          span(g)
                        )
                      }
                    ),
                    div(cls := "form-group", style := "margin-top: 6px;")(
                      label(attr("for") := "newGroup", style := "font-weight: normal; font-size: 9pt;")("Or create new group:"),
                      input(
                        tpe := "text",
                        id := "newGroup",
                        name := "newGroup",
                        placeholder := "e.g. Field Operations"
                      ),
                      p(cls := "form-help")(
                        "New group names are automatically formatted to Capitalized Words (e.g. 'hello world' -> 'Hello World')."
                      )
                    )
                  ),

                  div(cls := "form-group")(
                    label(cls := "checkbox-group")(
                      input(
                        tpe := "checkbox",
                        name := "enabled",
                        value := "true",
                        if editingUser.forall(_.enabled) then checked else cls := ""
                      ),
                      span(" Account is enabled (can log in)")
                    )
                  ),

                  div(style := "margin-top: 16px; display: flex; gap: 8px; align-items: center;")(
                    button(tpe := "submit", cls := "btn btn-primary")(
                      if isEdit then "Update User" else "Create User"
                    ),
                    if isEdit then
                      a(href := "/admin/users", cls := "btn btn-secondary")("Cancel")
                    else
                      span()
                  )
                )
              )
            )
          ),
          script(raw(
            """document.addEventListener('submit', function(e) {
              |  var form = e.target;
              |  if (form && form.hasAttribute('data-confirm')) {
              |    if (!confirm(form.getAttribute('data-confirm'))) {
              |      e.preventDefault();
              |      return;
              |    }
              |  }
              |  if (form) {
              |    var pass = form.querySelector('input[name="password"]');
              |    var confirmPass = form.querySelector('input[name="confirmPassword"]');
              |    if (pass && confirmPass && pass.value !== confirmPass.value) {
              |      alert('Passwords do not match.');
              |      e.preventDefault();
              |    }
              |  }
              |});""".stripMargin
          ))
        )
      )
    ).render
