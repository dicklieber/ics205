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

import com.google.inject.AbstractModule
import com.google.inject.name.Names
import com.typesafe.config.{Config, ConfigFactory}
import com.typesafe.scalalogging.LazyLogging
import ics205.BuildInfo
import ics205.auth.{AuthConfig, AuthenticationService, PasswordService, ScalaPassPasswordService}
import ics205.exporter.{RadioExportDefinitions, RadioExporter}
import ics205.store.{Ics205Store, InMemJsonSessionStore, SessionStore, UserStore}
import ics205.util.{FileHelper, LoggingConfig}
import ics205.web.auth.AuthSecurity
import net.codingwell.scalaguice.ScalaModule

import java.time.Duration
import scala.jdk.CollectionConverters.*

class ApplicationModule(
  fullConfig: Config = ApplicationModule.loadConfig(),
  customFileHelper: Option[FileHelper] = None
) extends AbstractModule with ScalaModule with LazyLogging:
  def this(fullConfig: Config) = this(fullConfig, None)
  def this(customFileHelper: FileHelper) = this(ApplicationModule.loadConfig(), Some(customFileHelper))

  override def configure(): Unit =
    val fileHelper = customFileHelper.getOrElse(new FileHelper())
    bind[FileHelper].toInstance(fileHelper)
    LoggingConfig.init(fileHelper.directory)

    val authConfig = AuthConfig.fromConfig(fullConfig)
    bind[AuthConfig].toInstance(authConfig)
    bind[PasswordService].to[ScalaPassPasswordService].asEagerSingleton()
    bind[UserStore].asEagerSingleton()
    bind[SessionStore].to[InMemJsonSessionStore].asEagerSingleton()
    bind[AuthenticationService].asEagerSingleton()
    bind[AuthSecurity].asEagerSingleton()
    bind[RadioExportDefinitions].asEagerSingleton()
    bind[RadioExporter].asEagerSingleton()

    AutoBind.bindAllImplementationsOf[ApiEndpoints](
      binder = binder(),
      packagesOnly = Seq("ics205.web"),
      asSingleton = true
    )
    bind[WebApplication]

    val entries = fullConfig.entrySet().asScala.toSeq
    for (entry <- entries) {
      val key = entry.getKey
      val value = fullConfig.getAnyRef(key)
      logger.trace(s"Config entry: $key = $value type: ${value.getClass}")
      scala.util.Try(
        fullConfig.getDuration(
          key
        )
      ).toOption.foreach(duration =>
        bind[Duration]
          .annotatedWith(Names.named(key))
          .toInstance(duration)
      )
      // Determine type and bind accordingly
      value match {
        case s: String =>
          bind[String]
            .annotatedWith(Names.named(key))
            .toInstance(s)
          s.toIntOption.foreach(i =>
            bind[Int]
              .annotatedWith(Names.named(key))
              .toInstance(i)
          )
          s.toLongOption.filterNot(_ => s.toIntOption.isDefined).foreach(l =>
            bind[Long]
              .annotatedWith(Names.named(key))
              .toInstance(l)
          )
          s.toDoubleOption.filterNot(_ => s.toLongOption.isDefined).foreach(d =>
            bind[Double]
              .annotatedWith(Names.named(key))
              .toInstance(d)
          )
          s.toBooleanOption.foreach(b =>
            bind[Boolean]
              .annotatedWith(Names.named(key))
              .toInstance(b)
          )

        case i: Integer =>
          bind[Int]
            .annotatedWith(Names.named(key))
            .toInstance(i.intValue)

        case l: java.lang.Long =>
          bind[Long]
            .annotatedWith(Names.named(key))
            .toInstance(l)

        case d: java.lang.Double =>
          bind[Double]
            .annotatedWith(Names.named(key))
            .toInstance(d)

        case b: java.lang.Boolean =>
          bind[Boolean]
            .annotatedWith(Names.named(key))
            .toInstance(b)

        case _ =>
        // Optionally log or ignore unsupported types
      }
    }

    if (!fullConfig.hasPath("port")) {
      bind[Int].annotatedWith(Names.named("port")).toInstance(8080)
    }

    bind[Config].toInstance(fullConfig)

object ApplicationModule:
  def loadConfig(): Config =
    val configFile = sys.props.get("config.file").map(os.Path(_))
      .orElse(sys.env.get("ICS205_CONFIG").map(os.Path(_)))
      .orElse(sys.env.get("CONFIG_FILE").map(os.Path(_)))
      .orElse(Option(os.pwd / "config" / "ics205.conf").filter(os.exists))
      .orElse(Option(os.pwd / "config" / "application.conf").filter(os.exists))
      .orElse(Option(os.Path(s"/home/${BuildInfo.productName}/config/ics205.conf")).filter(os.exists))
      .orElse(Option(os.Path(s"/home/${BuildInfo.productName}/config/application.conf")).filter(os.exists))
      .getOrElse(os.pwd / "config" / "ics205.conf")

    ConfigFactory.parseFile(configFile.toIO)
      .withFallback(ConfigFactory.load())
      .resolve()

  given Conversion[String, os.Path] = (in: String) => os.Path(in)
