# ESG Award (training project)

ESG Award submission system used for new-hire training. Keycloak (Docker) is the
identity provider, OpenFGA decides who may do what, a Spring Boot 4.1.1 /
**Java 17** OAuth2 Resource Server (PostgreSQL + Flyway) serves the API, and a
Vite + React + TypeScript + Mantine SPA signs in with Authorization Code + PKCE.

```
keycloak/     realm-export.json — auto-imported realm/client/roles/users
openfga/      model.fga (authorization model) + model.fga.yaml (model tests)
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
├── service/      Business logic and transactions; every public method has @RequirePermission
├── repository/   Spring Data JPA repositories
├── entity/       JPA entities (User, AwardEvent, Proposal, ProposalMember, ProposalFile)
├── dto/          Request/response records exchanged with the frontend
├── security/     Permission, @RequirePermission, @ResourceId, PermissionAspect,
│                 AuthorizationService (asks OpenFGA), CurrentUserService
├── openfga/      OpenFGA client + store/model bootstrap, tuple sync, startup backfill
├── storage/      FileStorage (uploaded file bytes on local disk)
├── exception/    Domain exceptions + ApiExceptionHandler (maps them to HTTP status)
└── config/       Spring Security (Keycloak JWT), Clock
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

### How authorization is enforced

No SpEL (`@PreAuthorize`) is used. Instead, every public service method
declares a strongly typed permission:

```java
@RequirePermission(Permission.PROPOSAL_UPDATE)
public ProposalDetailDto update(@ResourceId Long id, UpdateProposalRequest request) { ... }
```

1. [Permission](backend/src/main/java/com/example/esgaward/security/Permission.java)
   enumerates every action and the resource type it applies to (`NONE`,
   `AWARD_EVENT`, `PROPOSAL`). For resource-scoped permissions, `@ResourceId`
   marks the parameter holding the resource's id.
2. [PermissionAspect](backend/src/main/java/com/example/esgaward/security/PermissionAspect.java)
   (Spring AOP) intercepts `@RequirePermission` methods and calls
   `AuthorizationService.check(permission, resourceId)` before the method runs.
3. [AuthorizationService](backend/src/main/java/com/example/esgaward/security/AuthorizationService.java)
   maps each `Permission` to an OpenFGA relation in one exhaustive `switch`
   (adding a `Permission` without a mapping is a compile error) and asks
   OpenFGA (see below). It throws 403 (`AccessDeniedException`), 404 (missing
   resource) or 503 (OpenFGA unreachable — fail closed). It also computes the
   `editable` flag in API responses. The UI only uses it for hints
   ("read-only" badge / notice) and deliberately still shows every edit
   button, so trying a forbidden action surfaces the backend's 403 message —
   handy for seeing the authorization rules at work.
4. [ServiceArchitectureTest](backend/src/test/java/com/example/esgaward/architecture/ServiceArchitectureTest.java)
   (ArchUnit) fails the build when:
   - a public method in `..service..` has no `@RequirePermission`;
   - `@RequirePermission` is used outside public service methods;
   - the `@ResourceId` parameter doesn't match the permission's resource type;
   - `@PreAuthorize` / `@PostAuthorize` / `@Secured` is used anywhere.

Things to keep in mind:

- AOP works through the Spring proxy, so calling an annotated method from
  another method of the same class skips the check. Keep helpers non-public
  (ArchUnit then leaves them alone).
- The one body-dependent rule — only admins may set a proposal's `leaderId` —
  is checked inside `ProposalService`, because it depends on the request
  content rather than on the resource.

### OpenFGA

The rules live in the authorization model [openfga/model.fga](openfga/model.fga):

| Permission                              | OpenFGA check                                   |
|-----------------------------------------|-------------------------------------------------|
| `AWARD_EVENT_CREATE`                    | `system:esg-award#can_create_award_event`       |
| `AWARD_EVENT_READ`                      | `award_event:<id>#can_view`                     |
| `AWARD_EVENT_UPDATE` / `_DELETE`        | `award_event:<id>#can_manage`                   |
| `PROPOSAL_CREATE`                       | `award_event:<id>#can_create_proposal`          |
| `PROPOSAL_READ`, `PROPOSAL_FILE_READ`   | `proposal:<id>#can_view`                        |
| `PROPOSAL_UPDATE` / `_DELETE`, members, file upload/delete | `proposal:<id>#can_edit`     |
| `USER_READ`, `*_LIST`                   | none (any user with an app role)                |

