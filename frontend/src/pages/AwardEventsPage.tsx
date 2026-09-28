import { useCallback, useState } from 'react';
import { Link } from 'react-router-dom';
import { Alert, Anchor, Button, Card, Group, Loader, Stack, Table, Text, Title } from '@mantine/core';
import { errorMessage, useApi } from '../api/client';
import type { AwardEvent } from '../api/types';
import { AwardEventFormModal } from '../components/AwardEventFormModal';
import { DeadlineBadge } from '../components/DeadlineBadge';
import { useAsync } from '../hooks/useAsync';
import { formatDateTime } from '../utils/format';

export function AwardEventsPage() {
  const api = useApi();
  const { data: events, error, loading, reload } = useAsync(useCallback(() => api.listAwardEvents(), [api]));
  const [editing, setEditing] = useState<AwardEvent | 'new' | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);

  async function remove(event: AwardEvent) {
    if (!window.confirm(`Delete award event "${event.name}"?`)) return;
    setActionError(null);
    try {
      await api.deleteAwardEvent(event.id);
      reload();
    } catch (e) {
      setActionError(errorMessage(e));
    }
  }

  return (
    <Stack>
      <Group justify="space-between">
        <Title order={3}>Award events</Title>
        <Button onClick={() => setEditing('new')}>New award event</Button>
      </Group>

      {error && <Alert color="red">{error}</Alert>}
      {actionError && <Alert color="red">{actionError}</Alert>}
      {loading && !events && <Loader />}

      {events && events.length === 0 && (
        <Card withBorder>
          <Text c="dimmed">No award events yet.</Text>
        </Card>
      )}

      {events && events.length > 0 && (
        <Table striped highlightOnHover>
          <Table.Thead>
            <Table.Tr>
              <Table.Th>Name</Table.Th>
              <Table.Th>Deadline</Table.Th>
              <Table.Th>Status</Table.Th>
              <Table.Th />
            </Table.Tr>
          </Table.Thead>
          <Table.Tbody>
            {events.map((event) => (
              <Table.Tr key={event.id}>
                <Table.Td>
                  <Anchor component={Link} to={`/award-events/${event.id}`}>
                    {event.name}
                  </Anchor>
                </Table.Td>
                <Table.Td>{formatDateTime(event.deadline)}</Table.Td>
                <Table.Td>
                  <DeadlineBadge event={event} />
                </Table.Td>
                <Table.Td>
                  <Group gap="xs" justify="flex-end">
                    <Button size="xs" variant="light" onClick={() => setEditing(event)}>
                      Edit
                    </Button>
                    <Button size="xs" variant="light" color="red" onClick={() => remove(event)}>
                      Delete
                    </Button>
                  </Group>
                </Table.Td>
              </Table.Tr>
            ))}
          </Table.Tbody>
        </Table>
      )}

      <AwardEventFormModal
        opened={editing !== null}
        onClose={() => setEditing(null)}
        initial={editing === 'new' || editing === null ? undefined : editing}
        onSubmit={async (body) => {
          if (editing === 'new') await api.createAwardEvent(body);
          else if (editing) await api.updateAwardEvent(editing.id, body);
          reload();
        }}
      />
    </Stack>
  );
}
