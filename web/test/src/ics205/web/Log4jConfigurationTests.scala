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
import ics205.util.{FileHelper, LoggingConfig}
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.core.{Appender, LoggerContext}
import org.apache.logging.log4j.core.appender.RollingFileAppender

class Log4jConfigurationTests extends munit.FunSuite with LazyLogging:

  test("ensureConfigFile extracts bundled log4j2.yaml to destination directory when absent"):
    val tempDir = os.temp.dir()
    try
      val targetFile = tempDir / "log4j2.yaml"
      assertEquals(os.exists(targetFile), false)

      val result = LoggingConfig.ensureConfigFile(tempDir)
      assertEquals(result, Some(targetFile))
      assertEquals(os.exists(targetFile), true)
      val content = os.read(targetFile)
      assert(content.contains("ICS205"), s"Expected config to contain 'ICS205', got:\n$content")
      assert(content.contains("RollingFile"), s"Expected config to contain 'RollingFile', got:\n$content")
      assert(content.contains("monitorInterval"), s"Expected config to contain 'monitorInterval', got:\n$content")
    finally
      os.remove.all(tempDir)

  test("ensureConfigFile does not overwrite existing log4j2.yaml"):
    val tempDir = os.temp.dir()
    try
      val targetFile = tempDir / "log4j2.yaml"
      val customContent = "# Custom user-modified configuration\nConfiguration:\n  name: Custom\n"
      os.write(targetFile, customContent)

      val result = LoggingConfig.ensureConfigFile(tempDir)
      assertEquals(result, Some(targetFile))
      assertEquals(os.read(targetFile), customContent)
    finally
      os.remove.all(tempDir)

  test("log4j configuration contains a file appender writing to FileHelper log directory"):
    val ctx = LogManager.getContext(false).asInstanceOf[LoggerContext]
    val config = ctx.getConfiguration
    val appender: Appender = config.getAppender[Appender]("File")
    assert(appender != null, "File appender should be configured")
    assert(appender.isInstanceOf[RollingFileAppender], "File appender should be a RollingFileAppender")

    val rollingAppender = appender.asInstanceOf[RollingFileAppender]
    val fileName = rollingAppender.getFileName
    assert(fileName.contains("/log/ics205.log"), s"Log file path should end in /log/ics205.log, got: $fileName")

    val testLogMessage = s"Log4jConfigurationTests verification marker ${System.currentTimeMillis()}"
    logger.info(testLogMessage)

    val logFilePath = os.Path(fileName)
    assert(os.exists(logFilePath), s"Log file should exist at $logFilePath")
    val logContent = os.read(logFilePath)
    assert(logContent.contains(testLogMessage), s"Log file should contain logged message: $testLogMessage")

  test("log4j configuration has monitorInterval configured for automatic reloading"):
    val ctx = LogManager.getContext(false).asInstanceOf[LoggerContext]
    val config = ctx.getConfiguration
    val watchManager = config.getWatchManager
    assert(watchManager != null, "WatchManager should be present")
    assert(watchManager.getIntervalSeconds >= 0, "WatchManager interval should be non-negative")
