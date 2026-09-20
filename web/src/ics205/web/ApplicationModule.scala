package ics205.web

import com.google.inject.AbstractModule
import ics205.Ics205Store
import net.codingwell.scalaguice.ScalaModule

class ApplicationModule extends AbstractModule with ScalaModule:
  override def configure(): Unit =
    bind[Ics205Store]
    bind[WebApplication]
