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
  TextInput,
  Textarea,
  Title,
} from '@mantine/core';
import { errorMessage, useApi } from '../api/client';
import type { ProposalDetail, User } from '../api/types';
import { useCurrentUser } from '../auth/CurrentUserContext';
import { DeadlineBadge } from '../components/DeadlineBadge';
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

  if (error) return <Alert color="red">{error}</Alert>;
  if (!proposal) return <Loader />;

  /** Runs a mutation, surfacing backend errors (e.g. 403 after the deadline) in one place. */
  async function run(action: () => Promise<unknown>) {
    setActionError(null);
    try {
      await action();
    } catch (e) {
      setActionError(errorMessage(e));
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
          {proposal.editable && (
            <Button size="xs" color="red" variant="light" onClick={removeProposal}>
              Delete proposal
            </Button>
          )}
        </Group>
      </Group>

      {!proposal.editable && (
        <Alert color="gray">
          Read-only: only the proposal leader can edit it, and only until the award event deadline (
          {formatDateTime(proposal.awardEvent.deadline)}).
        </Alert>
      )}
      {actionError && <Alert color="red">{actionError}</Alert>}

      <DetailsCard key={proposal.updatedAt} proposal={proposal} onSave={(body) => run(async () => setProposal(await api.updateProposal(proposal.id, body)))} />
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
    </Stack>
  );
}

function DetailsCard({
  proposal,
  onSave,
}: {
  proposal: ProposalDetail;
  onSave: (body: { title: string; description: string | null; leaderId: string | null }) => Promise<void>;
}) {
  const api = useApi();
  const me = useCurrentUser();
  const isAdmin = me.role === 'ADMIN';
  const [title, setTitle] = useState(proposal.title);
  const [description, setDescription] = useState(proposal.description ?? '');
  const [leaderId, setLeaderId] = useState(proposal.leader.id);
  const [saving, setSaving] = useState(false);
  const users = useAsync(
    useCallback(() => (isAdmin ? api.listUsers() : Promise.resolve([] as User[])), [api, isAdmin]),
  );
  const readOnly = !proposal.editable;

  async function save() {
    setSaving(true);
    await onSave({ title, description: description || null, leaderId: isAdmin ? leaderId : null });
    setSaving(false);
  }

  return (
    <Card withBorder>
      <Stack>
        <TextInput label="Title" value={title} readOnly={readOnly} onChange={(e) => setTitle(e.currentTarget.value)} />
        <Textarea
          label="Description"
          autosize
          minRows={4}
          value={description}
          readOnly={readOnly}
          onChange={(e) => setDescription(e.currentTarget.value)}
        />
        {isAdmin ? (
          <Select
            label="Leader"
            description="Only admins can change the leader."
            data={(users.data ?? [proposal.leader]).map((u) => ({ value: u.id, label: userLabel(u) }))}
            value={leaderId}
            onChange={(value) => value && setLeaderId(value)}
            searchable
          />
        ) : (
          <TextInput label="Leader" value={userLabel(proposal.leader)} readOnly />
        )}
        <Text size="xs" c="dimmed">
          Last updated {formatDateTime(proposal.updatedAt)}
        </Text>
        {!readOnly && (
          <Group justify="flex-end">
            <Button onClick={save} loading={saving} disabled={!title.trim()}>
              Save
            </Button>
          </Group>
        )}
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
    useCallback(() => (proposal.editable ? api.listUsers() : Promise.resolve([] as User[])), [api, proposal.editable]),
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
            {proposal.editable && (
              <ActionIcon variant="subtle" color="red" aria-label="Remove member" onClick={() => onRemove(member.id)}>
                ✕
              </ActionIcon>
            )}
          </Group>
        ))}
        {proposal.editable && (
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
        )}
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
          {proposal.editable && (
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
          )}
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
                    {proposal.editable && (
                      <Button size="xs" variant="subtle" color="red" onClick={() => onDelete(file.id)}>
                        Delete
                      </Button>
                    )}
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
