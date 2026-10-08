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
import org.apache.logging.log4j.{Level, LogManager}
import org.apache.logging.log4j.core.LoggerContext
import org.apache.logging.log4j.core.config.{Configuration, ConfigurationFactory, ConfigurationSource, Configurator, Order}
import org.apache.logging.log4j.core.config.builder.api.{ComponentBuilder, ConfigurationBuilder, ConfigurationBuilderFactory}
import org.apache.logging.log4j.core.config.builder.impl.{BuiltConfiguration, DefaultComponentBuilder}
import org.apache.logging.log4j.core.config.plugins.Plugin

import java.net.URI

@Plugin(name = "Ics205ConfigurationFactory", category = ConfigurationFactory.CATEGORY)
@Order(50)
class Ics205ConfigurationFactory extends ConfigurationFactory:
  override protected def getSupportedTypes(): Array[String] = Array("*")
  override def getConfiguration(loggerContext: LoggerContext, source: ConfigurationSource): Configuration =
    LoggingConfig.createConfiguration()
  override def getConfiguration(loggerContext: LoggerContext, name: String, configLocation: java.net.URI): Configuration =
    LoggingConfig.createConfiguration()

object LoggingConfig extends LazyLogging:

  ConfigurationFactory.setConfigurationFactory(new Ics205ConfigurationFactory())

  val consolePattern: String =
    "%style{%d{HH:mm:ss.SSS}}{dim} %style{[%t]}{magenta} %highlight{%-5level} %style{%logger{36}}{cyan} - %msg%n"

  val filePattern: String =
    "%d{yyyy-MM-dd HH:mm:ss.SSS} [%t] %-5level %logger{36} - %msg%n"

  val accessPattern: String =
    "%msg%n"

  private val addComponentMethod: java.lang.reflect.Method =
    classOf[ComponentBuilder[?]].getMethod("addComponent", classOf[ComponentBuilder[?]])

  private val addAttributeMethod: java.lang.reflect.Method =
    classOf[ComponentBuilder[?]].getMethod("addAttribute", classOf[String], classOf[String])

  extension (cb: ComponentBuilder[?])
    private def addComp(child: ComponentBuilder[?]): ComponentBuilder[?] =
      addComponentMethod.invoke(cb, child)
      cb

    private def withAttr(key: String, value: String): ComponentBuilder[?] =
      addAttributeMethod.invoke(cb, key, value)
      cb

  /**
   * Builds a Log4j2 programmatic configuration in Scala code.
   *
   * @param logDir the directory where log files (ics205.log, access.log) should be written
   * @return BuiltConfiguration instance ready for Log4j2 initialization or reconfiguration
   */
  def createConfiguration(logDir: os.Path = FileHelper.logDirectory): BuiltConfiguration =
    os.makeDir.all(logDir)
    val builder: ConfigurationBuilder[BuiltConfiguration] = ConfigurationBuilderFactory.newConfigurationBuilder()

    builder.setConfigurationName("ICS205")

    // Console Appender
    val consoleAppender = builder.newAppender("Console", "Console")
      .addAttribute("target", "SYSTEM_OUT")
      .add(
        builder.newLayout("PatternLayout")
          .addAttribute("pattern", consolePattern)
      )
    builder.add(consoleAppender)

    // File Appender (ics205.log)
    val filePolicies: ComponentBuilder[?] = builder.newComponent("Policies")
      .addComp(builder.newComponent("SizeBasedTriggeringPolicy").withAttr("size", "10MB"))
      .addComp(builder.newComponent("TimeBasedTriggeringPolicy").withAttr("interval", "1"))

    val fileAppender = builder.newAppender("File", "RollingFile")
      .addAttribute("fileName", (logDir / "ics205.log").toString)
      .addAttribute("filePattern", (logDir / "ics205-%d{yyyy-MM-dd}-%i.log.gz").toString)
      .add(
        builder.newLayout("PatternLayout")
          .addAttribute("pattern", filePattern)
      )
      .addComponent(filePolicies)
      .addComponent(builder.newComponent("DefaultRolloverStrategy").withAttr("max", "10"))
    builder.add(fileAppender)

    // AccessLog Appender (access.log)
    val accessPolicies: ComponentBuilder[?] = builder.newComponent("Policies")
      .addComp(builder.newComponent("SizeBasedTriggeringPolicy").withAttr("size", "10MB"))
      .addComp(builder.newComponent("TimeBasedTriggeringPolicy").withAttr("interval", "1"))

    val accessLogAppender = builder.newAppender("AccessLog", "RollingFile")
      .addAttribute("fileName", (logDir / "access.log").toString)
      .addAttribute("filePattern", (logDir / "access-%d{yyyy-MM-dd}-%i.log.gz").toString)
      .add(
        builder.newLayout("PatternLayout")
          .addAttribute("pattern", accessPattern)
      )
      .addComponent(accessPolicies)
      .addComponent(builder.newComponent("DefaultRolloverStrategy").withAttr("max", "10"))
    builder.add(accessLogAppender)

    // Logger: ics205.exporter.RadioExportDefinitions -> Level.DEBUG
    val radioExportLogger = builder.newLogger("ics205.exporter.RadioExportDefinitions", Level.DEBUG)
    builder.add(radioExportLogger)

    // Logger: ics205.web.HttpAccessLog -> Level.INFO, additivity = false, appender = AccessLog
    val httpAccessLogger = builder.newLogger("ics205.web.HttpAccessLog", Level.INFO)
      .addAttribute("additivity", false)
      .add(builder.newAppenderRef("AccessLog"))
    builder.add(httpAccessLogger)

    // Root Logger -> Level.INFO, appenders = Console, File
    val rootLogger = builder.newRootLogger(Level.INFO)
      .add(builder.newAppenderRef("Console"))
      .add(builder.newAppenderRef("File"))
    builder.add(rootLogger)

    builder.build()

  /**
   * Renders the current Log4j2 configuration as YAML.
   *
   * @param configuredLoggers optional map of custom logger names to configured log levels
   * @return YAML string representing the Log4j2 configuration
   */
  def toYaml(configuredLoggers: Map[String, String] = Map.empty): String =
    val staticLoggers = Seq(
      ("ics205.exporter.RadioExportDefinitions", "DEBUG", true, Seq.empty[String]),
      ("ics205.web.HttpAccessLog", "INFO", false, Seq("AccessLog"))
    )
    val dynamicLoggers = configuredLoggers.toSeq.sortBy(_._1).filterNot(l => staticLoggers.exists(_._1 == l._1)).map {
      case (name, level) => (name, level, true, Seq.empty[String])
    }
    val allLoggers = staticLoggers ++ dynamicLoggers

    val loggersYaml = allLoggers.map { case (name, level, additivity, appenderRefs) =>
      val sb = new StringBuilder()
      sb.append(s"      - name: $name\n")
      sb.append(s"        level: $level")
      if !additivity then
        sb.append(s"\n        additivity: false")
      if appenderRefs.nonEmpty then
        sb.append(s"\n        AppenderRef:")
        appenderRefs.foreach { ref =>
          sb.append(s"\n          - ref: $ref")
        }
      sb.toString()
    }.mkString("\n\n")

    s"""Configuration:
       |  name: ICS205
       |  status: WARN
       |
       |  Appenders:
       |    Console:
       |      name: Console
       |      target: SYSTEM_OUT
       |      PatternLayout:
       |        pattern: "$consolePattern"
       |
       |    RollingFile:
       |      - name: File
       |        fileName: "$${fileHelper:logDir}/ics205.log"
       |        filePattern: "$${fileHelper:logDir}/ics205-%d{yyyy-MM-dd}-%i.log.gz"
       |        PatternLayout:
       |          pattern: "$filePattern"
       |        Policies:
       |          SizeBasedTriggeringPolicy:
       |            size: "10MB"
       |          TimeBasedTriggeringPolicy:
       |            interval: 1
       |        DefaultRolloverStrategy:
       |          max: 10
       |
       |      - name: AccessLog
       |        fileName: "$${fileHelper:logDir}/access.log"
       |        filePattern: "$${fileHelper:logDir}/access-%d{yyyy-MM-dd}-%i.log.gz"
       |        PatternLayout:
       |          pattern: "$accessPattern"
       |        Policies:
       |          SizeBasedTriggeringPolicy:
       |            size: "10MB"
       |          TimeBasedTriggeringPolicy:
       |            interval: 1
       |        DefaultRolloverStrategy:
       |          max: 10
       |
       |  Loggers:
       |    Root:
       |      level: INFO
       |      AppenderRef:
       |        - ref: Console
       |        - ref: File
       |
       |    Logger:
       |$loggersYaml
       |""".stripMargin

  /**
   * Initializes or reconfigures Log4j2 logging.
   * If an explicit configuration file is provided via system property or environment variable,
   * that configuration file is used. Otherwise, the programmatic Scala configuration is applied.
   *
   * @param logDir the directory where log files should be written (defaults to FileHelper.logDirectory)
   * @param fileHelper the FileHelper used to load persisted logger configuration
   * @return the active LoggerContext
   */
  def init(logDir: os.Path = FileHelper.logDirectory, fileHelper: FileHelper = new FileHelper()): LoggerContext =
    val explicitProp = sys.props.get("log4j.configurationFile").orElse(sys.env.get("LOG4J_CONFIGURATION_FILE"))
    explicitProp match
      case Some(customConfig) =>
        logger.info(s"Reconfiguring logging from explicit configuration: $customConfig")
        Configurator.reconfigure(URI.create(customConfig))
      case None =>
        val config = createConfiguration(logDir)
        Configurator.reconfigure(config)
        logger.info(s"Initialized programmatic Scala logging configuration in directory $logDir")

    new LoggingStore(fileHelper).applyPersisted()

    LogManager.getContext(false).asInstanceOf[LoggerContext]
