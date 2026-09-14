import axios from 'axios';

const apiBaseUrl = import.meta.env.VITE_API_BASE_URL;

export function createApiClient(accessToken?: string) {
  return axios.create({
    baseURL: apiBaseUrl,
    headers: accessToken ? { Authorization: `Bearer ${accessToken}` } : {},
  });
}

export async function fetchPublicHello() {
  const { data } = await createApiClient().get('/api/public/hello');
  return data;
}

export async function fetchPrivateMe(accessToken: string) {
  const { data } = await createApiClient(accessToken).get('/api/private/me');
  return data;
}
