package ics205.web

import ics205.auth.{AuthenticatedUser, RolePermissions}
import ics205.model.{Ics205, Ics205Event, OperationalPeriod}

class EventsPageTests extends munit.FunSuite:

  val adminUser = AuthenticatedUser(
    username = "admin",
    role = RolePermissions.Admin,
    id = "u-admin"
  )

  test("EventsPage does not use inline onsubmit for delete confirmation and uses data-confirm"):
    val maliciousEventName = "Test', (alert(document.domain), true) || '"
    val event = Ics205Event(
      eventName = maliciousEventName,
      ics205 = Ics205(incidentName = "Test Incident", operationalPeriod = OperationalPeriod(), channels = Seq.empty)
    )

    val html = EventsPage.render(
      currentUser = adminUser,
      events = Seq(event),
      currentEventName = Some(maliciousEventName)
    )

    assert(!html.contains("onsubmit="), "Should not contain inline onsubmit handler")
    assert(html.contains("data-confirm="), "Should use data-confirm attribute")
    assert(html.contains("document.addEventListener('submit'"), "Should include unobtrusive submit listener")
    assert(!html.contains(s"confirm('Are you sure you want to delete event \\'$maliciousEventName\\'?'"), "Should not interpolate into confirm string literal")
