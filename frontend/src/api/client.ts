import axios from 'axios';
import { keycloak } from '../auth/keycloak';

const apiBaseUrl = import.meta.env.VITE_API_BASE_URL;

export function createApiClient() {
  const client = axios.create({ baseURL: apiBaseUrl });

  client.interceptors.request.use(async (config) => {
    if (keycloak.authenticated) {
      // Proactively refresh if the token is expiring within 30s, same threshold as the
      // onTokenExpired handler — this covers the gap between the event firing and a
      // request already in flight.
      await keycloak.updateToken(30).catch(() => undefined);
      config.headers.Authorization = `Bearer ${keycloak.token}`;
    }
    return config;
  });

  return client;
}

export async function fetchPublicHello() {
  const { data } = await createApiClient().get('/api/public/hello');
  return data;
}

export async function fetchPrivateMe() {
  const { data } = await createApiClient().get('/api/private/me');
  return data;
}
