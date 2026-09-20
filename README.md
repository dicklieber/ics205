# ICS-205

Scala 3 thin-client application for creating ICS-205 incident radio communications
plans and exporting radio-programming data.

## Modules

- `core` — domain model and amateur band-plan logic
- `exporters` — radio-programmer exporters
- `web` — Tapir/http4s server with a Scalatags thin-client UI

## Build

    mill core.test
    mill exporters.test
    mill web.compile
    mill web.test

The core module generates `ics205.BuildInfo` with `name`, `appName`, `productName`, `version`,
`scalaVersion`, and `millVersion` fields. The application version is read from
`version.txt`; changes to that file automatically refresh the build info.
These build details are logged when the server starts.

## Run

    mill web.run

Open http://localhost:8080.

`Main` creates a Guice injector using `ApplicationModule` and starts the injected
`WebApplication`. Add application bindings in `ApplicationModule` and use
`jakarta.inject.Inject` on constructors. `Ics205Store` is annotated with
`jakarta.inject.Singleton`, so consumers in the same injector share one store.

Group related Tapir endpoints in a class implementing `ics205.web.ApiEndpoints`.
`ApplicationModule` discovers implementations under `ics205.web` (including
subpackages) and binds them as singletons. Use an `@Inject` constructor for
dependencies, or a no-argument constructor. `WebApplication` collects the injected
groups and serves their endpoints automatically; no individual route registration
is needed. Add other package roots to `packagesOnly` in `ApplicationModule` if needed.

`IndexEndpoints` serves `/`. `MetricsEndpoints` serves `/metrics` in Prometheus
text format using Dropwizard's shared `default` registry and the `ics205_` prefix.
Register application or JVM metrics in that registry to expose them.
`core` owns the Dropwizard dependencies, registry access, timers, and Prometheus
rendering in `ics205.metrics`. The web module handles HTTP timing boundaries and
serves the rendered metrics using `ApplicationMetrics.default`.
The `http.transactions` timer measures request handling through response-body
completion, including failed or canceled requests and unmatched routes. It is
exported under `ics205_http_transactions` with durations in seconds.
