# demo-project

A minimal, standards-based OAuth2/OIDC stack: Keycloak (Docker) as the identity
provider, a Spring Boot 4.1.1 OAuth2 Resource Server API, and a Vite + React +
TypeScript + Mantine SPA using the Authorization Code + PKCE flow.

```
keycloak/     realm-export.json — auto-imported realm/client/users
backend/      Spring Boot 4.1.1 resource server (Maven)
frontend/     Vite + React + TypeScript + Mantine SPA
```

## 1. Start Keycloak

```
docker compose up -d
```

Waits until healthy, then serves the identity provider at http://localhost:8080.

- Admin console: http://localhost:8080/admin (`admin` / `admin`)
- Realm: `demo-realm`
- SPA client: `demo-frontend` (public, PKCE-S256 required, no client secret)
- Seeded users:
  - `demo` / `demo123` — role `USER`
  - `admin-demo` / `admin123` — roles `USER`, `ADMIN`

Realm/client/users are defined declaratively in [keycloak/realm-export.json](keycloak/realm-export.json)
and imported automatically on first boot — no manual setup in the admin console
is required. To reset to a clean state: `docker compose down -v`.

## 2. Start the backend

```
cd backend
./mvnw spring-boot:run
```

Runs on http://localhost:8081 as a pure OAuth2 Resource Server — it has no
Keycloak client of its own, it just validates access tokens against
`demo-realm`'s issuer/JWKS (`spring.security.oauth2.resourceserver.jwt.issuer-uri`
in [application.yml](backend/src/main/resources/application.yml)).

- `GET /api/public/hello` — no auth required.
- `GET /api/private/me` — requires a valid Bearer access token; returns the
  username/email/roles read out of the JWT. Keycloak's realm roles live under
  the non-standard `realm_access.roles` claim, so `KeycloakRealmRoleConverter`
  maps them to Spring's `ROLE_*` authorities.

- `GET|POST /api/documents`, `GET|PUT|DELETE /api/documents/{id}` — CRUD on
  the caller's own documents (only the owner may read/update/delete one).
- `GET /api/audit-logs?entityType=Document&entityId=<id>&actorId=<sub>` — `ADMIN`
  only; the audit trail, newest first, with field-level changes. Filtering by
  entity returns every operation that changed it, whichever endpoint or job did so.

Data lives in an in-memory H2 database; the schema is managed by Flyway
(`backend/src/main/resources/db/migration`).

## Audit log

Auditing is declarative — business code (`DocumentService`) contains no audit calls:

```java
@Audited(action = "DOCUMENT_UPDATE")
@PutMapping("/{id}")
public DocumentResponse update(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, ...)

@Entity
@AuditedEntity          // field changes of this entity are recorded
public class Document { ... @CreationTimestamp Instant createdAt; @UpdateTimestamp Instant updatedAt; }
```

The annotation deliberately doesn't declare *which* resources an endpoint touches —
one request may change many entities, and a hand-maintained list would go stale.
Affected resources are whatever Hibernate actually flushed (`audit_log_change`
has one row per changed field, with `entity_type` / `entity_id`). Requests that
changed nothing (denied / failed / reads) are identified by `path`
(`/api/documents/3f2a…`) and `route` (`/api/documents/{id}`).

How it works (`backend/src/main/java/com/example/demobackend/audit`):

