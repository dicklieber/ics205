# ICS-205

Scala 3 thin-client application for creating ICS-205 incident radio communications
plans and exporting radio-programming data.

## Modules

- `core` — domain model and amateur band-plan logic
- `exporters` — radio-programmer and ICS 205 PDF exporters
- `web` — Tapir/http4s server with a Scalatags thin-client UI

## Prerequisites

Before setting up and running the project, ensure you have the following installed:

- **Java Development Kit (JDK)**: JDK 17 or higher (JDK 17, 21, or newer LTS recommended). Ensure `JAVA_HOME` is set and `java` is available in your `PATH`.
- **Git**: For cloning the repository and managing source control.
- **Mill Build Tool**: The project includes the `./mill` wrapper script at the repository root, which automatically downloads and uses the required Mill version (configured in `.mill-version`). You do not need to install Mill globally.
- **Scala 3**: All Scala 3 dependencies and the Scala 3.7.4 compiler are managed automatically by Mill — no separate Scala installation is necessary.

## Build

```bash
./mill core.compile
./mill exporters.compile
./mill web.compile
```

The core module generates `ics205.BuildInfo` with `name`, `appName`, `productName`, `version`,
`scalaVersion`, and `millVersion` fields. The application version is read from
`version.txt`; changes to that file automatically refresh the build info.
These build details are logged when the server starts.

## Testing and Code Coverage

### Run Tests

Run all unit tests across all modules:

```bash
./mill __.test
```

Or run tests for an individual module:

```bash
./mill core.test
./mill exporters.test
./mill web.test
```

### Clean, Test, and View Coverage Report

To perform a clean build, execute all tests with Scoverage instrumentation, generate HTML coverage reports, and view them in your default browser:

```bash
./mill clean && ./mill __.test && ./mill __.scoverage.htmlReport && open out/*/scoverage/htmlReport.dest/index.html
```

### Coverage Reports (Scoverage)

Code coverage is instrumented using Scoverage (`mill-contrib-scoverage`).

- **HTML Coverage Report** (interactive line-by-line source view):
  ```bash
  ./mill __.scoverage.htmlReport
  ```
  Reports are generated at:
  - `out/core/scoverage/htmlReport.dest/index.html`
  - `out/exporters/scoverage/htmlReport.dest/index.html`
  - `out/web/scoverage/htmlReport.dest/index.html`

  To open all generated HTML coverage reports in your browser:
  ```bash
  open out/*/scoverage/htmlReport.dest/index.html
  ```

- **Console Coverage Summary** (statement and branch coverage in terminal):
  ```bash
  ./mill __.scoverage.consoleReport
  ```

- **XML Coverage Report** (Cobertura-compatible XML for CI/tools):
  ```bash
  ./mill __.scoverage.xmlReport
  ```
  Reports are generated at `out/*/scoverage/xmlReport.dest/scoverage.xml`.

## Run

```bash
./mill web.run
```

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

**Export PDF** downloads the current saved plan as `ics205.pdf` (save edits first).
The authenticated `/export/pdf` endpoint uses `Ics205PdfExporter.generatePdf(plan)`
and the bundled FEMA ICS 205 v3.1 form. The PDF always has one page. Up to eight channels use landscape US Letter;
additional channels extend the page height with full-height rows, keeping the
instructions and signature below the table. RX and
calculated TX frequencies include N/W bandwidth; CTCSS Tone applies to TX only,
while TSQL applies to both RX and TX. FM/AM are marked A, digital D, and Other
remains Other because the model does not specify mixed mode. Text wraps and
shrinks to fit the form; very long entries can become small. Characters outside
the form's Helvetica/WinAnsi character set are replaced with `?`.

**Radio** opens `/radio`, a read-only radio view of the saved ICS205 plan.
The channel table uses two header rows: simple fields span both rows, while
`frequency` and `ctcss` group their component columns. Frequency values retain
their stored precision and show MHz/Hz units. Operational period and preparer
details are expanded above the table.

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

- **Users file**: `users.json` (configurable via `auth.userFileName` or `AuthConfig.userFileName`)
  ```json
  {
    "users": [
      {
        "id": "c1f7a0b3-1234-4a56-b789-0123456789ab",
        "username": "admin",
        "passwordHash": "$argon2id$v=19$m=65536,t=3,p=4$...",
        "role": "admin",
        "enabled": true
      }
    ]
  }
  ```
  Passwords are never stored in plaintext and are hashed using ScalaPass (Argon2id). Lookups by username are case-insensitive. Each user belongs to a single `Role` (defined by the `RolePermissions` enum).

- **Sessions file**: `sessions.json` (configurable via `auth.sessionFileName` or `AuthConfig.sessionFileName`)
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

Session lifetime is configurable via `auth.sessionLifetime` (defaults to 24 hours). Expired sessions are cleaned opportunistically on access and filtered on application startup.

