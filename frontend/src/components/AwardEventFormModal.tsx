import { useState } from 'react';
import { Alert, Button, Group, Modal, Stack, TextInput, Textarea } from '@mantine/core';
import { errorMessage } from '../api/client';
import type { AwardEvent, AwardEventRequest } from '../api/types';
import { fromLocalInputValue, toLocalInputValue } from '../utils/format';

interface Props {
  opened: boolean;
  onClose: () => void;
  /** Event being edited; omit to create a new one. */
  initial?: AwardEvent;
  onSubmit: (body: AwardEventRequest) => Promise<unknown>;
}

export function AwardEventFormModal({ opened, onClose, initial, onSubmit }: Props) {
  return (
    <Modal opened={opened} onClose={onClose} title={initial ? 'Edit award event' : 'New award event'}>
      {/* Remount the form each time the modal opens so it starts from `initial`. */}
      {opened && <AwardEventForm initial={initial} onSubmit={onSubmit} onDone={onClose} />}
    </Modal>
  );
}

function AwardEventForm({
  initial,
  onSubmit,
  onDone,
}: {
  initial?: AwardEvent;
  onSubmit: Props['onSubmit'];
  onDone: () => void;
}) {
  const [name, setName] = useState(initial?.name ?? '');
  const [description, setDescription] = useState(initial?.description ?? '');
  const [deadline, setDeadline] = useState(initial ? toLocalInputValue(initial.deadline) : '');
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  async function submit() {
    setSaving(true);
    setError(null);
    try {
      await onSubmit({
        name,
        description: description || null,
        deadline: fromLocalInputValue(deadline),
      });
      onDone();
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setSaving(false);
    }
  }

  return (
    <Stack>
      <TextInput label="Name" required value={name} onChange={(e) => setName(e.currentTarget.value)} />
      <Textarea
        label="Description"
        autosize
        minRows={3}
        value={description}
        onChange={(e) => setDescription(e.currentTarget.value)}
      />
      <TextInput
        label="Deadline"
        description="After this time normal users can no longer edit their proposals."
        type="datetime-local"
        required
        value={deadline}
        onChange={(e) => setDeadline(e.currentTarget.value)}
      />
      {error && <Alert color="red">{error}</Alert>}
      <Group justify="flex-end">
        <Button onClick={submit} loading={saving} disabled={!name.trim() || !deadline}>
          Save
        </Button>
      </Group>
    </Stack>
  );
}