| Piece | Role |
|---|---|
| `AuditAspect` | Around every `@Audited` controller method: records actor (JWT `sub` / `preferred_username`), action, HTTP method, route pattern, path, IP, `X-Request-Id`, duration, and outcome `SUCCESS` / `DENIED` (`AccessDeniedException`) / `FAILURE`. Runs outermost, so the service transaction has already committed when it finishes. |
| `EntityChangeListener` | Hibernate post-insert/update/delete listener for `@AuditedEntity` classes. Diffs old vs. new state per field; skips `@AuditIgnore`, `@CreationTimestamp`, `@UpdateTimestamp`, `@CreatedDate`, `@LastModifiedDate`, `@Version` fields; stores entity references by id and truncates long values. |
| `AuditChangeRecorder` | Buffers changes per transaction and releases them only **after commit** — rolled-back changes are never logged. They're attached to the open `@Audited` call, or logged as `ENTITY_CHANGE` if none (e.g. a batch job). |
| `AuditPublisher` | Non-blocking hand-off: `CompletableFuture.runAsync` on a bounded `ThreadPoolExecutor` (Java 17, no virtual threads). The request thread never waits for the audit DB. If the queue is full or the write fails, the event goes to the `AUDIT_FALLBACK` logger — the request is never slowed down or failed. Queued events are drained on shutdown. |
| `AuditLogWriter` | Plain JDBC (not JPA) insert into `audit_log` + `audit_log_change` in its own transaction, so it can't trigger the entity listener or join a business transaction. |

Tuning: `app.audit.worker-threads`, `queue-capacity`, `max-value-length` in
[application.yml](backend/src/main/resources/application.yml).

Not audited: requests rejected before reaching a controller (401 from the
security filter, 400 from `@Valid`).

### Trying it in a browser — http://localhost:8081/

The backend serves a single dependency-free page
([static/index.html](backend/src/main/resources/static/index.html): hand-rolled
Authorization Code + PKCE, `fetch`) at http://localhost:8081/. It's same-origin
with the API, so no CORS is involved; `demo-frontend` allows
`http://localhost:8081/*` as a redirect URI for it.

Log in as `demo`, create/edit/delete documents; copy a document id, log out,
log in as `admin-demo`, try `PUT` on that id (→ 403, audited as `DENIED`), and
watch the audit log panel with field diffs.

### Reminding developers — `AuditArchitectureTest` (ArchUnit)

`./mvnw test` fails when:

- a `POST`/`PUT`/`PATCH`/`DELETE` endpoint has no `@Audited`;
- any other endpoint has neither `@Audited` nor `@NoAudit(reason = "...")`, or the reason is blank;
- an `@Entity` has neither `@AuditedEntity` nor `@NoAudit`;
- a `createdAt`/`updatedAt`-style field of an audited entity isn't marked as a timestamp / `@AuditIgnore`;
- `@Audited` is used outside a `@RestController`;
- code outside the `audit` package depends on anything but `audit.annotation` (i.e. calls audit internals directly).

`AuditArchitectureFixtureTest` runs the rules against deliberately broken
classes (`com.example.auditfixture`) to prove they actually fail.

## 3. Start the frontend

```
cd frontend
npm install
npm run dev
```

Runs on http://localhost:5173. Config (Keycloak URL/realm/client, API base
URL) lives in [frontend/.env](frontend/.env).

## Auth flow

- **Login**: `auth.signinRedirect()` (`react-oidc-context`) starts the
  standard Authorization Code + PKCE flow — the SPA is a public client, so
  PKCE (not a client secret) protects the code exchange. Keycloak redirects
  back to `/callback`, which completes the token exchange and routes home.
- **Calling the API**: the SPA attaches the OIDC access token as
  `Authorization: Bearer <token>` on requests to the backend.
- **Refresh**: `automaticSilentRenew: true` — when the access token is close
  to expiry, `oidc-client-ts` transparently uses the refresh token (issued
  alongside the access token on login) to get a new one via the standard
  `grant_type=refresh_token` request, no iframe/redirect needed.
- **Logout**: `auth.signoutRedirect()` performs RP-Initiated Logout — it hits
  Keycloak's `end_session_endpoint` with the ID token, ending the Keycloak SSO
  session, then redirects back to the SPA.

## Notes

- `demo-frontend` has `directAccessGrantsEnabled: false` — the SPA must use
  the browser redirect flow, not the resource-owner password grant. This was
  verified during development by temporarily toggling it on via the Admin API
  to fetch a test token, then reverting it back to match `realm-export.json`.
- This is a local dev setup: Keycloak runs in dev mode (`start-dev`, no TLS,
  ephemeral H2 storage), and CORS on the backend is scoped to
  `http://localhost:5173`. Do not deploy this configuration as-is.
