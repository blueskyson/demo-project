import { Alert, Container, Loader, Stack, Title } from '@mantine/core';
import { useAuth } from '../auth/AuthProvider';
import { LoginCard } from '../components/LoginCard';
import { SessionCard } from '../components/SessionCard';

export function HomePage() {
  const { initialized, authenticated, error } = useAuth();

  if (!initialized) {
    return (
      <Container size="sm" py="xl">
        <Loader />
      </Container>
    );
  }

  if (error) {
    return (
      <Container size="sm" py="xl">
        <Alert color="red" title="Authentication error">
          {error}
        </Alert>
      </Container>
    );
  }

  return (
    <Container size="sm" py="xl">
      <Stack gap="lg">
        <Title order={2}>Keycloak OIDC Demo</Title>
        {authenticated ? <SessionCard /> : <LoginCard />}
      </Stack>
    </Container>
  );
}
