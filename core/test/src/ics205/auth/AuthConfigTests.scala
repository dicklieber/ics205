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

import com.typesafe.config.ConfigFactory
import java.time.Duration

class AuthConfigTests extends munit.FunSuite:
  test("AuthConfig loads default values from reference.conf"):
    val config = AuthConfig()
    assertEquals(config.userFileName, "users.json")
    assertEquals(config.sessionFileName, "sessions.json")
    assertEquals(config.sessionLifetime, Duration.ofHours(24))
    assertEquals(config.cookieName, "session")
    assertEquals(config.secureCookie, false)

  test("AuthConfig parses custom Config under auth prefix"):
    val custom = ConfigFactory.parseString(
      """
        |auth {
        |  userFileName = "custom_users.json"
        |  sessionFileName = "custom_sessions.json"
        |  sessionLifetime = 2h
        |  cookieName = "custom_session"
        |  secureCookie = true
        |}
        |""".stripMargin
    )
    val config = AuthConfig.fromConfig(custom)
    assertEquals(config.userFileName, "custom_users.json")
    assertEquals(config.sessionFileName, "custom_sessions.json")
    assertEquals(config.sessionLifetime, Duration.ofHours(2))
    assertEquals(config.cookieName, "custom_session")
    assertEquals(config.secureCookie, true)

  test("AuthConfig resiliently parses secureCookie from strings and various truthy/falsy representations"):
    val testCases = Seq(
      ("true", true),
      ("\"true\"", true),
      ("\"1\"", true),
      ("\"yes\"", true),
      ("\"on\"", true),
      ("\"TRUE\"", true),
      ("false", false),
      ("\"false\"", false),
      ("\"0\"", false),
      ("\"no\"", false),
      ("\"off\"", false),
      ("\"\"", false),
      ("\"invalid\"", false)
    )

    for ((valueStr, expected) <- testCases) do
      val conf = ConfigFactory.parseString(s"auth.secureCookie = $valueStr")
      val parsed = AuthConfig.fromConfig(conf)
      assertEquals(parsed.secureCookie, expected, s"Failed for input: $valueStr")

    // Test flat parsing as well
    for ((valueStr, expected) <- testCases) do
      val conf = ConfigFactory.parseString(s"secureCookie = $valueStr")
      val parsed = AuthConfig.fromConfig(conf)
      assertEquals(parsed.secureCookie, expected, s"Failed for flat input: $valueStr")

  test("AuthConfig parses flat custom Config without auth prefix"):
    val custom = ConfigFactory.parseString(
      """
        |userFileName = "flat_users.json"
        |sessionFileName = "flat_sessions.json"
        |sessionLifetime = 30m
        |cookieName = "flat_cookie"
        |secureCookie = true
        |""".stripMargin
    )
    val config = AuthConfig.fromConfig(custom)
    assertEquals(config.userFileName, "flat_users.json")
    assertEquals(config.sessionFileName, "flat_sessions.json")
    assertEquals(config.sessionLifetime, Duration.ofMinutes(30))
    assertEquals(config.cookieName, "flat_cookie")
    assertEquals(config.secureCookie, true)

  test("AuthConfig supports named parameter overrides"):
    val config = AuthConfig(sessionLifetime = Duration.ofSeconds(60))
    assertEquals(config.sessionLifetime, Duration.ofSeconds(60))
    assertEquals(config.userFileName, "users.json")

  test("AuthErrorResponse roundtrips to and from JSON"):
    import io.circe.syntax.*
    import io.circe.parser.*
    val err = AuthErrorResponse("unauthorized", "Access denied")
    val json = err.asJson.noSpaces
    val parsed = decode[AuthErrorResponse](json)
    assertEquals(parsed, Right(err))

  test("Permission fromString and KeyCodec"):
    assertEquals(Permission.fromString("ViewUsers"), Some(Permission.ViewUsers))
    assertEquals(Permission.fromString("UnknownPerm"), None)
    assertEquals(Role.fromString("UnknownRole"), None)
    assert(Role.hasPermission("admin", Permission.EditUsers))
    assert(!Role.hasPermission("viewer", Permission.EditUsers))
