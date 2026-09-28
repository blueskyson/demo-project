import { useCallback, useEffect, useState } from 'react';
import { errorMessage } from '../api/client';

/**
 * Runs `load` on mount and whenever it changes (wrap it in useCallback), and exposes `reload`
 * for refreshing after mutations.
 */
export function useAsync<T>(load: () => Promise<T>) {
  const [data, setData] = useState<T | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [version, setVersion] = useState(0);

  useEffect(() => {
    let cancelled = false;
    load()
      .then((result) => {
        if (!cancelled) {
          setData(result);
          setError(null);
        }
      })
      .catch((e: unknown) => {
        if (!cancelled) setError(errorMessage(e));
      });
    return () => {
      cancelled = true;
    };
  }, [load, version]);

  const reload = useCallback(() => setVersion((v) => v + 1), []);
  return { data, setData, error, loading: data === null && error === null, reload };
}
