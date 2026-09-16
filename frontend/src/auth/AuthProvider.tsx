import { createContext, useContext, useEffect, useMemo, useState } from 'react';
import type { ReactNode } from 'react';
import { keycloak } from './keycloak';

interface AuthContextValue {
  keycloak: typeof keycloak;
  /** False until the initial `keycloak.init()` (including the silent SSO check) resolves. */
  initialized: boolean;
  /** Reactive mirror of `keycloak.authenticated` — kept in sync via Keycloak's event callbacks. */
  authenticated: boolean;
  error: string | null;
}

const AuthContext = createContext<AuthContextValue | null>(null);

// Module-level, not component state: `keycloak.init()` may only run once per page load,
// but React 19's StrictMode intentionally mounts effects twice in development.
let initStarted = false;

export function AuthProvider({ children }: { children: ReactNode }) {
  const [initialized, setInitialized] = useState(false);
  const [authenticated, setAuthenticated] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (initStarted) return;
    initStarted = true;

    keycloak.onAuthSuccess = () => setAuthenticated(true);
    keycloak.onAuthRefreshSuccess = () => setAuthenticated(true);
    keycloak.onAuthLogout = () => setAuthenticated(false);
    keycloak.onAuthRefreshError = () => setAuthenticated(false);

    // keycloak-js has no built-in automatic silent renew — the standard pattern is to
    // refresh from this event, which fires once the access token's `exp` has passed.
    keycloak.onTokenExpired = () => {
      keycloak.updateToken(30).catch(() => {
        setAuthenticated(false);
      });
    };

    keycloak
      .init({
        onLoad: 'check-sso',
        pkceMethod: 'S256',
        silentCheckSsoRedirectUri: `${window.location.origin}/silent-check-sso.html`,
        // The iframe-based login-status poll is prone to false negatives when browsers
        // block third-party storage access; onTokenExpired + updateToken already cover
        // refresh, so this isn't needed.
        checkLoginIframe: false,
      })
      .then((isAuthenticated) => setAuthenticated(isAuthenticated))
      .catch(() => setError('Failed to reach Keycloak.'))
      .finally(() => setInitialized(true));
  }, []);

  const value = useMemo<AuthContextValue>(
    () => ({ keycloak, initialized, authenticated, error }),
    [initialized, authenticated, error],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return context;
}
