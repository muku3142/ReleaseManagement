import { useEffect, useMemo, useState } from 'react';
import { Link, useNavigate } from '@tanstack/react-router';
import { createColumnHelper } from '@tanstack/react-table';
import { Lock, Plus } from 'lucide-react';
import { ApiError } from '../api/client';
import { messageForReason } from '../api/reasonMessages';
import { useReleases, type ReleaseSummary } from '../api/releases';
import { readinessIndex, useDocumentReadiness } from '../api/documents';
import { DOCUMENT_TYPES, DOCUMENT_TYPE_LABELS } from '../api/templates';
import { ReadinessPill } from '../components/ReadinessPill';
import { PageContainer } from '@/components/layout/PageContainer';
import { PageHeader } from '@/components/layout/PageHeader';
import { DataTable } from '@/components/data-table/DataTable';
import { DataTableColumnHeader } from '@/components/data-table/DataTableColumnHeader';
import type { DataTableFeatures } from '@/components/data-table/features';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Empty, EmptyDescription, EmptyHeader, EmptyTitle } from '@/components/ui/empty';
import { Input } from '@/components/ui/input';

const helper = createColumnHelper<DataTableFeatures, ReleaseSummary>();

export function ReleasesRoute() {
  const [search, setSearch] = useState('');
  const [query, setQuery] = useState('');

  // Local-DB search is cheap, unlike Phase 2's Jira search, so it runs as you type rather than
  // behind a Load button. 300ms keeps a fast typist to one request per pause.
  useEffect(() => {
    const timer = setTimeout(() => setQuery(search.trim()), 300);
    return () => clearTimeout(timer);
  }, [search]);

  const navigate = useNavigate();
  const releases = useReleases(query);
  const problem = releases.isError && releases.error instanceof ApiError ? releases.error.problem : undefined;
  // DOC-09: independent of the releases query on purpose - a readiness failure must not blank the
  // whole release table (T-05-60), so its own isError branch only affects the three readiness cells below.
  const readiness = useDocumentReadiness();
  const stateOf = useMemo(() => readinessIndex(readiness.data?.entries), [readiness.data]);

  const columns = useMemo(
    () =>
      helper.columns([
        helper.accessor((r) => `${r.name} ${r.version}`, {
          id: 'release',
          header: ({ column }) => <DataTableColumnHeader column={column} title="Release" />,
          sortFn: 'alphanumeric',
          enableHiding: false,
          meta: { label: 'Release' },
          cell: ({ row: { original: r } }) => (
            <Link
              to="/releases/$releaseId"
              params={{ releaseId: r.id }}
              className="font-semibold whitespace-nowrap text-primary link-underline"
            >
              {r.name} {r.version}
            </Link>
          ),
        }),
        helper.display({
          id: 'products',
          header: 'Products',
          meta: { label: 'Products' },
          cell: ({ row: { original: r } }) =>
            r.products.length === 0 ? (
              <span className="text-muted-foreground">Unassigned</span>
            ) : (
              <span className="flex items-center gap-1" title={r.products.map((p) => p.name).join(', ')}>
                {r.products.slice(0, 2).map((p) => (
                  <Badge key={p.id} variant="outline">
                    {p.name}
                  </Badge>
                ))}
                {r.products.length > 2 && (
                  <small className="text-xs text-muted-foreground">+{r.products.length - 2}</small>
                )}
              </span>
            ),
        }),
        helper.accessor('status', {
          header: ({ column }) => <DataTableColumnHeader column={column} title="Status" />,
          filterFn: 'arrHas',
          meta: { label: 'Status' },
          cell: ({ getValue }) =>
            getValue() === 'FROZEN' ? (
              <Badge variant="neutral">
                <Lock aria-hidden="true" />
                Frozen
              </Badge>
            ) : (
              <Badge variant="outline">Open</Badge>
            ),
        }),
        helper.accessor((r) => r.jiraProjectKey ?? '', {
          id: 'jiraProjectKey',
          header: ({ column }) => <DataTableColumnHeader column={column} title="Jira project" />,
          sortFn: 'text',
          meta: { label: 'Jira project' },
          cell: ({ getValue }) => getValue() || '—',
        }),
        helper.accessor('itemCount', {
          header: ({ column }) => <DataTableColumnHeader column={column} title="Items" />,
          meta: { label: 'Items', headerClassName: 'text-right', cellClassName: 'text-right tabular-nums' },
        }),
        helper.accessor((r) => r.lastRefreshedAt ?? '', {
          id: 'lastRefreshedAt',
          header: ({ column }) => <DataTableColumnHeader column={column} title="Last refreshed" />,
          sortFn: 'text',
          meta: { label: 'Last refreshed', cellClassName: 'whitespace-nowrap' },
          cell: ({ getValue }) => (getValue() ? new Date(getValue()).toLocaleString() : '—'),
        }),
        ...DOCUMENT_TYPES.map((type) =>
          helper.display({
            id: type,
            header: DOCUMENT_TYPE_LABELS[type],
            meta: { label: DOCUMENT_TYPE_LABELS[type] },
            cell: ({ row: { original: r } }) =>
              readiness.isError ? (
                <span className="text-muted-foreground" aria-label="Readiness unavailable">
                  —
                </span>
              ) : (
                <ReadinessPill state={stateOf(r.id, type) ?? 'NOT_GENERATED'} releaseId={r.id} documentType={type} />
              ),
          }),
        ),
      ]),
    [readiness.isError, stateOf],
  );

  const emptyState = (
    <Empty>
      {query === '' ? (
        <EmptyHeader>
          <EmptyTitle>No releases yet</EmptyTitle>
          <EmptyDescription>Choose New release, pick a product and select its tickets.</EmptyDescription>
        </EmptyHeader>
      ) : (
        <EmptyHeader>
          <EmptyTitle>No releases match &apos;{query}&apos;</EmptyTitle>
          <EmptyDescription>Try a different name or version.</EmptyDescription>
        </EmptyHeader>
      )}
    </Empty>
  );

  return (
    <PageContainer>
      <PageHeader
        title="Releases"
        description="Every release, its status and its documents."
        actions={
          <Button asChild>
            <Link to="/tickets" search={{ new: true }}>
              <Plus aria-hidden="true" />
              New release
            </Link>
          </Button>
        }
      />

      {problem && (
        <div className="mb-4">
          <p role="alert" data-reason={problem.reason} className="text-sm text-destructive">
            {messageForReason(problem)}
          </p>
          <Button variant="outline" size="sm" className="mt-2" onClick={() => releases.refetch()}>
            Try again
          </Button>
        </div>
      )}

      <DataTable
        caption="Releases"
        noun="releases"
        columns={columns}
        data={releases.data?.releases ?? []}
        getRowId={(r) => r.id}
        onRowClick={(r) => navigate({ to: '/releases/$releaseId', params: { releaseId: r.id } })}
        stickyFirstColumn
        isLoading={releases.isPending}
        facetedFilters={[
          {
            columnId: 'status',
            title: 'Status',
            options: [
              { value: 'OPEN', label: 'Open' },
              { value: 'FROZEN', label: 'Frozen', icon: Lock },
            ],
          },
        ]}
        toolbar={
          <Input
            type="search"
            value={search}
            onChange={(event) => setSearch(event.target.value)}
            placeholder="Search by name or version…"
            aria-label="Search releases"
            className="h-8 w-64"
          />
        }
        emptyState={problem ? undefined : emptyState}
      />
    </PageContainer>
  );
}
