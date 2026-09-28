export function formatDateTime(iso: string): string {
  return new Date(iso).toLocaleString();
}

/** ISO instant -> value for <input type="datetime-local"> in the browser's timezone. */
export function toLocalInputValue(iso: string): string {
  const d = new Date(iso);
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`;
}

/** <input type="datetime-local"> value (browser timezone) -> ISO instant. */
export function fromLocalInputValue(value: string): string {
  return new Date(value).toISOString();
}

export function formatBytes(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`;
}

export function userLabel(user: { username: string; displayName: string | null }): string {
  return user.displayName ? `${user.displayName} (${user.username})` : user.username;
}
