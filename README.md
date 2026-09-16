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

## 3. Start the frontend

```
cd frontend
npm install
npm run dev
```

Runs on http://localhost:5173. Config (Keycloak URL/realm/client, API base
URL) lives in [frontend/.env](frontend/.env).

## Auth flow

The SPA uses Keycloak's own JS adapter, [`keycloak-js`](https://www.npmjs.com/package/keycloak-js),
directly (typed — it ships its own `.d.ts`) rather than a generic OIDC client. There's no
`react-keycloak` wrapper either: [`src/auth/keycloak.ts`](frontend/src/auth/keycloak.ts)
holds a single module-level `Keycloak` instance (it may only be `init()`-ed once per page
load), and [`src/auth/AuthProvider.tsx`](frontend/src/auth/AuthProvider.tsx) is a small
custom React context that mirrors its state (`initialized`, `authenticated`) reactively —
plain properties on the `Keycloak` instance don't trigger re-renders on their own.

- **Login**: `keycloak.login()` starts the standard Authorization Code + PKCE flow
  (`pkceMethod: 'S256'` in `init()`) — the SPA is a public client, so PKCE, not a client
  secret, protects the code exchange. There's no dedicated `/callback` route: unlike
  `oidc-client-ts`, keycloak-js's `init()` itself detects and completes the code exchange
  on whichever page the browser lands back on (here, `/`, since no `redirectUri` is set),
  then strips the query params.
- **Silent SSO check on load**: `onLoad: 'check-sso'` + `silentCheckSsoRedirectUri`
  quietly checks for an existing Keycloak session in a hidden iframe on first load (via
  [public/silent-check-sso.html](frontend/public/silent-check-sso.html), the standard
  keycloak-js pattern) — so a page refresh doesn't force a full-page redirect just to find
  out you're already logged in.
- **Calling the API**: an axios request interceptor
  ([src/api/client.ts](frontend/src/api/client.ts)) reads `keycloak.token` and attaches
  it as `Authorization: Bearer <token>` on every request to the backend.
- **Refresh**: keycloak-js has no automatic renewal of its own — the standard pattern is
  wiring `keycloak.onTokenExpired` to call `keycloak.updateToken(minValidity)`, which this
  app does in `AuthProvider`. The API client also calls `updateToken(30)` before every
  request as a belt-and-suspenders check. Both go through the standard
  `grant_type=refresh_token` request against Keycloak's token endpoint.
- **Logout**: `keycloak.logout({ redirectUri })` performs RP-Initiated Logout — it hits
  Keycloak's `end_session_endpoint`, ending the Keycloak SSO session, then redirects back
  to the SPA.

## Notes

- `demo-frontend` has `directAccessGrantsEnabled: false` — the SPA must use
  the browser redirect flow, not the resource-owner password grant. This was
  verified during development by temporarily toggling it on via the Admin API
  to fetch a test token, then reverting it back to match `realm-export.json`.
- This is a local dev setup: Keycloak runs in dev mode (`start-dev`, no TLS,
  ephemeral H2 storage), and CORS on the backend is scoped to
  `http://localhost:5173`. Do not deploy this configuration as-is.
