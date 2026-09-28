import { createContext, useContext } from 'react';
import type { User } from '../api/types';

export const CurrentUserContext = createContext<User | null>(null);

/** The backend's view of the signed-in user (including their app role). */
export function useCurrentUser(): User {
  const user = useContext(CurrentUserContext);
  if (!user) throw new Error('useCurrentUser must be used inside AppLayout');
  return user;
}
