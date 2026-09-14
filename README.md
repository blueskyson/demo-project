# demo-project

A minimal, standards-based OAuth2/OIDC stack: Keycloak (Docker) as the identity
provider, a Spring Boot 4.1.1 OAuth2 Resource Server API (with JPA/H2 persistence and
OpenFGA relationship-based authorization), and a Vite + React + TypeScript + Mantine SPA
using the Authorization Code + PKCE flow.

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

### Documents API — JPA + OpenFGA authorization

Persistence is an in-memory H2 database (Spring Data JPA, schema recreated on every
boot — browsable at http://localhost:8081/h2-console, JDBC URL `jdbc:h2:mem:demodb`).
Access control is [OpenFGA](https://openfga.dev) (also started by `docker compose up`,
API on http://localhost:8082, Playground UI on http://localhost:3000), using the
relation hierarchy `owner → editor → viewer` (an owner is implicitly an editor and a
viewer; an editor is implicitly a viewer). The model is bootstrapped by the app itself
on startup from [backend/src/main/resources/openfga/authorization-model.json](backend/src/main/resources/openfga/authorization-model.json)
— see the log line `OpenFGA ready: store=... model=...`.

| Endpoint | Enforcement |
|---|---|
| `POST /api/documents` | none — creates the document and grants the caller `owner` |
| `GET /api/documents` | Service layer — `AuthorizationService.listObjectIds(...)` (OpenFGA `ListObjects`) filters to documents the caller can `viewer` |
| `GET /api/documents/{id}` | **Controller layer** — `@FgaCheck(relation="viewer", ...)` on `DocumentController.get` |
| `PUT /api/documents/{id}` | **Service layer** — `@FgaCheck(relation="owner", ...)` on `DocumentService.updateDocument`. Deliberately `owner`, not `editor` — only the owner may edit their own document, an `editor` grant is not enough |
| `DELETE /api/documents/{id}` | **Service layer** — `@FgaCheck(relation="owner", ...)` on `DocumentService.deleteDocument` |
| `POST /api/documents/{id}/share` | **Service layer** — `@FgaCheck(relation="owner", ...)` on `DocumentService.shareDocument`, plus an explicit in-method check that the requested relation is `viewer`/`editor` (never re-granting `owner`) — a rule the annotation alone can't express |

Both layers use the same declarative annotation,
[`@FgaCheck`](backend/src/main/java/com/example/demobackend/authorization/FgaCheck.java)
(`userType`, `relation`, `objectType`, `idParam`), enforced by a single Spring AOP
`@Before` aspect,
[`FgaCheckAspect`](backend/src/main/java/com/example/demobackend/authorization/FgaCheckAspect.java)
— conceptually like `@PreAuthorize`, but checking an OpenFGA relation instead of a Spring
Security role. The aspect always reads the current user from
`SecurityContextHolder`, never from a method parameter, so `@FgaCheck` drops onto a
Service method (matching by plain parameter name, e.g. `idParam = "documentId"`) exactly
the same way it does onto a Controller method (matching by `@PathVariable`) — no
`requesterId`-style parameter needed either way. The rule of thumb: annotate exactly one
layer per operation (never both — that would run the OpenFGA check twice per request);
reach for the Service layer only when there's extra business logic, like `shareDocument`'s
relation-value validation, that a bare relation check can't express on its own.

To test end-to-end: `demo-frontend` has `directAccessGrantsEnabled: false` (see Notes
below), so mint a token the same way as in that section, then:

```
DOC=$(curl -s -X POST http://localhost:8081/api/documents \
  -H "Authorization: Bearer $DEMO_TOKEN" -H "Content-Type: application/json" \
  -d '{"title":"My Doc","content":"hello"}')
DOC_ID=$(echo "$DOC" | python3 -c "import json,sys;print(json.load(sys.stdin)['id'])")

# admin-demo has no relation yet -> 403
curl -i http://localhost:8081/api/documents/$DOC_ID -H "Authorization: Bearer $ADMIN_DEMO_TOKEN"

# demo (owner) shares viewer access
curl -X POST http://localhost:8081/api/documents/$DOC_ID/share \
  -H "Authorization: Bearer $DEMO_TOKEN" -H "Content-Type: application/json" \
  -d "{\"targetUserId\":\"<admin-demo's sub>\",\"relation\":\"viewer\"}"

# now admin-demo can GET, but still not PUT/DELETE (only the owner can update/delete)
```

Or skip curl entirely and use the frontend's **Documents** page (below) — it has a full
create/list/edit/delete/share UI and shows your own user ID for pasting into another
session's share form.

## 3. Start the frontend

```
cd frontend
npm install
npm run dev
```

Runs on http://localhost:5173. Config (Keycloak URL/realm/client, API base
URL) lives in [frontend/.env](frontend/.env).

- `/` — login/logout, calls `/api/public/hello` and `/api/private/me`.
- `/documents` ([DocumentsPage.tsx](frontend/src/pages/DocumentsPage.tsx)) — create,
  list, edit, delete, and share documents against the Documents API above. Displays your
  own user ID (the JWT `sub`) so you can copy it into another session's share form. To
  test sharing between two users, log in as `demo` in one browser profile/window and
  `admin-demo` in another (e.g. a private/incognito window) — sessions are stored in
  `sessionStorage`, isolated per tab/profile, so two windows can hold two different
  logins at once.

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
- The backend's H2 database and OpenFGA's store are both in-memory and reset together
  whenever the backend restarts / containers are recreated — there's never a stale
  mismatch between a document row and its OpenFGA tuples, but nothing here is meant to
  persist data long-term.
