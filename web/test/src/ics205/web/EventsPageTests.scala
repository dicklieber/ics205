package ics205.web

import ics205.auth.{AuthenticatedUser, Role}
import ics205.model.{Ics205, Ics205Event, OperationalPeriod}

class EventsPageTests extends munit.FunSuite:

  val adminUser = AuthenticatedUser("admin", Role.Admin)

  test("EventsPage does not use inline onsubmit for delete confirmation and uses data-confirm"):
    val maliciousEventName = "Test', (alert(document.domain), true) || '"
    val event = Ics205Event(
      id = maliciousEventName,
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

  test("EventsPage renders export buttons for events and import event form"):
    val event = Ics205Event(
      id = "Field Day",
      ics205 = Ics205(incidentName = "Field Day 2026", operationalPeriod = OperationalPeriod(), channels = Seq.empty)
    )

    val html = EventsPage.render(
      currentUser = adminUser,
      events = Seq(event),
      currentEventName = Some("Field Day")
    )

    assert(html.contains("/events/export?name=Field+Day"), "Should contain export link for Field Day")
    assert(html.contains("Export"), "Should contain Export button text")
    assert(html.contains("Import Event"), "Should contain Import Event section")
    assert(html.contains("action=\"/events/import\""), "Should contain import form action")
    assert(html.contains("enctype=\"multipart/form-data\""), "Should have multipart enctype")
    assert(html.contains("type=\"file\""), "Should contain file input")

  test("Event table links names, removes operational period, and suggests the next duplicate name"):
    val event = Ics205Event("Field Day", Ics205(incidentName = "Incident", operationalPeriod = OperationalPeriod(), channels = Seq.empty))
    val html = EventsPage.render(adminUser, Seq(event, event.copy(id = "Field Day (1)")))
    assert(!html.contains("<th>Operational Period</th>"))
    assert(html.contains("href=\"/?event=Field+Day\"><strong>Field Day</strong></a>"))
    assert(html.contains("class=\"event-action-select\""))
    assert(html.contains("data-duplicate-name=\"Field Day (2)\""))
    assert(html.contains("action=\"/events/duplicate\""))
