import { Fragment, useState } from 'react';
import { Link, useNavigate, useSearch } from '@tanstack/react-router';
import { Spinner } from '@/components/ui/spinner';
import { ApiError } from '../api/client';
import { useProducts } from '../api/products';
import { messageForReason } from '../api/reasonMessages';
import type { TicketGroup } from '../api/releases';
import { AddToReleaseDialog } from '../components/AddToReleaseDialog';
import { NoJiraProjectNotice } from '../components/NoJiraProjectNotice';
import { TicketBrowser } from '../components/TicketBrowser';

// D-17.2-01: both ways into a new release start here, and both start by choosing a product.
export function TicketsRoute() {
  const search = useSearch({ from: '/authenticated/tickets' });
  const navigate = useNavigate();
  const products = useProducts();
  // The groups are snapshotted when the trigger is pressed rather than tracked live: showModal() makes
  // the rest of the document inert, so the selection cannot change while the dialog is open.
  const [pickedGroups, setPickedGroups] = useState<TicketGroup[] | null>(null);

  const activeProducts = (products.data?.products ?? [])
    .filter((p) => !p.archived)
    .sort((a, b) => a.name.localeCompare(b.name));
  // An unknown or archived id from the URL counts as nothing chosen.
  const product = activeProducts.find((p) => p.id === search.product);
  const productId = product?.id;
  const productsProblem =
    products.isError && products.error instanceof ApiError ? products.error.problem : undefined;

  function chooseProduct(id: string) {
    navigate({
      to: '/tickets',
      search: (prev) => ({ ...prev, product: id === '' ? undefined : id }),
      replace: true,
    });
  }

  return (
    // No max-width cap: the ticket table is the point of this screen and eight columns do not
    // fit in 5xl, which was cutting content off.
    <div className="w-full">
      <h2 className="text-base font-semibold text-gray-900">{search.new ? 'New release' : 'Browse tickets'}</h2>
      {search.new && (
        <p className="mt-1 text-sm text-muted-foreground">
          Pick a product, select its tickets, then name the release.
        </p>
      )}

      <div className="mt-4">
        {products.isPending && (
          <p role="status" className="flex items-center gap-2 text-sm text-muted-foreground">
            <Spinner />
            Loading products…
          </p>
        )}

        {productsProblem && (
          <div>
            <p role="alert" data-reason={productsProblem.reason} className="text-sm text-destructive">
              {messageForReason(productsProblem)}
            </p>
            <button
              type="button"
              onClick={() => products.refetch()}
              className="mt-2 h-9 rounded-md border border-input bg-card px-3 text-sm font-semibold text-foreground"
            >
              Try again
            </button>
          </div>
        )}

        {products.isSuccess && activeProducts.length === 0 && (
          <p className="text-sm text-muted-foreground">
            No products yet. Add one in{' '}
            <Link to="/portfolio" search={{ tab: 'products' }} className="text-primary underline underline-offset-4">
              Portfolio › Products
            </Link>
            .
          </p>
        )}

        {products.isSuccess && activeProducts.length > 0 && (
          <div className="space-y-2">
            <label htmlFor="productId" className="block text-sm font-semibold text-foreground">
              Product
            </label>
            <select
              id="productId"
              value={productId ?? ''}
              onChange={(event) => chooseProduct(event.target.value)}
              aria-describedby={product && product.jiraProjectKeys.length > 0 ? 'productScope' : undefined}
              className="w-64 h-9 rounded-md border border-input bg-card px-3 text-sm text-foreground"
            >
              <option value="">Select a product…</option>
              {activeProducts.map((p) => (
                <option key={p.id} value={p.id}>
                  {p.name}
                </option>
              ))}
            </select>
            {product && product.jiraProjectKeys.length > 0 && (
              <p id="productScope" className="mt-1 text-xs text-muted-foreground">
                Searches{' '}
                {product.jiraProjectKeys.map((key, index) => (
                  <Fragment key={key}>
                    {index > 0 && ', '}
                    <span className="font-mono">{key}</span>
                  </Fragment>
                ))}
              </p>
            )}
          </div>
        )}
      </div>

      {products.isSuccess && activeProducts.length > 0 && !product && (
        <p className="mt-4 text-sm text-muted-foreground">Choose a product to browse its tickets.</p>
      )}

      {product && product.jiraProjectKeys.length === 0 && (
        <div className="mt-4">
          <NoJiraProjectNotice productName={product.name} />
        </div>
      )}

      {product && productId && product.jiraProjectKeys.length > 0 && (
        <div className="mt-4">
          {/* Keyed so a product change clears the loaded tickets and the selection. */}
          <TicketBrowser
            key={productId}
            productId={productId}
            offerHideReleased
            offerHideRelease
            actionLabel={search.new ? 'Create release' : 'Add to release'}
            onAction={(_, groups) => setPickedGroups(groups)}
          />
        </div>
      )}

      {product && product.jiraProjectKeys.length > 0 && (
        <AddToReleaseDialog
          open={pickedGroups !== null}
          groups={pickedGroups ?? []}
          productId={product.id}
          productName={product.name}
          startWithForm={search.new === true}
          onClose={() => setPickedGroups(null)}
        />
      )}
    </div>
  );
}
