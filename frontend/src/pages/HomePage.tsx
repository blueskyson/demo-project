import { useAuth } from 'react-oidc-context';
import { Alert, Container, Loader, Stack, Title } from '@mantine/core';
import { LoginCard } from '../components/LoginCard';
import { SessionCard } from '../components/SessionCard';

export function HomePage() {
  const auth = useAuth();

  if (auth.isLoading) {
    return (
      <Container size="sm" py="xl">
        <Loader />
      </Container>
    );
  }

  if (auth.error) {
    return (
      <Container size="sm" py="xl">
        <Alert color="red" title="Authentication error">
          {auth.error.message}
        </Alert>
      </Container>
    );
  }

  return (
    <Container size="sm" py="xl">
      <Stack gap="lg">
        <Title order={2}>Keycloak OIDC Demo</Title>
        {auth.isAuthenticated ? <SessionCard /> : <LoginCard />}
      </Stack>
    </Container>
  );
}
