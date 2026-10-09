package ics205.web

import ics205.auth.{AuthenticatedUser, Role}
import ics205.model.{Ics205, Ics205Event, OperationalPeriod}

class EventsPageTests extends munit.FunSuite:

  val adminUser = AuthenticatedUser("admin", Role.Admin)

  test("EventsPage displays file modification date and time in UTC"):
    val event = Ics205Event("Field Day", Ics205(incidentName = "Incident", operationalPeriod = OperationalPeriod(), channels = Seq.empty))
    val modified = java.time.Instant.parse("2026-10-06T15:30:45Z")
    val html = EventsPage.render(adminUser, Seq(event), fileModifiedAt = Map(event.id -> modified))
    assert(html.contains("<th>File Modified</th>"))
    assert(html.contains("<time datetime=\"2026-10-06T15:30:45Z\">2026-10-06 15:30:45 UTC</time>"))
    assert(EventsPage.render(adminUser, Seq.empty).contains("colspan=\"7\""))

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
    val event = Ics205Event("Field Day", Ics205(incidentName = "Field Day", operationalPeriod = OperationalPeriod(), channels = Seq.empty))
    val html = EventsPage.render(adminUser, Seq(event, event.copy(id = "Field Day (1)", ics205 = event.ics205.copy(incidentName = "Field Day (1)"))))
    assert(!html.contains("<th>Operational Period</th>"))
    assert(html.contains("href=\"/?event=Field+Day\"><strong>Field Day</strong></a>"))
    assert(html.contains("class=\"event-action-select\""))
    assert(html.contains("data-duplicate-name=\"Field Day (2)\""))
    assert(html.contains("action=\"/events/duplicate\""))

  test("EventsPage links to event editor using event ID"):
    val event = Ics205Event("ev-unique-id-777", Ics205(incidentName = "Grand Prix", operationalPeriod = OperationalPeriod(), channels = Seq.empty))
    val html = EventsPage.render(adminUser, Seq(event))
    assert(html.contains("href=\"/?event=ev-unique-id-777\"><strong>Grand Prix</strong></a>"))

  test("EventsPage and EventMetadataPage render editable combo-box with datalist for group selection"):
    val event = Ics205Event("Field Day", Ics205(incidentName = "Field Day", operationalPeriod = OperationalPeriod(), channels = Seq.empty), group = "Ares Ops")
    val knownGroups = Set("Default", "Ares Ops", "Hospital Network")

    val eventsHtml = EventsPage.render(adminUser, Seq(event), knownGroups = knownGroups)
    assert(eventsHtml.contains("list=\"group-list\""), "EventsPage must use input list attribute")
    assert(eventsHtml.contains("name=\"group\""), "EventsPage must use group input name")
    assert(eventsHtml.contains("<datalist id=\"group-list\">"), "EventsPage must render datalist with id group-list")
    assert(eventsHtml.contains("<option value=\"Ares Ops\">"), "EventsPage must render datalist option for Ares Ops")
    assert(eventsHtml.contains("<option value=\"Hospital Network\">"), "EventsPage must render datalist option for Hospital Network")
    assert(!eventsHtml.contains("name=\"newGroupName\""), "EventsPage must not render obsolete newGroupName input")

    val metadataHtml = EventMetadataPage.render(adminUser, event, knownGroups = knownGroups)
    assert(metadataHtml.contains("list=\"group-list\""), "EventMetadataPage must use input list attribute")
    assert(metadataHtml.contains("name=\"group\""), "EventMetadataPage must use group input name")
    assert(metadataHtml.contains("value=\"Ares Ops\""), "EventMetadataPage must populate current group value")
    assert(metadataHtml.contains("<datalist id=\"group-list\">"), "EventMetadataPage must render datalist with id group-list")
    assert(metadataHtml.contains("<option value=\"Hospital Network\">"), "EventMetadataPage must render datalist option for Hospital Network")
    assert(!metadataHtml.contains("name=\"newGroupName\""), "EventMetadataPage must not render obsolete newGroupName input")
