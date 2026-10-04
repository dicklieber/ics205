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

package ics205.config

import com.github.andyglow.config.*
import com.typesafe.config.ConfigFactory

class TypesafeConfigScalaTests extends munit.FunSuite:
  test("typesafe-config-scala extension methods"):
    val config = ConfigFactory.parseString(
      """
        |app {
        |  name = "test-app"
        |  port = 8080
        |  tags = ["a", "b"]
        |}
        |""".stripMargin
    )
    val appName = config.get[String]("app.name")
    val port = config.get[Int]("app.port")
    val opt = config.get[Option[String]]("app.nonexistent")
    val tags = config.get[List[String]]("app.tags")

    assertEquals(appName, "test-app")
    assertEquals(port, 8080)
    assertEquals(opt, None)
    assertEquals(tags, List("a", "b"))

  test("reference.conf defines default port as 8080"):
    val config = ConfigFactory.load()
    assertEquals(config.getInt("port"), 8080)

  test("PORT overrides default port from reference.conf"):
    val conf = ConfigFactory.parseString(
      """
        |port = 8080
        |port = ${?PORT}
        |""".stripMargin
    ).withFallback(ConfigFactory.parseString("PORT = 9999")).resolve()
    assertEquals(conf.getInt("port"), 9999)

  test("PORT environment variable or system property overrides port in ConfigFactory.load"):
    System.setProperty("PORT", "7777")
    try
      ConfigFactory.invalidateCaches()
      val config = ConfigFactory.load()
      assertEquals(config.getInt("port"), 7777)
    finally
      System.clearProperty("PORT")
      ConfigFactory.invalidateCaches()
