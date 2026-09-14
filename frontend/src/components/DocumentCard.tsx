import { useState } from 'react';
import {
  Alert,
  Badge,
  Button,
  Card,
  Group,
  Modal,
  Select,
  Stack,
  Text,
  Textarea,
  TextInput,
} from '@mantine/core';
import type { Document, ShareRelation } from '../api/documents';
import { deleteDocument, extractErrorMessage, shareDocument, updateDocument } from '../api/documents';

export function DocumentCard({
  document,
  accessToken,
  currentUserId,
  onChanged,
}: {
  document: Document;
  accessToken: string;
  currentUserId: string | undefined;
  onChanged: () => void;
}) {
  const [editing, setEditing] = useState(false);
  const [title, setTitle] = useState(document.title);
  const [content, setContent] = useState(document.content);
  const [shareOpen, setShareOpen] = useState(false);
  const [targetUserId, setTargetUserId] = useState('');
  const [relation, setRelation] = useState<ShareRelation>('viewer');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const isOwner = document.ownerId === currentUserId;

  async function save() {
    setBusy(true);
    setError(null);
    try {
      await updateDocument(accessToken, document.id, title, content);
      setEditing(false);
      onChanged();
    } catch (e) {
      setError(extractErrorMessage(e));
    } finally {
      setBusy(false);
    }
  }

  async function remove() {
    if (!window.confirm(`Delete "${document.title}"? This cannot be undone.`)) return;
    setBusy(true);
    setError(null);
    try {
      await deleteDocument(accessToken, document.id);
      onChanged();
    } catch (e) {
      setError(extractErrorMessage(e));
    } finally {
      setBusy(false);
    }
  }

  async function share() {
    setBusy(true);
    setError(null);
    try {
      await shareDocument(accessToken, document.id, targetUserId.trim(), relation);
      setShareOpen(false);
      setTargetUserId('');
    } catch (e) {
      setError(extractErrorMessage(e));
    } finally {
      setBusy(false);
    }
  }

  return (
    <Card withBorder padding="lg">
      <Stack gap="sm">
        <Group justify="space-between" wrap="nowrap">
          {editing ? (
            <TextInput value={title} onChange={(e) => setTitle(e.currentTarget.value)} style={{ flex: 1 }} />
          ) : (
            <Text fw={600}>{document.title}</Text>
          )}
          {isOwner && <Badge color="blue">owner</Badge>}
        </Group>

        {editing ? (
          <Textarea value={content} onChange={(e) => setContent(e.currentTarget.value)} autosize minRows={2} />
        ) : (
          <Text size="sm" c="dimmed">
            {document.content || <em>(no content)</em>}
          </Text>
        )}

        <Text size="xs" c="dimmed">
          owner: {document.ownerId} · created {new Date(document.createdAt).toLocaleString()}
        </Text>

        <Group>
          {editing ? (
            <>
              <Button size="xs" onClick={save} loading={busy}>
                Save
              </Button>
              <Button
                size="xs"
                variant="subtle"
                onClick={() => {
                  setEditing(false);
                  setTitle(document.title);
                  setContent(document.content);
                }}
              >
                Cancel
              </Button>
            </>
          ) : (
            <Button size="xs" variant="light" onClick={() => setEditing(true)}>
              Edit
            </Button>
          )}
          <Button size="xs" variant="light" color="grape" onClick={() => setShareOpen(true)}>
            Share
          </Button>
          <Button size="xs" variant="light" color="red" onClick={remove} loading={busy}>
            Delete
          </Button>
        </Group>

        {error && (
          <Alert color="red" title="Request failed">
            {error}
          </Alert>
        )}
      </Stack>

      <Modal opened={shareOpen} onClose={() => setShareOpen(false)} title={`Share "${document.title}"`}>
        <Stack gap="sm">
          <TextInput
            label="Target user ID (their token's 'sub' claim)"
            placeholder="e.g. d97816f6-58f0-4021-a41c-82ffe440a060"
            value={targetUserId}
            onChange={(e) => setTargetUserId(e.currentTarget.value)}
          />
          <Select
            label="Grant relation"
            data={[
              { value: 'viewer', label: 'viewer — can view only' },
              { value: 'editor', label: 'editor — can view (update requires owner)' },
            ]}
            value={relation}
            onChange={(value) => setRelation((value as ShareRelation) ?? 'viewer')}
          />
          <Button onClick={share} loading={busy} disabled={!targetUserId.trim()}>
            Grant access
          </Button>
        </Stack>
      </Modal>
    </Card>
  );
}
