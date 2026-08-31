# MemberConnect — Backend

REST API for MemberConnect, a membership administration system for a teachers' welfare fund
organised across Sri Lankan educational districts and zones.

It covers the full membership lifecycle: registration and board approval, membership
documentation, profile changes, district transfers, termination, retirement, member death
records, death donations, dormancy, and two scholarship programmes.

**Stack:** Java 21 · Spring Boot 3.5.11 · Spring Data JPA · Spring Security (JWT) · PostgreSQL 16

---

## Requirements

| | |
|---|---|
| Java | 21 (Eclipse Temurin recommended) |
| Maven | not needed — use the bundled `./mvnw` wrapper |
| PostgreSQL | 16 |
| Docker | optional, see [With Docker](#with-docker) |

---

## Quick start

### 1. Configure the environment

```bash
cp .env.example .env
```

Then edit `.env`. At minimum you must set:

```properties
DB_URL=jdbc:postgresql://localhost:5432/memberconnect
DB_USERNAME=postgres
DB_PASSWORD=your_password
JWT_SECRET=<base64 key, at least 32 bytes decoded>
```

Generate a JWT secret with:

```bash
openssl rand -base64 48
```

> The application **will not start** without `JWT_SECRET`. Everything else has a working default.

### 2. Run

```bash
./mvnw spring-boot:run
```

The API comes up on <http://localhost:8080>.

On first startup an administrator account is seeded:

| Username | Password | Role |
|---|---|---|
| `superadmin` | `Admin@1234` | `SUPER_ADMIN` |

Change it after first login. All other accounts are created from the app under
**Administration → User Management**.

### With Docker

From the parent directory, `docker-compose.yml` runs the API, the frontend and PostgreSQL
together:

```bash
docker compose up --build
```

This still reads `backend/.env`, so create it first. Postgres is published on host port
**5433** to avoid clashing with a local install.

---

## Configuration

All settings live in `src/main/resources/application.properties` and can be overridden from
the environment or `.env`.

### Required

| Variable | Notes |
|---|---|
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | PostgreSQL connection |
| `JWT_SECRET` | Base64; HS256 needs ≥ 32 bytes decoded. Tokens last 24 hours. |

### Optional

| Variable | Default | Notes |
|---|---|---|
| `SERVER_PORT` | `8080` | |
| `AWS_ACCESS_KEY` / `AWS_SECRET_KEY` / `AWS_REGION` / `AWS_BUCKET_NAME` | — | Document storage. Without these, uploads fall back to a local `uploads/` directory. |
| `NOTIFY_EMAIL_ENABLED` | `false` | When false, emails are written to the application log instead of being sent. |
| `NOTIFY_SMS_ENABLED` | `false` | **No SMS provider is integrated.** Setting this true only logs a warning that real delivery was requested. |
| `SMTP_HOST` / `SMTP_PORT` / `SMTP_USERNAME` / `SMTP_PASSWORD` | — | Only read when `NOTIFY_EMAIL_ENABLED=true`. |

### Feature flags

Every flag below is **off by default**, so a fresh environment runs the "not yet integrated"
path — which is the intended behaviour until the external modules exist.

| Flag | Effect when enabled |
|---|---|
| `FINANCE_INTEGRATION_ENABLED` | Sends termination / member-death / death-donation hand-offs to the Finance module. Off = logged in full, not sent. |
| `FINANCE_DEATH_DONATION_ENABLED` | Pulls death-donation entitlement figures from Finance. Off = zeroed, operator enters them by hand. |
| `FINANCE_MOCK_ENABLED` | Registers a stand-in Finance module at `/mock-finance` that speaks the real contract over HTTP. Also set `FINANCE_BASE_URL=http://localhost:8080/mock-finance`. |
| `LOAN_INTEGRATION_ENABLED` | Asks the Loan module to re-file loans after a cross-district transfer. |
| `DISPATCH_REQUIRE_ALL_PRINTED` | Default `true`. Requires card + signature card + passbook printed before dispatch. |

### Demo data

Two seeders are meant to be switched on for **one startup only**, then switched back off:

| Flag | Loads |
|---|---|
| `MEMBER_APPLICATION_SEED_DEMO` | `db/member_application_seed.csv` as in-flight applications (`APP-DEMO-REG-` ids) |
| `DORMANT_SEED_DEMO_CANDIDATES` | Ages a few demo members past the dormancy cut-off so identification finds something |

> **Expected on a fresh database:** the registration and board-approval screens look empty even
> though the member list is full. That is correct — demo members are created from applications
> that are already `APPROVED`, and approved applications are excluded from the registration list
> by design. Enable `MEMBER_APPLICATION_SEED_DEMO` once to populate them.

Demo rows can be removed again with `src/main/resources/db/undo-demo-seed.sql`, which targets
only the `APP-DEMO-REG-` and `MEM-DEMO-` id ranges.

---

## Project layout

```
src/main/java/com/memberconnect/backend/
├── config/       Security, JWT, CORS, seeders, scheduled jobs, role/permission matrix
├── controller/   REST endpoints (~40)
├── service/      Business rules and workflow state machines (~55)
│   ├── finance/  Outbound clients + after-commit listeners for the Finance module
│   ├── loan/     Outbound client for the Loan module
│   └── notification/  Email and SMS senders
├── repository/   Spring Data JPA repositories
├── model/        JPA entities (~90)
├── dto/          Request and response payloads
├── enums/        Roles, permissions and every workflow status
├── event/        Domain events, consumed after commit
├── exception/    Global exception handler
└── mock/         Stand-in Finance module (only active behind a flag)
```

The schema is created by Hibernate (`ddl-auto=update`). **There are no migration scripts** — see
[Known constraints](#known-constraints).

---

## How the domain works

Almost every action is a **request that travels through an approval ladder**. The same shape
repeats across eleven workflows with different actors and different numbers of gates:

```
NEW → SUBMITTED_FOR_APPROVAL → APPROVED | REJECTED
              (with INCOMPLETE and INACTIVE as side branches)
```

Four rules hold across nearly all of them:

1. **Maker/checker** — the office that raises a request generally cannot approve it.
2. **Editing stops at submission** — fields and supporting documents lock server-side once a
   request leaves `NEW` / `INCOMPLETE`.
3. **Inactive is a right, not a status** — moving a request into *or out of* `INACTIVE` requires
   a right held only by Super Admin, Head Office and Board Secretary.
4. **District scoping** — a District Office user only sees and acts on their own district's
   records, enforced in the service layer.

### Roles

`SUPER_ADMIN` · `DISTRICT_OFFICE` · `DISTRICT_COMMITTEE` · `PD_COMMITTEE` · `HEAD_OFFICE` ·
`BOARD_SECRETARY` · `ACCOUNTS` · `SCHOLARSHIP_OFFICER` · `DEATH_DONATION_OFFICER`

Authorisation is enforced at three levels, and they are not interchangeable:

| Level | Where | Answers |
|---|---|---|
| Role lists | `@PreAuthorize("hasAnyRole(...)")` | May this role call this endpoint? |
| Named permissions | `@PreAuthorize("hasAuthority('G5_LIST_PROCESS')")`, resolved via `config/RolePermissions.java` | Does this role hold this specific right? |
| Record guards | `assertCallerMayAccess`, `assertDecidableLevel`, `assertNotSelfApproval`, … in the services | May this user act on **this record**? |

Some accounts also carry an **authority flag** (`User.isAuthorized`) that grants extra
permissions on top of the role — the specification repeatedly describes rights held by a named
officer rather than by a whole office. Only `DISTRICT_OFFICE` and `HEAD_OFFICE` accounts can
carry it; the backend forces it false for every other role.

`config/RolePermissions.java` is the single source of truth for the matrix and documents the
reasoning behind each grant.

### Reference numbering

| Entity | Format | Entity | Format |
|---|---|---|---|
| Member application | `APP-2026-001` | Member | `MEM-2026-001` |
| Termination | `T-2026-001` | Retirement | `R-2026-001` |
| Member death | `MD-2026-001` | Death donation | `DD-2026-001` |
| Grade 5 request | `G5-2026-001` | Dispatch | `DSP-2026-001` |
| Profile change | `PCR` / `NCR` / `NMR` / `RCR-2026-001` | University | `USR-001` / `USFR-001` |

Year-scoped sequences restart each calendar year.

---

## API

Base paths are grouped under `/api`. There is no OpenAPI document generated, so the controllers
are the reference.

| Area | Base path |
|---|---|
| Authentication | `/api/auth` (public) |
| Own profile | `/api/profile` |
| User administration | `/api/admin/users` (Super Admin) |
| Applications / members | `/api/applications`, `/api/members` |
| Board meetings & lists | `/api/board-meetings`, `/api/board-approval-lists` |
| Profile changes | `/api/profile-changes`, `/api/v2`, `/api/v3`, `/api4/remitance`, `/api5/namechange` |
| Transfers | `/api/member-transfers` |
| Termination / retirement | `/api/termination-requests`, `/api/retirement-requests` |
| Death records / donations | `/api/member-death-records`, `/api/death-donation-requests` |
| Dormancy | `/api/dormant-members` |
| Scholarships | `/api/grade5`, `/api/university-scholarships…` |
| Documents | `/api/documents`, `/api/file`, `/api/uploaded-documents` |
| Finance callbacks | `/api/finance/*` (Accounts / Super Admin) |
| Reference data | `/api/masters`, `/api/education`, `/api/banks`, `/api/remittance-master` |

### Authentication

```http
POST /api/auth/login
{ "username": "superadmin", "password": "Admin@1234" }
```

Returns a JWT valid for 24 hours. Send it as `Authorization: Bearer <token>` on every other
request. `401` means the token is missing or expired; `403` means the account is valid but not
permitted to perform that action.

---

## Tests

```bash
./mvnw test
```

211 JUnit tests, concentrated on the modules with the most contentious authorisation rules —
death donation, termination and dormancy — plus the role matrix and notifications.

> There is no test profile (`src/test/resources` does not exist), so the suite currently needs a
> reachable database and a valid `JWT_SECRET`.

---

## Build

```bash
./mvnw clean package        # produces target/backend-0.0.1-SNAPSHOT.jar
java -jar target/*.jar
```

The `Dockerfile` uses a multi-stage build — a JDK image compiles the jar, and the final image
carries only a JRE plus the jar.

---

## Known constraints

- **No database migrations.** The schema comes from Hibernate `ddl-auto=update`, which adds
  columns but will not alter or drop them safely. Moving to Flyway or Liquibase before the first
  production deployment is recommended.
- **`open-in-view` is deliberately on.** Several read paths map lazy associations to DTOs outside
  a transaction; turning it off raises `LazyInitializationException` on the dashboard and progress
  tabs until those reads are given `@Transactional(readOnly = true)` or explicit fetch joins.
- **Connection pool is capped at 3** (`DB_HIKARI_MAX_POOL_SIZE`) because the team shared one
  remote database. Raise it when running against your own instance.
- **Document endpoints are currently unauthenticated** in `SecurityConfig` — `/api/documents/**`,
  `/api/file/**` and `/api/uploaded-documents/**`. These carry member documents and should be
  moved behind the filter.
- **The dormancy scheduler assumes a single instance.** There is no distributed lock; the run is
  idempotent, so two instances would duplicate work rather than corrupt data.