Where the data comes from:

- **Relationships** (`proposal#leader`, `proposal#member`,
  `proposal#award_event`, `award_event#system`/`viewer`/`open`) are tuples
  written by [RelationshipTuples](backend/src/main/java/com/example/esgaward/openfga/RelationshipTuples.java)
  whenever the services change the matching rows (inside the same
  transaction, so a failed OpenFGA write rolls the database back).
- **Admin role** is *not* stored: when the token has the Keycloak `admin`
  role, every check carries the contextual tuple
  `system:esg-award#admin@user:<id>`. Keycloak stays the single source of
  truth for roles.
- **Deadline**: `award_event#open` is stored as
  `user:*` *with* the `before_deadline` condition and the event's deadline as
  condition context; each check sends `current_time`. Once the deadline
  passes, `open` — and therefore a leader's `can_edit` — turns false without
  anything being rewritten.
- **Listing**: a normal user's proposal list comes from OpenFGA `ListObjects`
  (`can_view`), and the `editable` flags from one `ListObjects` (`can_edit`).

On startup the backend ([OpenFgaConfig](backend/src/main/java/com/example/esgaward/openfga/OpenFgaConfig.java))
creates the `esg-award` store if needed, compiles `model.fga` and writes it as
a new model version only if it changed, then
[OpenFgaBackfill](backend/src/main/java/com/example/esgaward/openfga/OpenFgaBackfill.java)
writes tuples for all existing rows (idempotent; disable with
`openfga.backfill-on-startup: false`). So changing the model is: edit
`model.fga`, run its tests, restart the backend.

Test the model on its own with the OpenFGA CLI:

```
docker run --rm -v "$PWD/openfga:/openfga" -w /openfga openfga/cli:v0.8.1 model test --tests model.fga.yaml
```

Limitations worth knowing (fine for training, revisit for production):

- The database and OpenFGA are updated with a simple dual write. If the
  database commit fails *after* the OpenFGA write, they can drift until the
  next startup backfill; a transactional outbox would fix that.
- The backfill only adds missing tuples, it doesn't remove stale ones.

## 1. Start Keycloak, PostgreSQL and OpenFGA

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
- OpenFGA: HTTP API on http://localhost:8090 (playground disabled). It stores
  its data in the same Postgres, in a separate `openfga` database that
  `openfga-db-init` / `openfga-migrate` create and migrate on `up`.

Realm, client, roles, and users are defined in [keycloak/realm-export.json](keycloak/realm-export.json)
and imported on first boot. To reset everything (Keycloak and the database):
`docker compose down -v`.

## 2. Start the backend

```
cd backend
./mvnw spring-boot:run
```

Requires JDK 17+ and the compose stack (Postgres, Keycloak, OpenFGA). Runs on
http://localhost:8081; Flyway migrates the database and the OpenFGA store/model
are set up on startup. Uploaded files go to `backend/data/uploads` (`app.storage.dir`).

`./mvnw test` runs against an in-memory H2 database (profile `test`) and a
throwaway OpenFGA container (Testcontainers), so it needs Docker but not
Keycloak or the compose stack.

### API

| Method & path                                      | Description                                  |
|----------------------------------------------------|----------------------------------------------|
| `GET /api/users/me`                                | Current user (created/synced from the token) |
| `GET /api/users`                                   | Users who have signed in at least once       |
| `GET/POST /api/award-events`                       | List / create (admin) award events           |
| `GET/PUT/DELETE /api/award-events/{id}`            | Get / update (admin) / delete (admin)        |
| `GET /api/proposals?awardEventId=`                 | Proposals visible to the current user        |
| `POST /api/award-events/{id}/proposals`            | Create a proposal in an award event          |
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
