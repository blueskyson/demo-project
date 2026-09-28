import { Badge } from '@mantine/core';
import type { AwardEvent } from '../api/types';

export function DeadlineBadge({ event }: { event: AwardEvent }) {
  return event.closed ? <Badge color="gray">closed</Badge> : <Badge color="green">open</Badge>;
}
