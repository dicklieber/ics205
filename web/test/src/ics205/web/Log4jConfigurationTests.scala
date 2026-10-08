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

package ics205.web

import com.typesafe.scalalogging.LazyLogging
import ics205.util.LoggingConfig
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.core.{Appender, LoggerContext}
import org.apache.logging.log4j.core.appender.RollingFileAppender

class Log4jConfigurationTests extends munit.FunSuite with LazyLogging:

  test("LoggingConfig.init configures a file appender writing to log directory"):
    val tempDir = os.temp.dir()
    try
      val ctx = LoggingConfig.init(tempDir)
      val config = ctx.getConfiguration
      val appender: Appender = config.getAppender[Appender]("File")
      assert(appender != null, "File appender should be configured")
      assert(appender.isInstanceOf[RollingFileAppender], "File appender should be a RollingFileAppender")

      val rollingAppender = appender.asInstanceOf[RollingFileAppender]
      val fileName = rollingAppender.getFileName
      assertEquals(fileName, (tempDir / "ics205.log").toString)

      val testLogMessage = s"Log4jConfigurationTests verification marker ${System.currentTimeMillis()}"
      logger.info(testLogMessage)

      val logFilePath = os.Path(fileName)
      assert(os.exists(logFilePath), s"Log file should exist at $logFilePath")
      val logContent = os.read(logFilePath)
      assert(logContent.contains(testLogMessage), s"Log file should contain logged message: $testLogMessage")
    finally
      os.remove.all(tempDir)

  test("LoggingConfig.init configures an access log rolling file appender writing to log directory"):
    val tempDir = os.temp.dir()
    try
      val ctx = LoggingConfig.init(tempDir)
      val config = ctx.getConfiguration
      val appender: Appender = config.getAppender[Appender]("AccessLog")
      assert(appender != null, "AccessLog appender should be configured")
      assert(appender.isInstanceOf[RollingFileAppender], "AccessLog appender should be a RollingFileAppender")

      val rollingAppender = appender.asInstanceOf[RollingFileAppender]
      val fileName = rollingAppender.getFileName
      assertEquals(fileName, (tempDir / "access.log").toString)

      val accessLogger = org.slf4j.LoggerFactory.getLogger(classOf[HttpAccessLog])
      val testAccessMessage = s"127.0.0.1 - testuser [07/Oct/2026:18:35:00 +0000] \"GET /test HTTP/1.1\" 200 123 - ${System.currentTimeMillis()}"
      accessLogger.info(testAccessMessage)

      val logFilePath = os.Path(fileName)
      assert(os.exists(logFilePath), s"Access log file should exist at $logFilePath")
      val logContent = os.read(logFilePath)
      assert(logContent.contains(testAccessMessage), s"Access log file should contain logged message: $testAccessMessage")
    finally
      os.remove.all(tempDir)
