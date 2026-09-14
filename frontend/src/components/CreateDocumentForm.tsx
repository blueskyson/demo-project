import { useState } from 'react';
import { Alert, Button, Card, Stack, Text, Textarea, TextInput } from '@mantine/core';
import { createDocument, extractErrorMessage } from '../api/documents';

export function CreateDocumentForm({
  accessToken,
  onCreated,
}: {
  accessToken: string;
  onCreated: () => void;
}) {
  const [title, setTitle] = useState('');
  const [content, setContent] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function submit() {
    setSubmitting(true);
    setError(null);
    try {
      await createDocument(accessToken, title, content);
      setTitle('');
      setContent('');
      onCreated();
    } catch (e) {
      setError(extractErrorMessage(e));
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <Card withBorder padding="lg">
      <Stack gap="sm">
        <Text fw={600}>Create a document</Text>
        <TextInput label="Title" value={title} onChange={(e) => setTitle(e.currentTarget.value)} />
        <Textarea
          label="Content"
          value={content}
          onChange={(e) => setContent(e.currentTarget.value)}
          autosize
          minRows={2}
        />
        {error && (
          <Alert color="red" title="Failed to create">
            {error}
          </Alert>
        )}
        <Button onClick={submit} loading={submitting} disabled={!title.trim()} style={{ alignSelf: 'flex-start' }}>
          Create
        </Button>
      </Stack>
    </Card>
  );
}
