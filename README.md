# ESG Award (training project)

ESG Award submission system used for new-hire training. Keycloak (Docker) is the
identity provider, a Spring Boot 4.1.1 / **Java 17** OAuth2 Resource Server
(PostgreSQL + Flyway) serves the API, and a Vite + React + TypeScript + Mantine
SPA signs in with Authorization Code + PKCE.

```
keycloak/     realm-export.json — auto-imported realm/client/roles/users
backend/      Spring Boot 4.1.1 resource server (Maven, Java 17)
frontend/     Vite + React + TypeScript + Mantine SPA
```

## Domain

| Entity            | Table             | Notes                                                                 |
|-------------------|-------------------|-----------------------------------------------------------------------|
| `User`            | `users`           | Local copy of a Keycloak user (id = token `sub`), created on first API call |
| `AwardEvent`      | `award_event`     | Has a `deadline`; the event is *closed* from that instant on         |
| `Proposal`        | `proposal`        | Belongs to one award event, has one `leader` (a user)                 |
| `ProposalMember`  | `proposal_member` | Join entity: proposal ⟷ user many-to-many                             |
| `ProposalFile`    | `proposal_file`   | Metadata of an uploaded file; bytes are stored under `app.storage.dir` |

### Backend layout

```
com.example.esgaward
├── controller/   REST endpoints (@RestController) — HTTP only, delegate to services
├── service/      Business logic and transactions; AccessPolicy holds the permission rules
├── repository/   Spring Data JPA repositories
├── entity/       JPA entities (User, AwardEvent, Proposal, ProposalMember, ProposalFile)
├── dto/          Request/response records exchanged with the frontend
├── exception/    Domain exceptions + ApiExceptionHandler (maps them to HTTP status)
└── config/       Security (Keycloak JWT), Clock
```

Requests flow `controller → service → repository`; entities never leave the
service layer — controllers only see DTOs.

### Database migrations (Flyway)

The schema is managed by Flyway, which runs automatically when the backend
starts. Migrations live in [db/migration](backend/src/main/resources/db/migration)
(starting with [V1__init.sql](backend/src/main/resources/db/migration/V1__init.sql)),
and applied versions are recorded in the `flyway_schema_history` table.

- To change the schema, add a new file `V<next>__<description>.sql`
  (e.g. `V2__add_proposal_status.sql`). Never edit a migration that has
  already been applied — Flyway's checksum validation will fail on startup.
- Hibernate runs with `ddl-auto: validate`: it never changes the schema, it
  only fails fast if the entities and the migrated schema disagree.

## Roles and permissions

Two Keycloak realm roles, mapped to Spring authorities by
[KeycloakRealmRoleConverter](backend/src/main/java/com/example/esgaward/config/KeycloakRealmRoleConverter.java):
`admin` → `ROLE_ADMIN`, `normal_user` → `ROLE_NORMAL_USER`. Tokens with
neither role get 403 on every `/api/**` endpoint.

| Action                                          | `admin` | `normal_user`                                              |
|-------------------------------------------------|---------|------------------------------------------------------------|
| View award events                               | ✅      | ✅                                                         |
| Create / edit / delete award events             | ✅      | ❌                                                         |
| Create a proposal                               | ✅      | ✅ if the award event is still open (they become the leader) |
| View a proposal and download its files          | ✅      | ✅ if they are its leader or a member                      |
| Edit / delete a proposal, manage members & files | ✅      | ✅ only if they are its **leader** and the award event is **still open** |
| Change a proposal's leader                      | ✅      | ❌                                                         |

Role-only rules use `@PreAuthorize`; the per-resource rules all live in
[AccessPolicy](backend/src/main/java/com/example/esgaward/service/AccessPolicy.java).
API responses include an `editable` flag computed by the same policy, which the
UI uses to show or hide edit controls.

## 1. Start Keycloak and PostgreSQL

```
docker compose up -d
```

- Keycloak: http://localhost:8080 — admin console http://localhost:8080/admin (`admin` / `admin`)
  - Realm `esg-award`, SPA client `esg-award-frontend` (public, PKCE-S256, no secret)
  - Seeded users:
    - `esg-admin` / `admin123` — role `admin`
    - `alice` / `alice123` — role `normal_user`
    - `bob` / `bob123` — role `normal_user`
- PostgreSQL: `localhost:5432`, database `esg_award`, user/password `esg` / `esg`

Realm, client, roles, and users are defined in [keycloak/realm-export.json](keycloak/realm-export.json)
and imported on first boot. To reset everything (Keycloak and the database):
`docker compose down -v`.

## 2. Start the backend

```
cd backend
./mvnw spring-boot:run
```

Requires JDK 17+. Runs on http://localhost:8081; Flyway migrates the database on
startup. Uploaded files go to `backend/data/uploads` (`app.storage.dir`).

`./mvnw test` runs against an in-memory H2 database (profile `test`), so it
needs neither Docker nor Keycloak.

### API

| Method & path                                      | Description                                  |
|----------------------------------------------------|----------------------------------------------|
| `GET /api/users/me`                                | Current user (created/synced from the token) |
| `GET /api/users`                                   | Users who have signed in at least once       |
| `GET/POST /api/award-events`                       | List / create (admin) award events           |
| `GET/PUT/DELETE /api/award-events/{id}`            | Get / update (admin) / delete (admin)        |
| `GET /api/proposals?awardEventId=`                 | Proposals visible to the current user        |
| `POST /api/proposals`                              | Create a proposal                            |
| `GET/PUT/DELETE /api/proposals/{id}`               | Get / update / delete a proposal             |
| `POST /api/proposals/{id}/members`                 | Add a member (`{"userId": "..."}`)           |
| `DELETE /api/proposals/{id}/members/{userId}`      | Remove a member                              |
| `POST /api/proposals/{id}/files`                   | Upload a file (multipart field `file`, ≤ 20 MB) |
| `GET /api/proposals/{id}/files/{fileId}/content`   | Download a file                              |
| `DELETE /api/proposals/{id}/files/{fileId}`        | Delete a file                                |

Errors are returned as RFC 9457 problem details (`{"status":403,"detail":"The award event deadline has passed",...}`).

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
  to expiry, `oidc-client-ts` uses the refresh token to get a new one.
- **Logout**: `auth.signoutRedirect()` performs RP-Initiated Logout against
  Keycloak's `end_session_endpoint`, then redirects back to the SPA.

## Notes

- A user only appears in `GET /api/users` (and can be added as a member)
  after they have signed in once, because the local `users` row is created
  from their token.
- This is a local dev setup: Keycloak runs in dev mode (`start-dev`, no TLS),
  and CORS on the backend is scoped to `http://localhost:5173`. Do not deploy
  this configuration as-is.
