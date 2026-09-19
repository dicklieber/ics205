# ICS-205

Scala 3 thin-client application for creating ICS-205 incident radio communications
plans and exporting radio-programming data.

## Modules

- `core` — domain model and amateur band-plan logic
- `exporters` — radio-programmer exporters
- `web` — Cask/Scalatags thin-client UI

## Build

    mill core.test
    mill exporters.test
    mill web.compile

## Run

    mill web.run
