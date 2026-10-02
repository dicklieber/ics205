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

package ics205.util

class LoggingConfigTests extends munit.FunSuite:

  test("findExternalConfig finds log4j2.yaml file in directory"):
    val tempDir = os.temp.dir()
    try
      assertEquals(LoggingConfig.findExternalConfig(tempDir), None)

      val yamlFile = tempDir / "log4j2.yaml"
      os.write(yamlFile, "Configuration:\n  name: Test\n")
      assertEquals(LoggingConfig.findExternalConfig(tempDir), Some(yamlFile))
    finally
      os.remove.all(tempDir)

  test("ensureConfigFile returns existing file and does not overwrite it"):
    val tempDir = os.temp.dir()
    try
      val yamlFile = tempDir / "log4j2.yaml"
      val originalContent = "Configuration:\n  name: CustomConfig\n"
      os.write(yamlFile, originalContent)

      val result = LoggingConfig.ensureConfigFile(tempDir)
      assertEquals(result, Some(yamlFile))
      assertEquals(os.read(yamlFile), originalContent)
    finally
      os.remove.all(tempDir)

  test("init returns None if no external config file exists and no resource/property is available"):
    val tempDir = os.temp.dir()
    try
      val oldProp = sys.props.get("log4j.configurationFile")
      sys.props.remove("log4j.configurationFile")
      try
        val result = LoggingConfig.init(tempDir)
        assertEquals(result, None)
      finally
        oldProp.foreach(p => sys.props("log4j.configurationFile") = p)
    finally
      os.remove.all(tempDir)

  test("init respects explicit log4j.configurationFile system property"):
    val tempDir = os.temp.dir()
    try
      val yamlFile = tempDir / "log4j2.yaml"
      os.write(yamlFile, "Configuration:\n  name: Test\n")

      val explicitPath = "/custom/path/log4j2.yaml"
      val oldProp = sys.props.get("log4j.configurationFile")
      sys.props("log4j.configurationFile") = explicitPath
      try
        val result = LoggingConfig.init(tempDir)
        assertEquals(result, None)
        assertEquals(sys.props("log4j.configurationFile"), explicitPath)
      finally
        oldProp match
          case Some(p) => sys.props("log4j.configurationFile") = p
          case None => sys.props.remove("log4j.configurationFile")
    finally
      os.remove.all(tempDir)

  test("init loads external config when present and sets system property"):
    val tempDir = os.temp.dir()
    try
      val yamlFile = tempDir / "log4j2.yaml"
      os.write(yamlFile, """Configuration:
                           |  name: ICS205External
                           |  monitorInterval: 30
                           |  Appenders:
                           |    Console:
                           |      name: Console
                           |      target: SYSTEM_OUT
                           |  Loggers:
                           |    Root:
                           |      level: debug
                           |      AppenderRef:
                           |        - ref: Console
                           |""".stripMargin)

      val oldProp = sys.props.get("log4j.configurationFile")
      sys.props.remove("log4j.configurationFile")
      try
        val result = LoggingConfig.init(tempDir)
        assertEquals(result, Some(yamlFile))
        assertEquals(sys.props("log4j.configurationFile"), yamlFile.toString)
      finally
        oldProp match
          case Some(p) => sys.props("log4j.configurationFile") = p
          case None => sys.props.remove("log4j.configurationFile")
    finally
      os.remove.all(tempDir)
