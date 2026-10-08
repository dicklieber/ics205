package ics205.web.util

class RequestUtilsTests extends munit.FunSuite:
  test("localRedirect keeps same-site paths"):
    assertEquals(RequestUtils.localRedirect("/"), Some("/"))
    assertEquals(RequestUtils.localRedirect("/events?msg=ok"), Some("/events?msg=ok"))

  test("localRedirect rejects targets a browser would follow off-site"):
    Seq("https://evil.example", "//evil.example", "/\\evil.example", "evil.example", "", "/ok\r\nSet-Cookie: x=1")
      .foreach(target => assertEquals(RequestUtils.localRedirect(target), None, target))

  test("refererPath keeps only the path and query"):
    assertEquals(RequestUtils.refererPath("http://localhost:8080/events?x=1"), Some("/events?x=1"))
    assertEquals(RequestUtils.refererPath("https://evil.example/phish"), Some("/phish"))
    assertEquals(RequestUtils.refererPath("not a url"), None)
