import axios from 'axios';
import { createApiClient } from './client';

export interface Document {
  id: string;
  title: string;
  content: string;
  ownerId: string;
  createdAt: string;
}

export type ShareRelation = 'viewer' | 'editor';

export async function listDocuments(accessToken: string): Promise<Document[]> {
  const { data } = await createApiClient(accessToken).get<Document[]>('/api/documents');
  return data;
}

export async function createDocument(accessToken: string, title: string, content: string): Promise<Document> {
  const { data } = await createApiClient(accessToken).post<Document>('/api/documents', { title, content });
  return data;
}

export async function updateDocument(
  accessToken: string,
  id: string,
  title: string,
  content: string,
): Promise<Document> {
  const { data } = await createApiClient(accessToken).put<Document>(`/api/documents/${id}`, { title, content });
  return data;
}

export async function deleteDocument(accessToken: string, id: string): Promise<void> {
  await createApiClient(accessToken).delete(`/api/documents/${id}`);
}

export async function shareDocument(
  accessToken: string,
  id: string,
  targetUserId: string,
  relation: ShareRelation,
): Promise<void> {
  await createApiClient(accessToken).post(`/api/documents/${id}/share`, { targetUserId, relation });
}

/** The backend's GlobalExceptionHandler returns {"error": "..."} for 400/403/404. */
export function extractErrorMessage(error: unknown): string {
  if (axios.isAxiosError(error)) {
    const body = error.response?.data as { error?: string } | undefined;
    if (body?.error) return body.error;
    if (error.response?.status) return `Request failed (HTTP ${error.response.status})`;
  }
  return 'Request failed.';
}
