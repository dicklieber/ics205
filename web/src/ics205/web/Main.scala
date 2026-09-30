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

import cats.effect.{ExitCode, IO, IOApp}
import com.comcast.ip4s.*
import com.google.inject.Guice
import com.typesafe.scalalogging.LazyLogging
import ics205.BuildInfo
import ics205.auth.UserAdminCli
import ics205.metrics.ApplicationMetrics
import jakarta.inject.Inject
import org.http4s.ember.server.EmberServerBuilder
import sttp.tapir.server.http4s.Http4sServerInterpreter
import sttp.tapir.swagger.bundle.SwaggerInterpreter

import scala.jdk.CollectionConverters.*

object Main extends IOApp:
  override def run(args: List[String]): IO[ExitCode] =
    if args.contains("--create-user") || args.contains("-u") then
      IO.blocking(UserAdminCli.main(args.toArray)).as(ExitCode.Success)
    else
      IO(Guice.createInjector(new ApplicationModule))
        .flatMap(injector => IO(injector.getInstance(classOf[WebApplication])))
        .flatMap(_.run)
        .as(ExitCode.Success)

class WebApplication @Inject() (endpointsSet: java.util.Set[ApiEndpoints]) extends LazyLogging:

  val httpApp: org.http4s.HttpApp[IO] =
    val configuredEndpoints = endpointsSet.asScala.toList
      .sortBy(_.getClass.getName)
      .flatMap { group =>
        logger.debug(s"Adding endpoints from ${group.getClass.getName}")
        group.endpoints
      }
    val docsEndpoints = SwaggerInterpreter().fromServerEndpoints[IO](
      configuredEndpoints,
      title = BuildInfo.appName,
      version = BuildInfo.version
    )
    val allEndpoints = configuredEndpoints ++ docsEndpoints
    HttpTransactionMetrics(
      Http4sServerInterpreter[IO]().toRoutes(allEndpoints).orNotFound,
      ApplicationMetrics.default
    )

  def run: IO[Unit] =
    EmberServerBuilder.default[IO]
bib      .withHost(ipv4"0.0.0.0")
      .withPort(port"8080")
      .withHttpApp(httpApp)
      .build
      .use(server =>
        IO(logger.info(
          s"${BuildInfo.name} ${BuildInfo.version} listening at ${server.baseUri} " +
            s"(Scala ${BuildInfo.scalaVersion}, Mill ${BuildInfo.millVersion})"
        )) *> IO.never
      )