### Cookie Behavior

Session tokens are transmitted using an HTTP-only cookie named `session` (configurable via `auth.cookieName`):
- `HttpOnly`: true (protects against XSS)
- `SameSite`: Lax (CSRF defense)
- `Path`: `/`
- `Max-Age`: session duration (or 0 when logging out)
- `Secure`: defaults to `false` in development and can be configured for production via `auth.secureCookie` or `AuthConfig.secureCookie`.

### Role Resolution for Existing Sessions

Sessions store **only** the `userId` and timestamp metadata; roles and permissions are **never stored in the session**. On every authenticated request, `AuthenticationService` resolves the user's current role and enabled status directly from `UserStore`. Modifying a user's role or setting `enabled = false` takes effect immediately for all active sessions without requiring re-login or session invalidation.

### Creating the Initial User

An initial user account must be provisioned before accessing protected features. If no users are defined in the database, navigating to the login page (or opening the application) automatically redirects to the user management page (`/admin/users`) with a notice prompting you to create the initial admin user directly through the web UI. Once the initial admin user is created, subsequent user administration is managed by authorized administrators via the web UI at `/admin/users`.

### User Login and Logout UI

- **Login Page (`GET /login`)**: HTML login form for authenticating in the browser. Supports optional `?redirect=/path` parameter to return the user to their requested destination after authentication, as well as `?err=...` and `?msg=...` notices.
- **Form Login (`POST /login`)**: Accepts `username`, `password`, and optional `redirect` via URL-encoded form body. On success, sets the session cookie and redirects via HTTP 303.
- **JSON Login (`POST /login`)**: Accepts JSON body `{"username": "...", "password": "..."}` for API clients.
- **Logout (`GET /logout` and `POST /logout`)**: Invalidates the active session in `SessionStore`, clears the browser cookie, and redirects to `/login?msg=Logged+out+successfully.` (or returns JSON response for POST API requests).

### User Administration UI

Users with `Permission.EditUsers` (such as those with the `admin` role) can manage accounts via the web browser at `/admin/users`:

- **View users**: View all accounts, IDs, assigned role, and active/disabled status.
- **Create user**: Add new accounts with username, password, role select dropdown, and enabled status. Passwords are automatically hashed via ScalaPass (Argon2id).
- **Edit user**: Change usernames, select role from dropdown, enable/disable accounts, and optionally reset passwords.
- **Delete user**: Remove user accounts with confirmation.

The page is guarded by `AuthSecurity.authorizedEndpoint(Permission.EditUsers)` returning `401 Unauthorized` for unauthenticated requests and `403 Forbidden` for users lacking the permission.

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
     private val configEndpoint = security.authorizedEndpoint(Permission.Debug)
       .post
       .in("config")
       .in(jsonBody[ConfigUpdate])
       .out(stringBody)
       .serverLogicSuccess(user => update => IO.pure("Updated"))

     override val endpoints = List(configEndpoint)
   ```

## Deployment

### 1. Build Fat JAR (Assembly)

To build a standalone executable fat JAR containing all compiled classes, dependencies, and web assets:

```bash
./mill web.assembly
```

The resulting fat JAR is output to:
```text
out/web/assembly.dest/out.jar
```

### 2. Automated Remote Deployment via SSH

Automated deployment scripts are provided in `deploy/` for deploying from a development host to a remote Linux server via SSH.

#### Directory Layout

The application deploys into the dedicated `ics205` system account's home directory (`/home/ics205/`):
```text
/home/ics205/
├── app/
│   └── ics205.jar          # Application JAR (owned by root:ics205, mode 640)
├── config/
│   ├── ics205.conf         # Optional Typesafe Config file (owned by root:ics205, mode 640)
│   └── ics205.env          # Optional environment variables file (owned by root:ics205, mode 640)
├── data/
│   ├── ics205.json         # Persistent application data (owned by ics205:ics205, mode 750)
│   ├── users.json
│   └── sessions.json
└── install/
    └── ics205.service      # Source copy of systemd unit (owned by root:ics205, mode 640)
```

#### Initial Installation on Remote Server (`deploy/install.sh`)
Installs the fat JAR, creates the dedicated `ics205` system user/group with home directory `/home/ics205`, creates the directory layout with strict permissions, migrates legacy data/config from `/var/lib/ics205` or `/etc/ics205` if present, configures the systemd service at `/etc/systemd/system/ics205.service`, and starts it:

```bash
# Build (if not already built) and install to remote Linux host
./deploy/install.sh --build user@remote-server

# Or with custom SSH port / key
./deploy/install.sh -p 2222 -i ~/.ssh/id_ed25519 user@remote-server
```

#### Updating JAR and Restarting Service (`deploy/update.sh`)
Transfers the newly compiled fat JAR from `out/web/assembly.dest/out.jar` to `/home/ics205/app/ics205.jar`, preserves existing data in `/home/ics205/data`, sets correct permissions, and restarts the `ics205.service` systemd unit:

```bash
# Rebuild fat JAR and deploy update to remote Linux host
./deploy/update.sh --build user@remote-server

