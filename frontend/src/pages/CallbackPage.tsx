import { useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from 'react-oidc-context';
import { Center, Loader, Stack, Text } from '@mantine/core';

// react-oidc-context's AuthProvider processes the ?code=&state= on mount
// (part of the Authorization Code exchange) whenever the app loads at the
// registered redirect_uri. This page just waits for that to finish, then
// forwards the user back into the app.
export function CallbackPage() {
  const auth = useAuth();
  const navigate = useNavigate();

  useEffect(() => {
    if (!auth.isLoading) {
      navigate('/', { replace: true });
    }
  }, [auth.isLoading, navigate]);

  return (
    <Center h="100vh">
      <Stack align="center" gap="sm">
        <Loader />
        <Text c="dimmed">Completing sign-in…</Text>
        {auth.error && (
          <Text c="red" size="sm">
            {auth.error.message}
          </Text>
        )}
      </Stack>
    </Center>
  );
}
