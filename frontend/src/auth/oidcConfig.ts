import type { AuthProviderProps } from 'react-oidc-context';
import { WebStorageStateStore } from 'oidc-client-ts';

const keycloakUrl = import.meta.env.VITE_KEYCLOAK_URL;
const realm = import.meta.env.VITE_KEYCLOAK_REALM;

export const oidcConfig: AuthProviderProps = {
  authority: `${keycloakUrl}/realms/${realm}`,
  client_id: import.meta.env.VITE_KEYCLOAK_CLIENT_ID,
  redirect_uri: `${window.location.origin}/callback`,
  post_logout_redirect_uri: `${window.location.origin}/`,
  response_type: 'code',
  scope: 'openid profile email',

  // Standard Authorization Code + PKCE flow (oidc-client-ts generates the
  // PKCE code_verifier/challenge automatically for public clients).
  automaticSilentRenew: true,

  // Keep tokens in sessionStorage (cleared when the tab closes) rather than
  // the library's in-memory-only default, so a page refresh doesn't force a
  // full re-login while still avoiding the longer-lived exposure of localStorage.
  userStore: new WebStorageStateStore({ store: window.sessionStorage }),

  onSigninCallback: () => {
    // Strip the ?code=...&state=... query params Keycloak appended after login.
    window.history.replaceState({}, document.title, window.location.pathname);
  },
};
