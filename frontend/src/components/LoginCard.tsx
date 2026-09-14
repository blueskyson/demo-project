import { useAuth } from 'react-oidc-context';
import { Button, Card, Stack, Text } from '@mantine/core';

export function LoginCard() {
  const auth = useAuth();

  return (
    <Card withBorder padding="lg">
      <Stack gap="sm">
        <Text>You are not signed in.</Text>
        <Button onClick={() => auth.signinRedirect()}>Login</Button>
      </Stack>
    </Card>
  );
}
