package ics205.web

import com.google.inject.Guice
import ics205.Ics205Store

class ApplicationModuleTests extends munit.FunSuite:
  test("web applications receive the injector's singleton store"):
    val injector = Guice.createInjector(new ApplicationModule)
    val store = injector.getInstance(classOf[Ics205Store])
    val first = injector.getInstance(classOf[WebApplication])
    val second = injector.getInstance(classOf[WebApplication])

    assert(first.store eq store)
    assert(second.store eq store)
    assert(injector.getInstance(classOf[Ics205Store]) eq store)

  test("independent injectors have independent stores"):
    val first = Guice.createInjector(new ApplicationModule)
    val second = Guice.createInjector(new ApplicationModule)

    assert(!(first.getInstance(classOf[Ics205Store]) eq
      second.getInstance(classOf[Ics205Store])))
