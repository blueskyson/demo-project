import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { useAuth } from 'react-oidc-context';
import { Alert, Button, Card, CopyButton, Code, Container, Group, Loader, Stack, Text, Title } from '@mantine/core';
import { RequireAuth } from '../components/RequireAuth';
import { CreateDocumentForm } from '../components/CreateDocumentForm';
import { DocumentCard } from '../components/DocumentCard';
import type { Document } from '../api/documents';
import { extractErrorMessage, listDocuments } from '../api/documents';

export function DocumentsPage() {
  return (
    <RequireAuth>
      <DocumentsPageContent />
    </RequireAuth>
  );
}

function DocumentsPageContent() {
  const auth = useAuth();
  const accessToken = auth.user?.access_token;
  const currentUserId = auth.user?.profile.sub;

  const [documents, setDocuments] = useState<Document[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const reload = useCallback(async () => {
    if (!accessToken) return;
    setLoading(true);
    setError(null);
    try {
      setDocuments(await listDocuments(accessToken));
    } catch (e) {
      setError(extractErrorMessage(e));
    } finally {
      setLoading(false);
    }
  }, [accessToken]);

  useEffect(() => {
    reload();
  }, [reload]);

  if (!accessToken) {
    return null; // RequireAuth guarantees this only renders while authenticated
  }

  return (
    <Container size="md" py="xl">
      <Stack gap="lg">
        <Group justify="space-between">
          <Title order={2}>Documents</Title>
          <Button component={Link} to="/" variant="subtle">
            Back to home
          </Button>
        </Group>

        <Card withBorder padding="sm">
          <Group justify="space-between" wrap="nowrap">
            <Stack gap={2}>
              <Text size="sm" c="dimmed">
                Your user ID — share it with another logged-in user so they can grant{' '}
                <em>you</em> access to their documents:
              </Text>
              <Code>{currentUserId}</Code>
            </Stack>
            <CopyButton value={currentUserId ?? ''}>
              {({ copied, copy }) => (
                <Button size="xs" variant="light" onClick={copy}>
                  {copied ? 'Copied' : 'Copy'}
                </Button>
              )}
            </CopyButton>
          </Group>
        </Card>

        <CreateDocumentForm accessToken={accessToken} onCreated={reload} />

        {error && (
          <Alert color="red" title="Failed to load documents">
            {error}
          </Alert>
        )}

        {loading ? (
          <Loader />
        ) : (
          <Stack gap="md">
            {documents.length === 0 && (
              <Text c="dimmed">No documents visible to you yet — create one above, or ask an owner to share one.</Text>
            )}
            {documents.map((doc) => (
              <DocumentCard
                key={doc.id}
                document={doc}
                accessToken={accessToken}
                currentUserId={currentUserId}
                onChanged={reload}
              />
            ))}
          </Stack>
        )}
      </Stack>
    </Container>
  );
}
