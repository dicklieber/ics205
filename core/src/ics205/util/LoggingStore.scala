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

import com.typesafe.scalalogging.LazyLogging
import io.circe.Codec
import jakarta.inject.{Inject, Singleton}
import org.apache.logging.log4j.{Level, LogManager}
import org.apache.logging.log4j.core.LoggerContext
import org.apache.logging.log4j.core.config.Configurator

import scala.util.control.NonFatal

case class LoggingConfigState(
  loggers: Map[String, String] = Map.empty
) derives Codec.AsObject

object LoggingStore:
  val DefaultFileName: String = "loggers.json"
  val AvailableLevels: Seq[String] = Seq("TRACE", "DEBUG", "INFO", "WARN", "ERROR", "FATAL", "OFF")

@Singleton
class LoggingStore @Inject()(val fileHelper: FileHelper = new FileHelper()) extends LazyLogging:

  def getPersistedConfig(): LoggingConfigState =
    fileHelper.loadOrDefault[LoggingConfigState](Locus.config, LoggingStore.DefaultFileName)(LoggingConfigState())

  def getAllConfigured(): Map[String, String] =
    getPersistedConfig().loggers

  def getLevel(loggerName: String): Option[String] =
    getAllConfigured().get(loggerName.trim)

  def setLevel(loggerName: String, level: String): Unit = synchronized {
    val trimmedName = loggerName.trim
    require(trimmedName.nonEmpty, "Logger name cannot be empty")
    val upperLevel = level.trim.toUpperCase
    val validatedLevel = Level.valueOf(upperLevel)

    // Apply dynamically to Log4j2
    Configurator.setLevel(trimmedName, validatedLevel)

    // Persist to Locus.config
    val current = getPersistedConfig()
    val updated = current.copy(loggers = current.loggers + (trimmedName -> upperLevel))
    fileHelper.save(Locus.config, LoggingStore.DefaultFileName, updated)
    logger.info(s"Persisted log level '$upperLevel' for logger '$trimmedName' in Locus.config")
  }

  def resetLevel(loggerName: String): Unit = synchronized {
    val trimmedName = loggerName.trim
    val current = getPersistedConfig()
    val updated = current.copy(loggers = current.loggers - trimmedName)
    fileHelper.save(Locus.config, LoggingStore.DefaultFileName, updated)

    // Reset in Log4j2
    Configurator.setLevel(trimmedName, null.asInstanceOf[Level])
    logger.info(s"Reset and removed persisted log level for logger '$trimmedName'")
  }

  def getEffectiveLevel(loggerName: String): String =
    try
      val ctx = LogManager.getContext(false).asInstanceOf[LoggerContext]
      val config = ctx.getConfiguration
      val loggerConfig = config.getLoggerConfig(loggerName.trim)
      if loggerConfig != null && loggerConfig.getLevel != null then
        loggerConfig.getLevel.name()
      else
        "INFO"
    catch
      case NonFatal(_) => "INFO"

  def applyPersisted(): Unit = synchronized {
    val config = getPersistedConfig()
    config.loggers.foreach { case (loggerName, levelStr) =>
      try
        val level = Level.valueOf(levelStr.trim.toUpperCase)
        Configurator.setLevel(loggerName, level)
        logger.info(s"Applied persisted log level '$level' for logger '$loggerName'")
      catch
        case e: Exception =>
          logger.warn(s"Invalid log level '$levelStr' for '$loggerName' in persisted config", e)
    }
  }
