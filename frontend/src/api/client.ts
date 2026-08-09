import { markCredentialInvalid as markJiraCredentialInvalid } from './credentialStatus';

export type ProblemDetail = {
  type?: string;
  title?: string;
  status: number;
  detail?: string;
  reason: string;
  fieldErrors?: Record<string, string>;
};

export class ApiError extends Error {
  constructor(readonly problem: ProblemDetail) {
    super(problem.detail ?? problem.title ?? 'Request failed');
  }
}

function readCookie(name: string): string | null {
  const match = document.cookie.match(new RegExp('(^|; )' + name + '=([^;]*)'));
  return match ? decodeURIComponent(match[2]) : null;
}

async function rawFetch<T>(path: string, init: RequestInit = {}): Promise<T> {
  const method = (init.method ?? 'GET').toUpperCase();
  const headers = new Headers(init.headers);
  // A FormData body carries its own multipart/form-data content type with a generated boundary.
  // Overwriting it with application/json makes the server unable to parse the upload at all.
  if (init.body !== undefined && !(init.body instanceof FormData)) {
    headers.set('Content-Type', 'application/json');
  }
  if (method !== 'GET' && method !== 'HEAD') {
    const csrf = readCookie('XSRF-TOKEN');
    if (csrf) headers.set('X-XSRF-TOKEN', csrf);
  }
  const response = await fetch(path, { ...init, headers, credentials: 'same-origin' });
  if (response.status === 204) return undefined as T;
  if (!response.ok) {
    const problem = await response.json().catch(() => ({ status: response.status, reason: 'UNKNOWN' }));
    throw new ApiError({ ...problem, status: response.status, reason: problem.reason ?? 'UNKNOWN' });
  }
  return (await response.json()) as T;
}

const SESSION_REASONS = new Set(['UNAUTHENTICATED', 'SESSION_EXPIRED', 'INVALID_SESSION']);
const REFRESH_PATH = '/api/auth/refresh';
let refreshInFlight: Promise<boolean> | null = null;

async function attemptRefresh(): Promise<boolean> {
  refreshInFlight ??= (async () => {
    try {
      await rawFetch(REFRESH_PATH, { method: 'POST' });
      return true;
    } catch {
      return false;
    } finally {
      refreshInFlight = null;
    }
  })();
  return refreshInFlight;
}

// The single HTTP boundary every SPA request goes through: same-origin credentials,
// XSRF-TOKEN echo on writes, and (additive to plan 02) a single-flight silent
// refresh-then-retry on a session-reason 401, and a credential-invalid signal on a
// rejected Jira credential (D-04/D-06/D-07).
export async function apiFetch<T>(path: string, init: RequestInit = {}, retried = false): Promise<T> {
  try {
    return await rawFetch<T>(path, init);
  } catch (error) {
    if (error instanceof ApiError && SESSION_REASONS.has(error.problem.reason) && path !== REFRESH_PATH && !retried) {
      const refreshed = await attemptRefresh();
      if (refreshed) return apiFetch<T>(path, init, true);
      throw error;
    }
    if (error instanceof ApiError && error.problem.reason === 'INVALID_CREDENTIALS') {
      markJiraCredentialInvalid();
      throw error;
    }
    throw error;
  }
}
