import { useCallback, type ReactNode } from 'react';
import { Link } from 'react-router-dom';
import { useAuth } from 'react-oidc-context';
import { Alert, Anchor, Badge, Button, Container, Group, Loader, Stack, Text, Title } from '@mantine/core';
import { useApi } from '../api/client';
import { CurrentUserContext } from '../auth/CurrentUserContext';
import { useAsync } from '../hooks/useAsync';
import { userLabel } from '../utils/format';
import { LoginCard } from './LoginCard';

/** Page chrome shared by all signed-in pages; also resolves the current user's app role. */
export function AppLayout({ children }: { children: ReactNode }) {
  const auth = useAuth();

  if (auth.isLoading) {
    return (
      <Container size="md" py="xl">
        <Loader />
      </Container>
    );
  }

  if (auth.error) {
    return (
      <Container size="md" py="xl">
        <Alert color="red" title="Authentication error">
          {auth.error.message}
        </Alert>
      </Container>
    );
  }

  if (!auth.isAuthenticated) {
    return (
      <Container size="sm" py="xl">
        <Stack gap="lg">
          <Title order={2}>ESG Award</Title>
          <LoginCard />
        </Stack>
      </Container>
    );
  }

  return <SignedInLayout>{children}</SignedInLayout>;
}

function SignedInLayout({ children }: { children: ReactNode }) {
  const auth = useAuth();
  const api = useApi();
  const { data: me, error } = useAsync(useCallback(() => api.me(), [api]));

  return (
    <Container size="md" py="xl">
      <Stack gap="lg">
        <Group justify="space-between">
          <Anchor component={Link} to="/" underline="never">
            <Title order={2}>ESG Award</Title>
          </Anchor>
          <Group gap="sm">
            {me && (
              <>
                <Text size="sm">{userLabel(me)}</Text>
                <Badge color={me.role === 'ADMIN' ? 'grape' : 'blue'}>
                  {me.role === 'ADMIN' ? 'admin' : 'normal user'}
                </Badge>
              </>
            )}
            <Button size="xs" variant="outline" color="red" onClick={() => auth.signoutRedirect()}>
              Logout
            </Button>
          </Group>
        </Group>

        {error && (
          <Alert color="red" title="Could not load your profile">
            {error}
          </Alert>
        )}
        {!me && !error && <Loader />}
        {me && <CurrentUserContext.Provider value={me}>{children}</CurrentUserContext.Provider>}
      </Stack>
    </Container>
  );
}
