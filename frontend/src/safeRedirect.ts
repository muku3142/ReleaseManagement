/**
 * Post-login redirect targets come from a query parameter an attacker fully controls, so
 * only same-origin relative paths may be followed. Anything else collapses to "/".
 *
 * Rejects, beyond the obvious absolute URL:
 *  - "//host"  — protocol-relative, resolves to a different origin
 *  - "/\host"  — browsers normalise the backslash to "/", making it protocol-relative
 */
export function safeRedirectTarget(raw: string | null | undefined): string {
  if (!raw || !raw.startsWith('/')) return '/';
  if (raw.startsWith('//') || raw.startsWith('/\\')) return '/';
  return raw;
}
