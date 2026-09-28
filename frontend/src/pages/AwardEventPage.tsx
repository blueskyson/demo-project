import { useCallback, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { Alert, Anchor, Badge, Button, Card, Group, Loader, Stack, Table, Text, Title } from '@mantine/core';
import { useApi } from '../api/client';
import { useCurrentUser } from '../auth/CurrentUserContext';
import { DeadlineBadge } from '../components/DeadlineBadge';
import { ProposalFormModal } from '../components/ProposalFormModal';
import { useAsync } from '../hooks/useAsync';
import { formatDateTime, userLabel } from '../utils/format';

export function AwardEventPage() {
  const eventId = Number(useParams().id);
  const api = useApi();
  const me = useCurrentUser();
  const navigate = useNavigate();
  const event = useAsync(useCallback(() => api.getAwardEvent(eventId), [api, eventId]));
  const proposals = useAsync(useCallback(() => api.listProposals(eventId), [api, eventId]));
  const [creating, setCreating] = useState(false);

  if (event.error) return <Alert color="red">{event.error}</Alert>;
  if (!event.data) return <Loader />;

  const canCreate = me.role === 'ADMIN' || !event.data.closed;

  return (
    <Stack>
      <Anchor component={Link} to="/" size="sm">
        ← All award events
      </Anchor>
      <Card withBorder>
        <Stack gap="xs">
          <Group justify="space-between">
            <Title order={3}>{event.data.name}</Title>
            <DeadlineBadge event={event.data} />
          </Group>
          <Text size="sm" c="dimmed">
            Deadline: {formatDateTime(event.data.deadline)}
          </Text>
          {event.data.description && <Text style={{ whiteSpace: 'pre-wrap' }}>{event.data.description}</Text>}
        </Stack>
      </Card>

      <Group justify="space-between">
        <Title order={4}>{me.role === 'ADMIN' ? 'All proposals' : 'My proposals'}</Title>
        {canCreate && <Button onClick={() => setCreating(true)}>New proposal</Button>}
      </Group>

      {proposals.error && <Alert color="red">{proposals.error}</Alert>}
      {proposals.loading && !proposals.data && <Loader />}
      {proposals.data && proposals.data.length === 0 && <Text c="dimmed">No proposals yet.</Text>}
      {proposals.data && proposals.data.length > 0 && (
        <Table striped highlightOnHover>
          <Table.Thead>
            <Table.Tr>
              <Table.Th>Title</Table.Th>
              <Table.Th>Leader</Table.Th>
              <Table.Th>Members</Table.Th>
              <Table.Th>Files</Table.Th>
              <Table.Th />
            </Table.Tr>
          </Table.Thead>
          <Table.Tbody>
            {proposals.data.map((p) => (
              <Table.Tr key={p.id}>
                <Table.Td>
                  <Anchor component={Link} to={`/proposals/${p.id}`}>
                    {p.title}
                  </Anchor>
                </Table.Td>
                <Table.Td>{userLabel(p.leader)}</Table.Td>
                <Table.Td>{p.memberCount}</Table.Td>
                <Table.Td>{p.fileCount}</Table.Td>
                <Table.Td>{p.editable ? <Badge variant="light">editable</Badge> : <Badge color="gray" variant="light">read-only</Badge>}</Table.Td>
              </Table.Tr>
            ))}
          </Table.Tbody>
        </Table>
      )}

      <ProposalFormModal
        opened={creating}
        onClose={() => setCreating(false)}
        onSubmit={async (values) => {
          const created = await api.createProposal({ awardEventId: eventId, ...values });
          navigate(`/proposals/${created.id}`);
        }}
      />
    </Stack>
  );
}

