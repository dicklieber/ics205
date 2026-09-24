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

The index renders an editable HTML form using `Ics205Page.render(plan: Ics205)`
and Scalatags, following the first page of the reference PDF. Use **Add channel**,
**Delete**, and the up/down buttons to manage row order, then **Save plan** to
persist all edits. **Copy** captures a row's current values; **Paste channel**
appends a new row with its own ID. You can paste repeatedly while the page stays
open, then use the arrows to position the copies. Validation errors keep the submitted values available to fix.
**Print preview** opens the current edits without saving, using
`Ics205Page.renderPrintable(plan)`. Print that preview in landscape on US Letter
paper; plans continue in groups of eight channels.
RX frequency and signed offset are in MHz, with bandwidth in a separate column.
Each channel selects one of the standard 50 CTCSS frequencies in Hz and a mode: None (off), Tone
(transmit tone), or TSQL (transmit tone and receive tone squelch). Remarks are plain text. The preparer's
callsign appears beside their name. Signature remains blank for signing.

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

## Authentication and Authorization

### User and Session Storage

User and session databases are stored in the application data directory (resolved by `FileHelper`, e.g., `~/Library/Application Support/ICS-205` on macOS, `%LOCALAPPDATA%\ICS-205` on Windows, `~/.ics205` on Linux):

- **Users file**: `users.json` (configurable via `AUTH_USERS_FILE` or `AuthConfig.userFileName`)
  ```json
  {
    "users": [
      {
        "id": "c1f7a0b3-1234-4a56-b789-0123456789ab",
        "username": "admin",
        "passwordHash": "$argon2id$v=19$m=65536,t=3,p=4$...",
        "roles": ["admin"],
        "enabled": true
      }
    ]
  }
  ```
  Passwords are never stored in plaintext and are hashed using ScalaPass (Argon2id). Lookups by username are case-insensitive.

- **Sessions file**: `sessions.json` (configurable via `AUTH_SESSIONS_FILE` or `AuthConfig.sessionFileName`)
  ```json
  {
    "sessions": [
      {
        "id": "e4d909c290d0fb1ca068ffaddf22cbd0ffd60ea60f4e46da8a670fbc51a24859",
        "userId": "c1f7a0b3-1234-4a56-b789-0123456789ab",
        "createdAt": "2026-09-24T12:00:00Z",
        "expiresAt": "2026-09-25T12:00:00Z"
      }
    ]
  }
  ```
  Sessions are kept in-memory for low-latency lookups and synchronized with atomic file writes to survive restarts.

### Session Expiration

Session lifetime is configurable via `AUTH_SESSION_LIFETIME_SECONDS` (defaults to 24 hours). Expired sessions are cleaned opportunistically on access and filtered on application startup.

### Cookie Behavior

Session tokens are transmitted using an HTTP-only cookie named `session` (configurable via `AUTH_COOKIE_NAME`):
- `HttpOnly`: true (protects against XSS)
- `SameSite`: Lax (CSRF defense)
- `Path`: `/`
- `Max-Age`: session duration (or 0 when logging out)
- `Secure`: defaults to `false` in development and should be set to `true` in production (controlled via `AUTH_COOKIE_SECURE=true` or `AuthConfig.secureCookie`).

### Role Resolution for Existing Sessions

Sessions store **only** the `userId` and timestamp metadata; roles and permissions are **never stored in the session**. On every authenticated request, `AuthenticationService` resolves the user's current roles and enabled status directly from `UserStore`. Modifying a user's roles or setting `enabled = false` takes effect immediately for all active sessions without requiring re-login or session invalidation.

### Creating the Initial User

Run the built-in user administration tool:

```bash
mill web.run -- --create-user
```

Or run the CLI directly:
```bash
# Prompts interactively for Username, Roles, and Password (without echoing password)
mill core.runMain ics205.auth.UserAdminCli
```

### Protecting Tapir Endpoints

Inject `AuthSecurity` into your endpoint class.

1. **Authentication only**:
   ```scala
   class ProtectedEndpoints @Inject()(security: AuthSecurity) extends ApiEndpoints:
     private val myEndpoint = security.secureEndpoint
       .get
       .in("my-resource")
       .out(stringBody)
       .serverLogicSuccess(user => _ => IO.pure(s"Hello, ${user.username}"))

     override val endpoints = List(myEndpoint)
   ```

2. **Requiring a permission**:
   ```scala
   class AdminEndpoints @Inject()(security: AuthSecurity) extends ApiEndpoints:
     private val configEndpoint = security.authorizedEndpoint(Permission.ConfigureSystem)
       .post
       .in("config")
       .in(jsonBody[ConfigUpdate])
       .out(stringBody)
       .serverLogicSuccess(user => update => IO.pure("Updated"))

     override val endpoints = List(configEndpoint)
   ```
