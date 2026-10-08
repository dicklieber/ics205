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

import org.apache.logging.log4j.Level
import org.apache.logging.log4j.core.LoggerContext
import org.apache.logging.log4j.core.appender.RollingFileAppender

class LoggingConfigTests extends munit.FunSuite:

  test("createConfiguration generates configuration with expected name, appenders and loggers"):
    val tempDir = os.temp.dir()
    try
      val config = LoggingConfig.createConfiguration(tempDir)
      assertEquals(config.getName, "ICS205")

      // Appenders
      val appenders = config.getAppenders
      assert(appenders.containsKey("Console"), "Console appender should be present")
      assert(appenders.containsKey("File"), "File appender should be present")
      assert(appenders.containsKey("AccessLog"), "AccessLog appender should be present")

      // File Appender paths
      val fileAppender = appenders.get("File").asInstanceOf[RollingFileAppender]
      assertEquals(fileAppender.getFileName, (tempDir / "ics205.log").toString)

      // AccessLog Appender paths
      val accessAppender = appenders.get("AccessLog").asInstanceOf[RollingFileAppender]
      assertEquals(accessAppender.getFileName, (tempDir / "access.log").toString)

      // Loggers
      val loggers = config.getLoggers
      assert(loggers.containsKey("ics205.exporter.RadioExportDefinitions"))
      assertEquals(loggers.get("ics205.exporter.RadioExportDefinitions").getLevel, Level.DEBUG)

      assert(loggers.containsKey("ics205.web.HttpAccessLog"))
      val accessLogger = loggers.get("ics205.web.HttpAccessLog")
      assertEquals(accessLogger.getLevel, Level.INFO)
      assertEquals(accessLogger.isAdditive, false)
      assert(accessLogger.getAppenders.containsKey("AccessLog"))

      // Root Logger
      val rootLogger = config.getRootLogger
      assertEquals(rootLogger.getLevel, Level.INFO)
      assert(rootLogger.getAppenders.containsKey("Console"))
      assert(rootLogger.getAppenders.containsKey("File"))
    finally
      os.remove.all(tempDir)

  test("init programmatically initializes Log4j2"):
    val tempDir = os.temp.dir()
    try
      val oldProp = sys.props.get("log4j.configurationFile")
      sys.props.remove("log4j.configurationFile")
      try
        val ctx = LoggingConfig.init(tempDir)
        assert(ctx != null)
        val activeConfig = ctx.getConfiguration
        val fileAppender = activeConfig.getAppender[RollingFileAppender]("File")
        assert(fileAppender != null)
        assertEquals(fileAppender.getFileName, (tempDir / "ics205.log").toString)
      finally
        oldProp.foreach(p => sys.props("log4j.configurationFile") = p)
    finally
      os.remove.all(tempDir)

  test("init respects explicit log4j.configurationFile system property"):
    val tempDir = os.temp.dir()
    try
      val explicitUri = (tempDir / "custom.xml").toNIO.toUri.toString
      val oldProp = sys.props.get("log4j.configurationFile")
      sys.props("log4j.configurationFile") = explicitUri
      try
        val ctx = LoggingConfig.init(tempDir)
        assert(ctx != null)
      finally
        oldProp match
          case Some(p) => sys.props("log4j.configurationFile") = p
          case None => sys.props.remove("log4j.configurationFile")
    finally
      os.remove.all(tempDir)
