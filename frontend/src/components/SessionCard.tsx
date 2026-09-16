import { useState } from 'react';
import { Alert, Badge, Button, Card, Code, Group, Stack, Text } from '@mantine/core';
import { useAuth } from '../auth/AuthProvider';
import { fetchPrivateMe, fetchPublicHello } from '../api/client';

export function SessionCard() {
  const { keycloak } = useAuth();
  const [apiResult, setApiResult] = useState<unknown>(null);
  const [apiError, setApiError] = useState<string | null>(null);

  async function callPublicApi() {
    setApiError(null);
    try {
      setApiResult(await fetchPublicHello());
    } catch {
      setApiError('Failed to call public endpoint.');
    }
  }

  async function callPrivateApi() {
    setApiError(null);
    try {
      setApiResult(await fetchPrivateMe());
    } catch {
      setApiError('Failed to call protected endpoint (token may be invalid or expired).');
    }
  }

  return (
    <Card withBorder padding="lg">
      <Stack gap="sm">
        <Group justify="space-between">
          <Text fw={600}>{keycloak.tokenParsed?.preferred_username}</Text>
          <Badge color="green">signed in</Badge>
        </Group>
        <Text size="sm" c="dimmed">
          {keycloak.tokenParsed?.email}
        </Text>
        <Text size="xs" c="dimmed">
          Access token expires at:{' '}
          {keycloak.tokenParsed?.exp
            ? new Date(keycloak.tokenParsed.exp * 1000).toLocaleTimeString()
            : 'unknown'}
        </Text>

        <Group>
          <Button variant="light" onClick={callPublicApi}>
            Call public endpoint
          </Button>
          <Button onClick={callPrivateApi}>Call protected endpoint</Button>
          <Button
            variant="outline"
            color="red"
            onClick={() => keycloak.logout({ redirectUri: window.location.origin })}
          >
            Logout
          </Button>
        </Group>

        {apiError && (
          <Alert color="red" title="Request failed">
            {apiError}
          </Alert>
        )}
        {apiResult != null && <Code block>{JSON.stringify(apiResult, null, 2)}</Code>}
      </Stack>
    </Card>
  );
}
