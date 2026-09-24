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

import scalatags.Text.all.*

object LoginPage:

  def render(
    message: Option[String] = None,
    error: Option[String] = None,
    redirect: Option[String] = None
  ): String =
    doctype("html")(
      html(lang := "en")(
        head(
          meta(charset := "utf-8"),
          meta(name := "viewport", content := "width=device-width, initial-scale=1"),
          scalatags.Text.tags2.title("ICS 205 — Login"),
          link(rel := "stylesheet", href := "/css/admin.css")
        ),
        body(
          div(cls := "admin-container", style := "max-width: 440px; margin: 60px auto;")(
            div(style := "text-align: center; margin-bottom: 24px;")(
              h1(style := "font-size: 20pt; margin-bottom: 6px; color: #172b4d;")("ICS 205 Plan Editor"),
              p(style := "color: #5e6c84; margin: 0;")("Please log in to continue")
            ),

            message.filter(_.nonEmpty).map(msg =>
              div(cls := "alert alert-success", attr("role") := "status")(msg)
            ),

            error.filter(_.nonEmpty).map(err =>
              div(cls := "alert alert-error", attr("role") := "alert")(err)
            ),

            div(cls := "card")(
              form(method := "post", action := "/login")(
                redirect.filter(_.nonEmpty).map(r =>
                  input(tpe := "hidden", name := "redirect", value := r)
                ),

                div(cls := "form-group")(
                  label(attr("for") := "username")("Username"),
                  input(
                    tpe := "text",
                    id := "username",
                    name := "username",
                    required,
                    autofocus,
                    placeholder := "Enter your username"
                  )
                ),

                div(cls := "form-group")(
                  label(attr("for") := "password")("Password"),
                  input(
                    tpe := "password",
                    id := "password",
                    name := "password",
                    required,
                    placeholder := "Enter your password"
                  )
                ),

                div(style := "margin-top: 24px;")(
                  button(tpe := "submit", cls := "btn btn-primary", style := "width: 100%; padding: 10px; font-size: 11pt;")("Log in")
                )
              )
            )
          )
        )
      )
    ).render
