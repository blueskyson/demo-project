import { useMemo } from 'react';
import axios, { type AxiosInstance } from 'axios';
import { useAuth } from 'react-oidc-context';
import type {
  AwardEvent,
  AwardEventRequest,
  CreateProposalRequest,
  ProposalDetail,
  ProposalFile,
  ProposalSummary,
  UpdateProposalRequest,
  User,
} from './types';

const apiBaseUrl = import.meta.env.VITE_API_BASE_URL;

export function createApiClient(accessToken?: string) {
  return axios.create({
    baseURL: apiBaseUrl,
    headers: accessToken ? { Authorization: `Bearer ${accessToken}` } : {},
  });
}

/** Typed wrapper around the backend REST API, bound to the current user's access token. */
export function createApi(http: AxiosInstance) {
  return {
    me: () => http.get<User>('/api/users/me').then((r) => r.data),
    listUsers: () => http.get<User[]>('/api/users').then((r) => r.data),

    listAwardEvents: () => http.get<AwardEvent[]>('/api/award-events').then((r) => r.data),
    getAwardEvent: (id: number) => http.get<AwardEvent>(`/api/award-events/${id}`).then((r) => r.data),
    createAwardEvent: (body: AwardEventRequest) =>
      http.post<AwardEvent>('/api/award-events', body).then((r) => r.data),
    updateAwardEvent: (id: number, body: AwardEventRequest) =>
      http.put<AwardEvent>(`/api/award-events/${id}`, body).then((r) => r.data),
    deleteAwardEvent: (id: number) => http.delete(`/api/award-events/${id}`),

    listProposals: (awardEventId?: number) =>
      http.get<ProposalSummary[]>('/api/proposals', { params: { awardEventId } }).then((r) => r.data),
    getProposal: (id: number) => http.get<ProposalDetail>(`/api/proposals/${id}`).then((r) => r.data),
    createProposal: (awardEventId: number, body: CreateProposalRequest) =>
      http.post<ProposalDetail>(`/api/award-events/${awardEventId}/proposals`, body).then((r) => r.data),
    updateProposal: (id: number, body: UpdateProposalRequest) =>
      http.put<ProposalDetail>(`/api/proposals/${id}`, body).then((r) => r.data),
    deleteProposal: (id: number) => http.delete(`/api/proposals/${id}`),

    addMember: (proposalId: number, userId: string) =>
      http.post<ProposalDetail>(`/api/proposals/${proposalId}/members`, { userId }).then((r) => r.data),
    removeMember: (proposalId: number, userId: string) =>
      http.delete<ProposalDetail>(`/api/proposals/${proposalId}/members/${userId}`).then((r) => r.data),

    uploadFile: (proposalId: number, file: File) => {
      const form = new FormData();
      form.append('file', file);
      return http.post<ProposalFile>(`/api/proposals/${proposalId}/files`, form).then((r) => r.data);
    },
    downloadFile: (proposalId: number, fileId: number) =>
      http
        .get<Blob>(`/api/proposals/${proposalId}/files/${fileId}/content`, { responseType: 'blob' })
        .then((r) => r.data),
    deleteFile: (proposalId: number, fileId: number) =>
      http.delete(`/api/proposals/${proposalId}/files/${fileId}`),
  };
}

export type Api = ReturnType<typeof createApi>;

export function useApi(): Api {
  const auth = useAuth();
  const token = auth.user?.access_token;
  return useMemo(() => createApi(createApiClient(token)), [token]);
}

/** Pulls the human-readable message out of the backend's ProblemDetail error body. */
export function errorMessage(error: unknown): string {
  if (axios.isAxiosError(error)) {
    const detail = (error.response?.data as { detail?: string } | undefined)?.detail;
    if (detail) return detail;
    if (error.response) return `Request failed with status ${error.response.status}`;
  }
  return error instanceof Error ? error.message : 'Unexpected error';
}
