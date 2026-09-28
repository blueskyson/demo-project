export type UserRole = 'ADMIN' | 'NORMAL_USER';

export interface User {
  id: string;
  username: string;
  email: string | null;
  displayName: string | null;
  role: UserRole;
}

export interface AwardEvent {
  id: number;
  name: string;
  description: string | null;
  deadline: string;
  closed: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface AwardEventRequest {
  name: string;
  description: string | null;
  deadline: string;
}

export interface ProposalSummary {
  id: number;
  awardEventId: number;
  awardEventName: string;
  title: string;
  leader: User;
  memberCount: number;
  fileCount: number;
  editable: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface ProposalFile {
  id: number;
  originalFilename: string;
  contentType: string | null;
  sizeBytes: number;
  uploadedBy: User;
  uploadedAt: string;
}

export interface ProposalDetail {
  id: number;
  awardEvent: AwardEvent;
  title: string;
  description: string | null;
  leader: User;
  members: User[];
  files: ProposalFile[];
  editable: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface CreateProposalRequest {
  awardEventId: number;
  title: string;
  description: string | null;
  leaderId?: string | null;
}

export interface UpdateProposalRequest {
  title: string;
  description: string | null;
  leaderId?: string | null;
}
