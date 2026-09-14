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
