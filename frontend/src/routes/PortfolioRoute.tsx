import { useState } from 'react';
import { useNavigate, useSearch } from '@tanstack/react-router';
import {
  useAddClient,
  useArchiveClient,
  useClients,
  useRenameClient,
  useUnarchiveClient,
} from '../api/clients';
import {
  useAddProduct,
  useArchiveProduct,
  useProducts,
  useRenameProduct,
  useUnarchiveProduct,
} from '../api/products';
import { ClientDetailPanel } from '../components/ClientDetailPanel';
import { PortfolioRegisterPanel } from '../components/PortfolioRegisterPanel';
import { ProductJiraProjectsEditor } from '../components/ProductJiraProjectsEditor';

type Tab = 'products' | 'clients';
const TABS: { id: Tab; label: string }[] = [
  { id: 'products', label: 'Products' },
  { id: 'clients', label: 'Clients' },
];

function ProductsTab() {
  const products = useProducts();
  const [openJiraEditorId, setOpenJiraEditorId] = useState<string | null>(null);
  const productById = Object.fromEntries((products.data?.products ?? []).map((p) => [p.id, p]));
  return (
    <PortfolioRegisterPanel
      noun="product"
      heading="Products"
      items={(products.data?.products ?? []).map((p) => ({
        id: p.id,
        name: p.name,
        archived: p.archived,
        meta: `${p.clientCount} clients · ${p.releaseCount} releases`,
      }))}
      loading={products.isPending}
      loadError={products.isError ? products.error : null}
      onRetry={() => products.refetch()}
      add={useAddProduct()}
      rename={useRenameProduct()}
      archive={useArchiveProduct()}
      unarchive={useUnarchiveProduct()}
      emptyHeading="No products yet"
      emptyBody="Add your first product above. Releases can then be tagged with the products they cover."
      renderDetails={(item) => (
        <ProductJiraProjectsEditor
          product={productById[item.id]}
          open={openJiraEditorId === item.id}
          onToggle={() => setOpenJiraEditorId((current) => (current === item.id ? null : item.id))}
        />
      )}
    />
  );
}

function ClientsTab({
  clientId,
  onSelect,
  onShowProducts,
}: {
  clientId?: string;
  onSelect: (id: string) => void;
  onShowProducts: () => void;
}) {
  const clients = useClients();
  return (
    <div className="grid grid-cols-1 gap-4 lg:grid-cols-[minmax(0,2fr)_minmax(0,3fr)]">
      <PortfolioRegisterPanel
        noun="client"
        heading="Clients"
        items={(clients.data?.clients ?? []).map((c) => ({
          id: c.id,
          name: c.name,
          archived: c.archived,
          meta: `${c.productIds.length} products`,
        }))}
        loading={clients.isPending}
        loadError={clients.isError ? clients.error : null}
        onRetry={() => clients.refetch()}
        add={useAddClient()}
        rename={useRenameClient()}
        archive={useArchiveClient()}
        unarchive={useUnarchiveClient()}
        selectedId={clientId}
        onSelect={onSelect}
        emptyHeading="No clients yet"
        emptyBody="Add a client above, then link it to the products it runs."
      />
      <ClientDetailPanel clientId={clientId} onShowProducts={onShowProducts} />
    </div>
  );
}

export function PortfolioRoute() {
  const search = useSearch({ from: '/authenticated/portfolio' });
  const navigate = useNavigate();
  const tab: Tab = search.tab ?? 'products';

  function go(next: Tab, client?: string) {
    navigate({ to: '/portfolio', search: { tab: next, client } });
  }

  function onTabKeyDown(event: React.KeyboardEvent, index: number) {
    if (event.key !== 'ArrowRight' && event.key !== 'ArrowLeft') return;
    event.preventDefault();
    const next = TABS[(index + (event.key === 'ArrowRight' ? 1 : TABS.length - 1)) % TABS.length];
    go(next.id, search.client);
    document.getElementById(`portfolio-tab-${next.id}`)?.focus();
  }

  return (
    <div className="space-y-6">
      <div className="bg-white p-6">
        <h1 className="text-base font-semibold text-gray-900">Portfolio</h1>
        <p className="mt-2 text-sm text-gray-600">
          The products you ship and the clients that run them. Record which clients installed a release from that
          release&apos;s page.
        </p>
        <div role="tablist" aria-label="Portfolio" className="mt-4 flex gap-4 border-b border-gray-200">
          {TABS.map((t, index) => {
            const active = t.id === tab;
            return (
              <button
                key={t.id}
                id={`portfolio-tab-${t.id}`}
                type="button"
                role="tab"
                aria-selected={active}
                aria-controls={`portfolio-panel-${t.id}`}
                tabIndex={active ? 0 : -1}
                onClick={() => go(t.id, search.client)}
                onKeyDown={(e) => onTabKeyDown(e, index)}
                className={`px-1 pb-2 text-sm ${
                  active
                    ? '-mb-px border-b-2 border-blue-600 font-semibold text-gray-900'
                    : 'text-gray-600 hover:text-gray-900'
                }`}
              >
                {t.label}
              </button>
            );
          })}
        </div>
      </div>
      <div
        role="tabpanel"
        id={`portfolio-panel-${tab}`}
        aria-labelledby={`portfolio-tab-${tab}`}
      >
        {tab === 'products' ? (
          <ProductsTab />
        ) : (
          <ClientsTab
            clientId={search.client}
            onSelect={(id) => go('clients', id)}
            onShowProducts={() => go('products')}
          />
        )}
      </div>
    </div>
  );
}