# Deploy an existing build and also sync systemd service unit changes
./deploy/update.sh --sync-service user@remote-server
```

#### Uninstalling Application (`deploy/uninstall.sh`)
Stops and disables `ics205.service`, removes the active systemd unit from `/etc/systemd/system/ics205.service`, and deletes the application binaries. Persistent data in `/home/ics205/data` and configuration in `/home/ics205/config` are safely preserved:

```bash
# Standard uninstall (preserves /home/ics205/data and configuration)
./deploy/uninstall.sh user@remote-server

# Complete purge (removes all data, configuration, and the system account)
./deploy/uninstall.sh --purge user@remote-server
```

### 3. Manual Linux Server Setup

#### System Prerequisites
- Java Runtime Environment (JRE/JDK 17 or 21+ LTS):
  ```bash
  # Debian / Ubuntu
  sudo apt update && sudo apt install -y openjdk-21-jre-headless
  ```

#### Create Dedicated System User and Directories
Create a dedicated system user and group without login shell privileges, along with the home directory layout:

```bash
sudo groupadd --system ics205
sudo useradd --system --home-dir /home/ics205 --create-home --gid ics205 --shell /usr/sbin/nologin ics205
sudo mkdir -p /home/ics205/app /home/ics205/config /home/ics205/data /home/ics205/install
sudo chown root:ics205 /home/ics205 /home/ics205/app /home/ics205/config /home/ics205/install
sudo chmod 750 /home/ics205 /home/ics205/app /home/ics205/config /home/ics205/install
sudo chown ics205:ics205 /home/ics205/data
sudo chmod 750 /home/ics205/data

sudo cp out/web/assembly.dest/out.jar /home/ics205/app/ics205.jar
sudo chown root:ics205 /home/ics205/app/ics205.jar
sudo chmod 640 /home/ics205/app/ics205.jar
```

### 4. Systemd Service Configuration

A production-ready systemd service unit file is provided at `deploy/ics205.service`.

1. Copy the service unit to `/home/ics205/install/ics205.service` and `/etc/systemd/system/`:
   ```bash
   sudo cp deploy/ics205.service /home/ics205/install/ics205.service
   sudo chown root:ics205 /home/ics205/install/ics205.service
   sudo chmod 640 /home/ics205/install/ics205.service

   sudo cp deploy/ics205.service /etc/systemd/system/ics205.service
   sudo chown root:root /etc/systemd/system/ics205.service
   sudo chmod 644 /etc/systemd/system/ics205.service
   ```

2. (Optional) Customize environment variables by creating `/home/ics205/config/ics205.env`:
   ```bash
   sudo tee /home/ics205/config/ics205.env > /dev/null << 'EOF'
   JAVA_OPTS=-Xms256m -Xmx512m -XX:+UseG1GC -Dauth.secureCookie=true
   JAR_PATH=/home/ics205/app/ics205.jar
   EOF
   sudo chown root:ics205 /home/ics205/config/ics205.env
   sudo chmod 640 /home/ics205/config/ics205.env
   ```

3. Reload systemd, enable, and start the service:
   ```bash
   sudo systemctl daemon-reload
   sudo systemctl enable --now ics205.service
   ```

4. Create the initial admin user by opening `http://<server-ip>:8080/` (or port 80 if configured) in your web browser.

### 5. Service Management and Monitoring

- **Check status**:
  ```bash
  sudo systemctl status ics205.service
  ```
- **View live logs**:
  ```bash
  sudo journalctl -u ics205 -f
  ```
- **Restart service**:
  ```bash
  sudo systemctl restart ics205.service
  ```

### 6. Reverse Proxy, Port 80, and HTTPS Setup

- **Port 80 Direct Binding**: The systemd service is granted `CAP_NET_BIND_SERVICE` capability via `AmbientCapabilities` and `CapabilityBoundingSet`, allowing the unprivileged `ics205` user to bind directly to privileged low ports such as port 80 (set via `PORT=80` in `/home/ics205/config/ics205.env` or `port = 80` in `/home/ics205/config/ics205.conf`).
- **Reverse Proxy and HTTPS**: In production, the application can also be placed behind a reverse proxy (such as Nginx, Caddy, or Apache) with TLS/HTTPS enabled:
  - Configure proxy forwarding to `http://127.0.0.1:8080`.
  - Ensure `X-Forwarded-For` and `X-Forwarded-Proto` headers are preserved.
  - When serving over HTTPS, ensure `-Dauth.secureCookie=true` is set in `JAVA_OPTS` to enforce `Secure` attributes on session cookies.
