import { Container, Stack, Title } from '@mantine/core';
import { RequireAuth } from '../components/RequireAuth';
import { SessionCard } from '../components/SessionCard';

export function HomePage() {
  return (
    <RequireAuth>
      <Container size="sm" py="xl">
        <Stack gap="lg">
          <Title order={2}>Keycloak OIDC Demo</Title>
          <SessionCard />
        </Stack>
      </Container>
    </RequireAuth>
  );
}
