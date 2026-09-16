import { Button, Card, Stack, Text } from '@mantine/core';
import { useAuth } from '../auth/AuthProvider';

export function LoginCard() {
  const { keycloak } = useAuth();

  return (
    <Card withBorder padding="lg">
      <Stack gap="sm">
        <Text>You are not signed in.</Text>
        <Button onClick={() => keycloak.login()}>Login</Button>
      </Stack>
    </Card>
  );
}
