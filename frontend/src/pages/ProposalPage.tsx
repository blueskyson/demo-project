import { useCallback, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import {
  ActionIcon,
  Alert,
  Anchor,
  Button,
  Card,
  FileButton,
  Group,
  Loader,
  Select,
  Stack,
  Table,
  Text,
  Title,
} from '@mantine/core';
import { errorMessage, useApi } from '../api/client';
import type { ProposalDetail } from '../api/types';
import { DeadlineBadge } from '../components/DeadlineBadge';
import { ProposalFormModal } from '../components/ProposalFormModal';
import { useAsync } from '../hooks/useAsync';
import { formatBytes, formatDateTime, userLabel } from '../utils/format';

export function ProposalPage() {
  const proposalId = Number(useParams().id);
  const api = useApi();
  const navigate = useNavigate();
  const { data: proposal, setData: setProposal, error, reload } = useAsync(
    useCallback(() => api.getProposal(proposalId), [api, proposalId]),
  );
  const [actionError, setActionError] = useState<string | null>(null);
  const [editing, setEditing] = useState(false);

  if (error) return <Alert color="red">{error}</Alert>;
  if (!proposal) return <Loader />;

  /** Runs a mutation, surfacing backend errors (e.g. 403 after the deadline) in one place. */
  async function run(action: () => Promise<unknown>) {
    setActionError(null);
    try {
      await action();
    } catch (e) {
      setActionError(errorMessage(e));
      // The error alert is at the top; make sure it's visible even when the action was further down.
      window.scrollTo({ top: 0, behavior: 'smooth' });
      reload();
    }
  }

  async function removeProposal() {
    if (!proposal || !window.confirm(`Delete proposal "${proposal.title}"?`)) return;
    await run(async () => {
      await api.deleteProposal(proposal.id);
      navigate(`/award-events/${proposal.awardEvent.id}`);
    });
  }

  return (
    <Stack>
      <Anchor component={Link} to={`/award-events/${proposal.awardEvent.id}`} size="sm">
        ← {proposal.awardEvent.name}
      </Anchor>

      <Group justify="space-between">
        <Title order={3}>{proposal.title}</Title>
        <Group gap="xs">
          <DeadlineBadge event={proposal.awardEvent} />
          <Button size="xs" onClick={() => setEditing(true)}>
            Edit
          </Button>
          <Button size="xs" color="red" variant="light" onClick={removeProposal}>
            Delete proposal
          </Button>
        </Group>
      </Group>

      {!proposal.editable && (
        <Alert color="gray">
          You can't edit this proposal: only its leader can, and only until the award event deadline (
          {formatDateTime(proposal.awardEvent.deadline)}). Changes you try will be rejected by the server.
        </Alert>
      )}
      {actionError && <Alert color="red">{actionError}</Alert>}

      <DetailsCard proposal={proposal} />
      <MembersCard
        proposal={proposal}
        onAdd={(userId) => run(async () => setProposal(await api.addMember(proposal.id, userId)))}
        onRemove={(userId) => run(async () => setProposal(await api.removeMember(proposal.id, userId)))}
      />
      <FilesCard
        proposal={proposal}
        onUpload={(file) =>
          run(async () => {
            await api.uploadFile(proposal.id, file);
            reload();
          })
        }
        onDownload={(fileId, filename) =>
          run(async () => {
            const blob = await api.downloadFile(proposal.id, fileId);
            const url = URL.createObjectURL(blob);
            const a = document.createElement('a');
            a.href = url;
            a.download = filename;
            a.click();
            URL.revokeObjectURL(url);
          })
        }
        onDelete={(fileId) =>
          run(async () => {
            await api.deleteFile(proposal.id, fileId);
            reload();
          })
        }
      />

      <ProposalFormModal
        opened={editing}
        onClose={() => setEditing(false)}
        initial={proposal}
        onSubmit={async (values) => setProposal(await api.updateProposal(proposal.id, values))}
      />
    </Stack>
  );
}

