import { describe, expect, it } from 'vitest';
import { isAllowedPackagesUrl } from '../../src/components/packagesUrl';

describe('isAllowedPackagesUrl', () => {
  it.each([
    'https://contoso.sharepoint.com/x',
    'https://onedrive.live.com/x',
    'https://1drv.ms/x',
    'https://CONTOSO.SharePoint.com/x',
  ])('accepts %s', (value) => {
    expect(isAllowedPackagesUrl(value)).toBe(true);
  });

  it.each([
    'http://contoso.sharepoint.com/x',
    'https://sharepoint.com/x',
    'https://evil.com/.sharepoint.com',
    'https://user@contoso.sharepoint.com/x',
    'https://contoso.sharepoint.com:8443/x',
    'javascript:alert(1)',
    'not a url',
  ])('rejects %s', (value) => {
    expect(isAllowedPackagesUrl(value)).toBe(false);
  });
});
