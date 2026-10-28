import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { apiFetch } from './client';

export type ProductBundle = { id: string; name: string; ticketKeys: string[] };
type ProductBundleList = { bundles: ProductBundle[] };

export const productBundlesQueryKey = (productId: string | undefined) => ['productBundles', productId] as const;

const bundlesUrl = (productId: string) => `/api/products/${productId}/bundles`;

export function useProductBundles(productId: string | undefined) {
  return useQuery({
    queryKey: productBundlesQueryKey(productId),
    queryFn: () => apiFetch<ProductBundleList>(bundlesUrl(productId!)),
    enabled: productId !== undefined,
  });
}

function useBundleMutation<TVars>(
  productId: string | undefined,
  request: (vars: TVars) => Promise<ProductBundleList>,
) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: request,
    onSuccess: (list) => queryClient.setQueryData(productBundlesQueryKey(productId), list),
  });
}

export function useSaveProductBundle(productId: string) {
  return useBundleMutation(productId, (vars: { name: string; ticketKeys: string[] }) =>
    apiFetch<ProductBundleList>(bundlesUrl(productId), { method: 'POST', body: JSON.stringify(vars) }),
  );
}

// Takes undefined so a browser without a product can still call the hook; it only fires with one.
export function useDeleteProductBundle(productId: string | undefined) {
  return useBundleMutation(productId, (bundleId: string) =>
    apiFetch<ProductBundleList>(`${bundlesUrl(productId!)}/${bundleId}`, { method: 'DELETE' }),
  );
}
