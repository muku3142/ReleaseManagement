const ONEDRIVE_HOST = 'onedrive.live.com';
const SHORT_HOST = '1drv.ms';
const SHAREPOINT_SUFFIX = '.sharepoint.com';

// Advisory mirror of the server's PackagesUrl.normalize rule; the server stays the authority.
export function isAllowedPackagesUrl(value: string): boolean {
  let url: URL;
  try {
    url = new URL(value);
  } catch {
    return false;
  }
  const host = url.hostname.toLowerCase();
  return (
    url.protocol === 'https:' &&
    url.username === '' &&
    url.password === '' &&
    (url.port === '' || url.port === '443') &&
    (host === ONEDRIVE_HOST ||
      host === SHORT_HOST ||
      (host.endsWith(SHAREPOINT_SUFFIX) && host.length > SHAREPOINT_SUFFIX.length))
  );
}
