import { useCallback, useState } from 'react';
import { Alert, Button, Group, Modal, Select, Stack, Text, TextInput, Textarea } from '@mantine/core';
import { errorMessage, useApi } from '../api/client';
import type { ProposalDetail, User } from '../api/types';
import { useCurrentUser } from '../auth/CurrentUserContext';
import { useAsync } from '../hooks/useAsync';
import { userLabel } from '../utils/format';

export interface ProposalFormValues {
  title: string;
  description: string | null;
  /** Only sent by admins; normal users can't choose or change the leader. */
  leaderId: string | null;
}

interface Props {
  opened: boolean;
  onClose: () => void;
  /** Proposal being edited; omit to create a new one. */
  initial?: ProposalDetail;
  onSubmit: (values: ProposalFormValues) => Promise<unknown>;
}

/** Create / edit form for a proposal's title, description and (admin only) leader. */
export function ProposalFormModal({ opened, onClose, initial, onSubmit }: Props) {
  return (
    <Modal opened={opened} onClose={onClose} title={initial ? 'Edit proposal' : 'New proposal'} size="lg">
      {/* Remount the form each time the modal opens so it starts from `initial`. */}
      {opened && <ProposalForm initial={initial} onSubmit={onSubmit} onDone={onClose} />}
    </Modal>
  );
}

function ProposalForm({
  initial,
  onSubmit,
  onDone,
}: {
  initial?: ProposalDetail;
  onSubmit: Props['onSubmit'];
  onDone: () => void;
}) {
  const api = useApi();
  const me = useCurrentUser();
  const isAdmin = me.role === 'ADMIN';
  const [title, setTitle] = useState(initial?.title ?? '');
  const [description, setDescription] = useState(initial?.description ?? '');
  const [leaderId, setLeaderId] = useState<string>(initial?.leader.id ?? me.id);
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const users = useAsync(
    useCallback(() => (isAdmin ? api.listUsers() : Promise.resolve([] as User[])), [api, isAdmin]),
  );
  // Current members can't become leader (the backend rejects it), so leave them out.
  const memberIds = new Set(initial?.members.map((m) => m.id) ?? []);
  const leaderOptions = (users.data ?? [initial?.leader ?? me])
    .filter((u) => !memberIds.has(u.id))
    .map((u) => ({ value: u.id, label: userLabel(u) }));

  async function submit() {
    setSaving(true);
    setError(null);
    try {
      await onSubmit({ title: title.trim(), description: description.trim() || null, leaderId: isAdmin ? leaderId : null });
      onDone();
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setSaving(false);
    }
  }

  return (
    <Stack>
      <TextInput
        label="Title"
        required
        maxLength={200}
        value={title}
        onChange={(e) => setTitle(e.currentTarget.value)}
      />
      <Textarea
        label="Description"
        autosize
        minRows={5}
        maxLength={4000}
        value={description}
        onChange={(e) => setDescription(e.currentTarget.value)}
      />
      {isAdmin ? (
        <Select
          label="Leader"
          description="Only admins can assign the leader."
          data={leaderOptions}
          value={leaderId}
          onChange={(value) => value && setLeaderId(value)}
          searchable
          allowDeselect={false}
        />
      ) : (
        <Text size="xs" c="dimmed">
          Leader: {userLabel(initial?.leader ?? me)}
        </Text>
      )}
      {error && <Alert color="red">{error}</Alert>}
      <Group justify="flex-end">
        <Button variant="default" onClick={onDone}>
          Cancel
        </Button>
        <Button onClick={submit} loading={saving} disabled={!title.trim()}>
          {initial ? 'Save' : 'Create'}
        </Button>
      </Group>
    </Stack>
  );
}
