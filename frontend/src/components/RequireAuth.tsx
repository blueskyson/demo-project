import type { PropsWithChildren, ReactNode } from 'react';
import { useAuth } from 'react-oidc-context';
import { Alert, Container, Loader } from '@mantine/core';
import { LoginCard } from './LoginCard';

function CenteredState({ children }: PropsWithChildren) {
  return (
    <Container size="sm" py="xl">
      {children}
    </Container>
  );
}

/** Gates a page behind login, showing the loading/error/login states HomePage used to
 * duplicate — the actual page content is only ever rendered once authenticated. */
export function RequireAuth({ children }: { children: ReactNode }) {
  const auth = useAuth();

  if (auth.isLoading) {
    return (
      <CenteredState>
        <Loader />
      </CenteredState>
    );
  }

  if (auth.error) {
    return (
      <CenteredState>
        <Alert color="red" title="Authentication error">
          {auth.error.message}
        </Alert>
      </CenteredState>
    );
  }

  if (!auth.isAuthenticated) {
    return (
      <CenteredState>
        <LoginCard />
      </CenteredState>
    );
  }

  return <>{children}</>;
}
