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

export async function apiFetch<T>(path: string, init: RequestInit = {}): Promise<T> {
  const method = (init.method ?? 'GET').toUpperCase();
  const headers = new Headers(init.headers);
  if (init.body !== undefined) headers.set('Content-Type', 'application/json');
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