function DetailsCard({ proposal }: { proposal: ProposalDetail }) {
  return (
    <Card withBorder>
      <Stack gap="xs">
        <Text size="sm" c="dimmed">
          Leader
        </Text>
        <Text>{userLabel(proposal.leader)}</Text>
        <Text size="sm" c="dimmed">
          Description
        </Text>
        <Text style={{ whiteSpace: 'pre-wrap' }} c={proposal.description ? undefined : 'dimmed'}>
          {proposal.description ?? 'No description.'}
        </Text>
        <Text size="xs" c="dimmed">
          Last updated {formatDateTime(proposal.updatedAt)}
        </Text>
      </Stack>
    </Card>
  );
}

function MembersCard({
  proposal,
  onAdd,
  onRemove,
}: {
  proposal: ProposalDetail;
  onAdd: (userId: string) => Promise<void>;
  onRemove: (userId: string) => Promise<void>;
}) {
  const api = useApi();
  const [selected, setSelected] = useState<string | null>(null);
  const users = useAsync(
    useCallback(() => api.listUsers(), [api]),
  );
  const taken = new Set([proposal.leader.id, ...proposal.members.map((m) => m.id)]);
  const candidates = (users.data ?? []).filter((u) => !taken.has(u.id));

  return (
    <Card withBorder>
      <Stack>
        <Title order={5}>Members</Title>
        {proposal.members.length === 0 && (
          <Text size="sm" c="dimmed">
            No members besides the leader.
          </Text>
        )}
        {proposal.members.map((member) => (
          <Group key={member.id} justify="space-between">
            <Text size="sm">{userLabel(member)}</Text>
            <ActionIcon variant="subtle" color="red" aria-label="Remove member" onClick={() => onRemove(member.id)}>
              ✕
            </ActionIcon>
          </Group>
        ))}
        <Group align="flex-end">
          <Select
            style={{ flex: 1 }}
            label="Add member"
            description="Only users who have signed in at least once are listed."
            placeholder="Pick a user"
            data={candidates.map((u) => ({ value: u.id, label: userLabel(u) }))}
            value={selected}
            onChange={setSelected}
            searchable
          />
          <Button
            disabled={!selected}
            onClick={async () => {
              if (!selected) return;
              await onAdd(selected);
              setSelected(null);
            }}
          >
            Add
          </Button>
        </Group>
      </Stack>
    </Card>
  );
}

function FilesCard({
  proposal,
  onUpload,
  onDownload,
  onDelete,
}: {
  proposal: ProposalDetail;
  onUpload: (file: File) => Promise<void>;
  onDownload: (fileId: number, filename: string) => Promise<void>;
  onDelete: (fileId: number) => Promise<void>;
}) {
  const [uploading, setUploading] = useState(false);

  return (
    <Card withBorder>
      <Stack>
        <Group justify="space-between">
          <Title order={5}>Files</Title>
          <FileButton
            onChange={async (file) => {
              if (!file) return;
              setUploading(true);
              await onUpload(file);
              setUploading(false);
            }}
          >
            {(props) => (
              <Button {...props} size="xs" loading={uploading}>
                Upload file
              </Button>
            )}
          </FileButton>
        </Group>
        {proposal.files.length === 0 ? (
          <Text size="sm" c="dimmed">
            No files uploaded.
          </Text>
        ) : (
          <Table>
            <Table.Thead>
              <Table.Tr>
                <Table.Th>Name</Table.Th>
                <Table.Th>Size</Table.Th>
                <Table.Th>Uploaded by</Table.Th>
                <Table.Th />
              </Table.Tr>
            </Table.Thead>
            <Table.Tbody>
              {proposal.files.map((file) => (
                <Table.Tr key={file.id}>
                  <Table.Td>
                    <Anchor component="button" onClick={() => onDownload(file.id, file.originalFilename)}>
                      {file.originalFilename}
                    </Anchor>
                  </Table.Td>
                  <Table.Td>{formatBytes(file.sizeBytes)}</Table.Td>
                  <Table.Td>
                    {userLabel(file.uploadedBy)}
                    <Text size="xs" c="dimmed">
                      {formatDateTime(file.uploadedAt)}
                    </Text>
                  </Table.Td>
                  <Table.Td>
                    <Button size="xs" variant="subtle" color="red" onClick={() => onDelete(file.id)}>
                      Delete
                    </Button>
                  </Table.Td>
                </Table.Tr>
              ))}
            </Table.Tbody>
          </Table>
        )}
      </Stack>
    </Card>
  );
}
