import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { apiFetch } from './client';

export type Product = { id: string; name: string; archived: boolean; clientCount: number; releaseCount: number; jiraProjectKeys: string[] };
export type ProductList = { products: Product[] };
export const productsQueryKey = ['products'] as const;

const PRODUCTS_URL = '/api/products';

// Jira's project key shape; advisory only, the server is the authority.
export const JIRA_PROJECT_KEY = /^[A-Z][A-Z0-9_]{1,9}$/;

export function parseJiraKeys(raw: string): string[] {
  return [...new Set(raw.toUpperCase().split(/[\s,]+/).filter((key) => key !== ''))];
}

export function useProducts(enabled = true) {
  return useQuery({
    queryKey: productsQueryKey,
    queryFn: () => apiFetch<ProductList>(PRODUCTS_URL),
    enabled,
  });
}

function useProductMutation<TVars>(request: (vars: TVars) => Promise<ProductList>) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: request,
    onSuccess: (list) => {
      queryClient.setQueryData(productsQueryKey, list);
      // A product name is shown on clients, releases, install history and the dashboard, so all of them go stale.
      for (const key of ['clients', 'client', 'release', 'releases', 'installs', 'dashboard', 'roadmap', 'briefings']) {
        queryClient.invalidateQueries({ queryKey: [key] });
      }
    },
  });
}

function send(path: string, method: string, body?: unknown) {
  return apiFetch<ProductList>(`${PRODUCTS_URL}${path}`, {
    method,
    ...(body === undefined ? {} : { body: JSON.stringify(body) }),
  });
}

export function useAddProduct() {
  return useProductMutation((name: string) => send('', 'POST', { name }));
}

export function useRenameProduct() {
  return useProductMutation(({ id, name }: { id: string; name: string }) => send(`/${id}`, 'PUT', { name }));
}

export function useArchiveProduct() {
  return useProductMutation((id: string) => send(`/${id}/archive`, 'POST'));
}

export function useUnarchiveProduct() {
  return useProductMutation((id: string) => send(`/${id}/unarchive`, 'POST'));
}

export function useSetProductJiraProjects() {
  return useProductMutation(({ id, keys }: { id: string; keys: string[] }) =>
    send(`/${id}/jira-projects`, 'PUT', { keys }),
  );
}
