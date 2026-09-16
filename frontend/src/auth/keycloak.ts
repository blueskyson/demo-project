import Keycloak from 'keycloak-js';

/**
 * A single, module-level instance — keycloak-js's `init()` must only ever run once per
 * page load, so this is created here rather than inside a component (where React's
 * StrictMode double-invoke, or a remount, would create a second instance).
 */
export const keycloak = new Keycloak({
  url: import.meta.env.VITE_KEYCLOAK_URL,
  realm: import.meta.env.VITE_KEYCLOAK_REALM,
  clientId: import.meta.env.VITE_KEYCLOAK_CLIENT_ID,
});
