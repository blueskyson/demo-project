import { useState } from 'react';
import { useAuth } from 'react-oidc-context';
import { Alert, Badge, Button, Card, Code, Group, Stack, Text } from '@mantine/core';
import { fetchPrivateMe, fetchPublicHello } from '../api/client';

export function SessionCard() {
  const auth = useAuth();
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
    if (!auth.user?.access_token) return;
    try {
      setApiResult(await fetchPrivateMe(auth.user.access_token));
    } catch {
      setApiError('Failed to call protected endpoint (token may be invalid or expired).');
    }
  }

  return (
    <Card withBorder padding="lg">
      <Stack gap="sm">
        <Group justify="space-between">
          <Text fw={600}>{auth.user?.profile.preferred_username}</Text>
          <Badge color="green">signed in</Badge>
        </Group>
        <Text size="sm" c="dimmed">
          {auth.user?.profile.email}
        </Text>
        <Text size="xs" c="dimmed">
          Access token expires at:{' '}
          {auth.user?.expires_at
            ? new Date(auth.user.expires_at * 1000).toLocaleTimeString()
            : 'unknown'}
        </Text>

        <Group>
          <Button variant="light" onClick={callPublicApi}>
            Call public endpoint
          </Button>
          <Button onClick={callPrivateApi}>Call protected endpoint</Button>
          <Button variant="outline" color="red" onClick={() => auth.signoutRedirect()}>
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
