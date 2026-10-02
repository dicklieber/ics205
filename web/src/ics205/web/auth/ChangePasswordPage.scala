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

import ics205.auth.AuthenticatedUser
import ics205.web.NavigationBar
import scalatags.Text.all.*

object ChangePasswordPage:

  def render(
    currentUser: Option[AuthenticatedUser],
    message: Option[String] = None,
    error: Option[String] = None
  ): String =
    doctype("html")(
      html(lang := "en")(
        head(
          meta(charset := "utf-8"),
          meta(name := "viewport", content := "width=device-width, initial-scale=1"),
          scalatags.Text.tags2.title("ICS 205 — Change Password"),
          link(rel := "stylesheet", href := "/css/navbar.css"),
          link(rel := "stylesheet", href := "/css/admin.css")
        ),
        body(
          NavigationBar.render(NavigationBar.ActivePage.ChangePassword, currentUser),
          div(cls := "admin-container", style := "max-width: 500px; margin: 40px auto;")(
            div(cls := "admin-header")(
              div(
                h1("Change Password")
              )
            ),

            message.filter(_.nonEmpty).map(msg =>
              div(cls := "alert alert-success", attr("role") := "status")(msg)
            ),

            error.filter(_.nonEmpty).map(err =>
              div(cls := "alert alert-error", attr("role") := "alert")(err)
            ),

            div(cls := "card")(
              form(method := "post", action := "/change-password")(
                div(cls := "form-group")(
                  label(attr("for") := "currentPassword")("Current Password"),
                  input(
                    tpe := "password",
                    id := "currentPassword",
                    name := "currentPassword",
                    required,
                    autofocus,
                    placeholder := "Enter current password"
                  )
                ),

                div(cls := "form-group")(
                  label(attr("for") := "newPassword")("New Password"),
                  input(
                    tpe := "password",
                    id := "newPassword",
                    name := "newPassword",
                    required,
                    placeholder := "Enter new password (min. 8 characters)"
                  ),
                  p(cls := "form-help")("Password must be at least 8 characters.")
                ),

                div(cls := "form-group")(
                  label(attr("for") := "confirmPassword")("Confirm New Password"),
                  input(
                    tpe := "password",
                    id := "confirmPassword",
                    name := "confirmPassword",
                    required,
                    placeholder := "Re-enter new password to confirm"
                  ),
                  p(cls := "form-help")("Re-enter your new password to confirm.")
                ),

                div(style := "margin-top: 24px; display: flex; gap: 10px; align-items: center;")(
                  button(tpe := "submit", cls := "btn btn-primary")("Change Password"),
                  a(href := "/", cls := "btn btn-secondary")("Cancel")
                )
              )
            )
          ),
          script(raw(
            """document.addEventListener('submit', function(e) {
              |  var form = e.target;
              |  if (form) {
              |    var pass = form.querySelector('input[name="newPassword"]') || form.querySelector('input[name="password"]');
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
