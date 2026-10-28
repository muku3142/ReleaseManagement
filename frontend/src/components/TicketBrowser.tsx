import { Fragment, useEffect, useRef, useState } from 'react';
import { Link } from '@tanstack/react-router';
import { useJiraProjects, useJiraSprints } from '../api/jiraProjects';
import { useProducts } from '../api/products';
import { useDeleteProductBundle, useProductBundles, type ProductBundle } from '../api/productBundles';
import { groupKeys, useReleases, type TicketGroup } from '../api/releases';
import { ApiError } from '../api/client';
import { messageForReason } from '../api/reasonMessages';
import { AddToBundleDialog } from './AddToBundleDialog';
import { CodeLinksDetail } from './CodeLinks';
import {
  DEFAULT_STATUS_CATEGORIES,
  STATUS_CATEGORIES,
  STATUS_CATEGORY_LABELS,
  useTicketCodeLinks,
  useTicketSearch,
  type StatusCategory,
  type Ticket,
  type TicketSearchCriteria,
} from '../api/tickets';

function isControl(target: EventTarget) {
  return (target as HTMLElement).closest('button, a, input, label') !== null;
}

// A product bundle as it shows in the current results: only the members that were loaded.
type BundleRowData = { bundle: ProductBundle; members: Ticket[]; missing: string[] };

const BUNDLE_EXPAND_PREFIX = 'bundle:';

function BundleRow(props: {
  row: BundleRowData;
  selected: boolean;
  expanded: boolean;
  deleting: boolean;
  onToggleSelect: () => void;
  onToggleExpand: () => void;
  onDelete: () => void;
}) {
  const { bundle, members, missing } = props.row;
  const count =
    missing.length === 0
      ? `${members.length} tickets`
      : `${members.length} of ${members.length + missing.length} tickets`;
  return (
    <>
      <tr
        className="cursor-pointer border-b border-gray-100 bg-gray-50/60 hover:bg-gray-50"
        onClick={(event) => {
          if (isControl(event.target)) return;
          if (event.detail === 1 && window.getSelection()?.toString()) return;
          props.onToggleSelect();
        }}
        onDoubleClick={(event) => {
          if (isControl(event.target)) return;
          window.getSelection()?.removeAllRanges();
          props.onToggleExpand();
        }}
      >
        <td className="p-0">
          <label className="flex cursor-pointer items-center px-4 py-3">
            <input
              type="checkbox"
              checked={props.selected}
              onChange={props.onToggleSelect}
              aria-label={`Select bundle ${bundle.name}`}
              className="h-5 w-5 cursor-pointer rounded border-gray-300 text-blue-600 focus-visible:ring-2 focus-visible:ring-blue-600"
            />
          </label>
        </td>
        <td className="whitespace-nowrap px-4 py-2 font-bold text-gray-900">{bundle.name}</td>
        <td colSpan={6} className="px-4 py-2 text-gray-700">
          {count}
        </td>
        <td className="whitespace-nowrap px-4 py-2">
          <button
            type="button"
            onClick={props.onToggleExpand}
            aria-expanded={props.expanded}
            aria-label={props.expanded ? 'Collapse bundle' : 'Expand bundle'}
            className="rounded px-2 text-sm text-gray-600 hover:text-gray-900 focus-visible:ring-2 focus-visible:ring-blue-600"
          >
            {props.expanded ? '▾' : '▸'}
          </button>
          <button
            type="button"
            onClick={props.onDelete}
            disabled={props.deleting}
            className="ml-2 rounded border border-gray-300 px-2 py-0.5 text-sm text-gray-700 disabled:opacity-50"
          >
            Delete bundle
          </button>
        </td>
      </tr>
      {props.expanded && (
        <tr>
          <td colSpan={9} className="px-4 pb-4">
            <ul className="space-y-1 rounded border border-gray-200 bg-gray-50 p-4 text-sm text-gray-700">
              {members.map((ticket) => (
                <li key={ticket.key}>
                  <span className="font-semibold text-gray-900">{ticket.key}</span> {ticket.summary}{' '}
                  <span className="text-gray-500">
                    ({ticket.issueType}, {ticket.status})
                  </span>
                </li>
              ))}
              {missing.map((key) => (
                <li key={key} className="text-gray-500">
                  <span className="font-semibold">{key}</span> not in these results
                </li>
              ))}
            </ul>
          </td>
        </tr>
      )}
    </>
  );
}

function TicketCodeLinks({ ticketKey }: { ticketKey: string }) {
  const lookup = useTicketCodeLinks();
  const problem =
    lookup.isError && lookup.error instanceof ApiError ? lookup.error.problem : undefined;

  if (lookup.data) {
    return <CodeLinksDetail links={lookup.data} />;
  }
  return (
    <>
      <button
        type="button"
        disabled={lookup.isPending}
        onClick={() => lookup.mutate(ticketKey)}
        className="rounded border border-gray-300 px-3 py-1.5 text-sm font-semibold text-gray-700 disabled:opacity-50"
      >
        {lookup.isPending ? 'Looking up…' : 'Show linked commits'}
      </button>
      {lookup.isError && (
        <p role="alert" data-reason={problem?.reason} className="mt-2 text-sm text-red-600">
          {problem ? messageForReason(problem) : 'Could not look up linked commits.'}
        </p>
      )}
    </>
  );
}

export function TicketBrowser(props: {
  actionLabel: string;
  onAction: (ticketKeys: string[], groups: TicketGroup[]) => void;
  releaseId?: string;
  productId?: string;
  // D-17.2-06: only where tickets are picked for a release; the roadmap browser leaves it off.
  offerHideReleased?: boolean;
  // Tickets page only: pick one of the product's releases whose tickets are left out.
  offerHideRelease?: boolean;
}) {
  const [projectId, setProjectId] = useState('');
  const [sprintId, setSprintId] = useState('');
  const [statusCategories, setStatusCategories] = useState<StatusCategory[]>(DEFAULT_STATUS_CATEGORIES);
  const [loaded, setLoaded] = useState<Ticket[] | null>(null);
  const [selected, setSelected] = useState<ReadonlySet<string>>(new Set());
  const [selectedBundles, setSelectedBundles] = useState<ReadonlySet<string>>(new Set());
  const [bundleDialogKeys, setBundleDialogKeys] = useState<string[] | null>(null);
  // A ticket key, or BUNDLE_EXPAND_PREFIX + bundle id, so only one row is open at a time.
  const [expandedKey, setExpandedKey] = useState<string | null>(null);
  const [jqlOpen, setJqlOpen] = useState(false);
  const [jql, setJql] = useState('');
  // Which mode produced the current `loaded` value. Deliberately not the live jqlOpen flag,
  // which the user may have toggled since the search ran.
  const [loadedMode, setLoadedMode] = useState<'project' | 'jql' | null>(null);
  const [hideReleased, setHideReleased] = useState(true);
  // Like loadedMode: the flag of the search that produced `loaded`, not the live checkbox.
  const [loadedHidReleased, setLoadedHidReleased] = useState(false);
  const [hiddenReleaseId, setHiddenReleaseId] = useState('');
  const [loadedHiddenRelease, setLoadedHiddenRelease] = useState<string | null>(null);

  const projects = useJiraProjects();
  const sprints = useJiraSprints(projectId);
  const search = useTicketSearch(props.releaseId);
  const productMode = props.productId !== undefined;
  const products = useProducts(productMode);
  const product = products.data?.products.find((p) => p.id === props.productId);
  const offerHideRelease = props.offerHideRelease === true && productMode;
  const releases = useReleases('', offerHideRelease);
  const productReleases =
    releases.data?.releases.filter((r) => r.products.some((p) => p.id === props.productId)) ?? [];
  const hideAnyRelease = props.offerHideReleased === true && hideReleased;
  const mappedKeys = product?.jiraProjectKeys ?? [];
  // D-17.2-03: with no single project picked, the browser searches every project the product maps.
  const allProductProjects = productMode && projectId === '';
  const projectOptions = productMode
    ? projects.data?.filter((p) => mappedKeys.includes(p.key))
    : projects.data;
  // A mapped key Jira doesn't list may be a typo or a permission gap; Jira may still answer, so
  // this warns rather than blocks.
  const unknownKeys = projects.data
    ? mappedKeys.filter((key) => !projects.data.some((p) => p.key === key))
    : [];

  const bundlesQuery = useProductBundles(props.productId);
  const deleteBundle = useDeleteProductBundle(props.productId);

  // Bundle rows cover only the members present in the current results (K-05); every loaded ticket
  // that is in a bundle is shown under it rather than as a loose row.
  const loadedByKey = new Map((loaded ?? []).map((t) => [t.key, t]));
  const bundleRows: BundleRowData[] = (bundlesQuery.data?.bundles ?? [])
    .map((bundle) => ({
      bundle,
      members: bundle.ticketKeys.flatMap((key) => loadedByKey.get(key) ?? []),
      missing: bundle.ticketKeys.filter((key) => !loadedByKey.has(key)),
    }))
    .filter((row) => row.members.length > 0)
    .sort((a, b) => a.bundle.name.localeCompare(b.bundle.name, undefined, { sensitivity: 'base' }));
  const bundledKeys = new Set(bundleRows.flatMap((row) => row.members.map((t) => t.key)));
  const looseTickets = (loaded ?? []).filter((t) => !bundledKeys.has(t.key));
  const looseSelected = looseTickets.filter((t) => selected.has(t.key)).map((t) => t.key);
  const selectedBundleRows = bundleRows.filter((row) => selectedBundles.has(row.bundle.id));
  const bundleSelectedKeys = selectedBundleRows.flatMap((row) => row.members.map((t) => t.key));
  const selectedCount = looseSelected.length + bundleSelectedKeys.length;

  const selectAllRef = useRef<HTMLInputElement>(null);
  const rowCount = looseTickets.length + bundleRows.length;
  const selectedRowCount = looseSelected.length + selectedBundleRows.length;
  const allSelected = rowCount > 0 && selectedRowCount === rowCount;
  const someSelected = selectedRowCount > 0 && !allSelected;

  // indeterminate is a DOM property, not an HTML attribute, so it cannot be set as a JSX prop.
  useEffect(() => {
    if (selectAllRef.current) selectAllRef.current.indeterminate = someSelected;
  }, [someSelected]);

  function handleProjectChange(event: React.ChangeEvent<HTMLSelectElement>) {
    setProjectId(event.target.value);
    setSprintId('');
  }

  function runSearch() {
    const usingJql = jqlOpen && jql.trim() !== '';
    // Exactly one mode is ever sent: the backend rejects a body carrying both with
    // VALIDATION_FAILED, and the discriminated union makes that unrepresentable here.
    const criteria: TicketSearchCriteria = usingJql
      ? { mode: 'jql', jql: jql.trim() }
      : allProductProjects
        ? { mode: 'product', productId: props.productId!, statusCategories }
        : { mode: 'project', projectId, sprintId: sprintId || null, statusCategories };
    const hideAlreadyInRelease = hideAnyRelease;
    // Hiding every release already covers the picked one, so it is only sent on its own.
    const hiddenRelease = offerHideRelease && !hideAnyRelease
      ? productReleases.find((r) => r.id === hiddenReleaseId)
      : undefined;
    search.mutate({ criteria, hideAlreadyInRelease, hiddenReleaseId: hiddenRelease?.id }, {
      onSuccess: (data) => {
        setLoaded(data.tickets);
        // A checkbox pointing at a ticket no longer on screen would misreport what is selected.
        setSelected(new Set());
        setSelectedBundles(new Set());
        setExpandedKey(null);
        setLoadedMode(usingJql ? 'jql' : 'project');
        setLoadedHidReleased(hideAlreadyInRelease);
        setLoadedHiddenRelease(hiddenRelease ? `${hiddenRelease.name} ${hiddenRelease.version}` : null);
      },
    });
  }

  function toggle(key: string) {
    setSelected((prev) => {
      const next = new Set(prev);
      if (next.has(key)) next.delete(key);
      else next.add(key);
      return next;
    });
  }

  function toggleBundle(id: string) {
    setSelectedBundles((prev) => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });
  }

  function toggleExpanded(key: string) {
    setExpandedKey((current) => (current === key ? null : key));
  }

  function selectAll() {
    setSelected(allSelected ? new Set() : new Set(looseTickets.map((t) => t.key)));
    setSelectedBundles(allSelected ? new Set() : new Set(bundleRows.map((row) => row.bundle.id)));
  }

  // One group for the loose tickets, then one per selected bundle, so each bundle keeps its name.
  function addToRelease() {
    const groups: TicketGroup[] = [
      ...(looseSelected.length > 0 ? [{ ticketKeys: looseSelected }] : []),
      ...selectedBundleRows.map((row) => ({
        ticketKeys: row.members.map((t) => t.key),
        bundle: row.bundle.name,
      })),
    ];
    props.onAction(groupKeys(groups), groups);
  }

  const projectsProblem =
    projects.isError && projects.error instanceof ApiError ? projects.error.problem : undefined;
  const sprintsProblem =
    sprints.isError && sprints.error instanceof ApiError ? sprints.error.problem : undefined;
  const searchProblem =
    search.isError && search.error instanceof ApiError ? search.error.problem : undefined;
  const bundlesProblem =
    bundlesQuery.isError && bundlesQuery.error instanceof ApiError ? bundlesQuery.error.problem : undefined;
  const deleteProblem =
    deleteBundle.isError && deleteBundle.error instanceof ApiError ? deleteBundle.error.problem : undefined;

  // A project with no scrum board has no sprints, which is normal for a Kanban or business
  // project — the picker says so rather than sitting empty and looking like a failed load.
  const hasNoSprints = projectId !== '' && sprints.isSuccess && (sprints.data?.length ?? 0) === 0;

  const selectedProjectKey =
    projects.data?.find((p) => p.id === projectId)?.key ?? 'the selected project';
  const selectedSprint = sprints.data?.find((s) => s.id === sprintId)?.name;
  const scopeDescription = allProductProjects
    ? `${product?.name ?? 'this product'} (${mappedKeys.join(', ')})`
    : selectedSprint
      ? `${selectedProjectKey} / ${selectedSprint}`
      : selectedProjectKey;
  // With the Done-only default, the most likely cause of an empty result is that the sprint's
  // work is not finished yet — so the message names the active status filter rather than sending
  // the release manager hunting the wrong one.
  const statusHint =
    statusCategories.length === 0 || statusCategories.length === STATUS_CATEGORIES.length
      ? ''
      : ` with status ${statusCategories.map((c) => STATUS_CATEGORY_LABELS[c]).join(' or ')}`;
  // The release-scope clause is a subset of the any-release one, so only the wider one is named.
  const hiddenHint = loadedHidReleased
    ? ', or every match is already in a release'
    : loadedHiddenRelease !== null
      ? `, or every match is in ${loadedHiddenRelease}`
      : props.releaseId !== undefined
      ? ', or every match is already on this release'
      : '';

  return (
    <>
      {projects.isPending && <p className="mt-2 text-sm text-gray-600">Loading projects…</p>}

      <div className="mt-4 flex flex-wrap items-end gap-3">
        <div>
          <label htmlFor="projectId" className="block text-sm font-semibold text-gray-700">
            Project
          </label>
          <select
            id="projectId"
            value={projectId}
            onChange={handleProjectChange}
            disabled={search.isPending}
            className="rounded border border-gray-300 px-3 py-2 text-sm"
          >
            <option value="">
              {productMode ? `All ${product ? `${product.name} ` : ''}projects` : 'Select a project…'}
            </option>
            {projectOptions?.map((p) => (
              <option key={p.id} value={p.id}>
                {p.key} — {p.name}
              </option>
            ))}
          </select>
        </div>

        <div>
          <label htmlFor="sprintId" className="block text-sm font-semibold text-gray-700">
            Sprint <span className="font-normal text-gray-500">(optional)</span>
          </label>
          <select
            id="sprintId"
            value={sprintId}
            onChange={(event) => setSprintId(event.target.value)}
            disabled={projectId === '' || sprints.isPending || search.isPending || hasNoSprints}
            className="rounded border border-gray-300 px-3 py-2 text-sm"
          >
            <option value="">
              {allProductProjects
                ? 'Pick one project to filter by sprint'
                : hasNoSprints
                  ? 'No sprints in this project'
                  : 'All sprints'}
            </option>
            {sprints.data?.map((s) => (
              <option key={s.id} value={s.id}>
                {s.name} ({s.state})
              </option>
            ))}
          </select>
        </div>

        <fieldset className="flex items-center gap-3" disabled={search.isPending}>
          <legend className="text-sm font-semibold text-gray-900">Status</legend>
          {STATUS_CATEGORIES.map((category) => (
            <label key={category} className="flex items-center gap-1.5 text-sm text-gray-600">
              <input
                type="checkbox"
                checked={statusCategories.includes(category)}
                onChange={(event) =>
                  setStatusCategories((current) =>
                    event.target.checked
                      ? [...current, category]
                      : current.filter((c) => c !== category),
                  )
                }
                className="h-4 w-4 rounded border-gray-300 text-blue-600 focus-visible:ring-2 focus-visible:ring-blue-600"
              />
              {STATUS_CATEGORY_LABELS[category]}
            </label>
          ))}
        </fieldset>

        {props.offerHideReleased && (
          <label className="flex items-center gap-1.5 text-sm text-foreground">
            <input
              type="checkbox"
              checked={hideReleased}
              onChange={(event) => setHideReleased(event.target.checked)}
              disabled={search.isPending}
              className="size-4 accent-primary"
            />
            Hide tickets already in a release
          </label>
        )}

        {offerHideRelease && (
          <div>
            <label htmlFor="hideReleaseId" className="block text-sm font-semibold text-gray-700">
              Hide tickets in release
            </label>
            <select
              id="hideReleaseId"
              value={hiddenReleaseId}
              onChange={(event) => setHiddenReleaseId(event.target.value)}
              disabled={search.isPending || hideAnyRelease}
              title={hideAnyRelease ? 'Tickets in every release are already hidden' : undefined}
              className="rounded border border-gray-300 px-3 py-2 text-sm"
            >
              <option value="">None</option>
              {productReleases.map((r) => (
                <option key={r.id} value={r.id}>
                  {r.name} {r.version}
                </option>
              ))}
            </select>
          </div>
        )}

        <button
          type="button"
          onClick={runSearch}
          disabled={
            search.isPending || (jqlOpen && jql.trim() !== '' ? false : projectId === '' && !productMode)
          }
          className="rounded bg-blue-600 px-4 py-2 text-sm font-semibold text-white disabled:opacity-50"
        >
          Load tickets
        </button>
      </div>

      {unknownKeys.map((key) => (
        <p key={key} className="mt-2 text-sm text-warning">
          {key} isn't a Jira project you can see. Check the mapping in{' '}
          <Link to="/portfolio" search={{ tab: 'products' }} className="text-primary underline underline-offset-4">
            Portfolio › Products
          </Link>
          .
        </p>
      ))}

      <button
        type="button"
        onClick={() => setJqlOpen((open) => !open)}
        aria-expanded={jqlOpen}
        className="mt-3 text-sm text-blue-600 underline"
      >
        Advanced: edit as JQL
      </button>

      {jqlOpen && (
        <div className="mt-2">
          <label htmlFor="jql" className="block text-sm font-semibold text-gray-700">
            JQL
          </label>
          <textarea
            id="jql"
            rows={3}
            maxLength={2000}
            value={jql}
            onChange={(event) => setJql(event.target.value)}
            disabled={search.isPending}
            aria-describedby={productMode ? 'jql-scope-helper' : undefined}
            placeholder="project = CNV AND sprint = 42"
            className="mt-1 w-full rounded border border-gray-300 px-3 py-2 font-mono text-sm"
          />
          <p className="mt-1 text-sm text-gray-600">
            While JQL is entered, the project and sprint pickers are ignored for this search.
          </p>
          {productMode && (
            <p id="jql-scope-helper" className="mt-1 text-sm text-muted-foreground">
              Advanced JQL searches every project you can see, not only {product?.name ?? 'this product'}'s.
            </p>
          )}
        </div>
      )}

      {props.releaseId !== undefined && (
        <p className="mt-3 text-sm text-gray-600">Tickets already on this release are hidden.</p>
      )}

      {projectsProblem && (
        <p role="alert" data-reason={projectsProblem.reason} className="mt-2 text-sm text-red-600">
          {messageForReason(projectsProblem)}
        </p>
      )}
      {sprintsProblem && (
        <p role="alert" data-reason={sprintsProblem.reason} className="mt-2 text-sm text-red-600">
          {messageForReason(sprintsProblem)}
        </p>
      )}

      {search.isPending && (
        <div className="mt-6 flex items-center gap-3">
          <span
            className="h-4 w-4 animate-spin rounded-full border-2 border-gray-300 border-t-blue-600"
            aria-hidden="true"
          />
          <p className="text-sm text-gray-600">
            Loading tickets… this can take a few seconds for large sprints.
          </p>
        </div>
      )}

      {searchProblem && (
        <div className="mt-6">
          <p role="alert" data-reason={searchProblem.reason} className="text-sm text-red-600">
            {messageForReason(searchProblem)}
          </p>
          <button
            type="button"
            onClick={runSearch}
            className="mt-2 rounded border border-gray-300 px-3 py-1.5 text-sm font-semibold text-gray-700"
          >
            Try again
          </button>
        </div>
      )}

      {!search.isPending && !searchProblem && loaded !== null && loaded.length === 0 && (
        <div className="mt-6 rounded bg-gray-50 p-6 text-center">
          <p className="text-base font-semibold text-gray-900">No tickets found</p>
          {loadedMode === 'jql' ? (
            <p className="mt-2 text-sm text-gray-600">
              {`No tickets matched your JQL${hiddenHint}. Try a broader query.`}
            </p>
          ) : (
            <p className="mt-2 text-sm text-gray-600">
              {`No tickets matched ${scopeDescription}${statusHint}${hiddenHint}. Try a different sprint, widen the status filter, or use Advanced: edit as JQL.`}
            </p>
          )}
        </div>
      )}

      {!search.isPending && loaded !== null && loaded.length > 0 && (
        <div className="mt-6 flex items-center gap-3">
          <p role="status" aria-live="polite" className="text-sm font-semibold text-blue-600">
            {selectedCount} selected
          </p>
        {selectedCount > 0 && (
            <button
              type="button"
              onClick={addToRelease}
              className="rounded bg-blue-600 px-4 py-2 text-sm font-semibold text-white"
            >
              {props.actionLabel}
            </button>
          )}
          {productMode && selectedCount > 0 && (
            <button
              type="button"
              onClick={() => setBundleDialogKeys([...looseSelected, ...bundleSelectedKeys])}
              className="rounded border border-gray-300 px-4 py-2 text-sm font-semibold text-gray-700"
            >
              Add to bundle
            </button>
          )}
        </div>
      )}

      {bundlesProblem && (
        <p role="alert" data-reason={bundlesProblem.reason} className="mt-2 text-sm text-red-600">
          {messageForReason(bundlesProblem)}
        </p>
      )}
      {deleteProblem && (
        <p role="alert" data-reason={deleteProblem.reason} className="mt-2 text-sm text-red-600">
          {messageForReason(deleteProblem)}
        </p>
      )}

      {!search.isPending && loaded !== null && loaded.length > 0 && (
        <div className="mt-6 overflow-x-auto rounded border border-gray-200 bg-white">
          <table className="min-w-full text-sm">
            <thead className="border-b border-gray-200">
              <tr>
                <th scope="col" className="p-0 text-left">
                  <label className="flex cursor-pointer items-center px-4 py-3">
                    <input
                      ref={selectAllRef}
                      type="checkbox"
                      checked={allSelected}
                      onChange={selectAll}
                      aria-label="Select all loaded tickets"
                      className="h-5 w-5 cursor-pointer rounded border-gray-300 text-blue-600 focus-visible:ring-2 focus-visible:ring-blue-600"
                    />
                  </label>
                </th>
                <th scope="col" className="px-4 py-2 text-left font-semibold text-gray-700">
                  Key
                </th>
                <th scope="col" className="px-4 py-2 text-left font-semibold text-gray-700">
                  Summary
                </th>
                <th scope="col" className="px-4 py-2 text-left font-semibold text-gray-700">
                  Type
                </th>
                <th scope="col" className="px-4 py-2 text-left font-semibold text-gray-700">
                  Status
                </th>
                <th scope="col" className="px-4 py-2 text-left font-semibold text-gray-700">
                  Resolution
                </th>
                <th scope="col" className="px-4 py-2 text-left font-semibold text-gray-700">
                  Assignee
                </th>
                <th scope="col" className="px-4 py-2 text-left font-semibold text-gray-700">
                  Reporter
                </th>
                <th scope="col" className="px-4 py-2" />
              </tr>
            </thead>
            <tbody>
              {bundleRows.map((row) => (
                <BundleRow
                  key={row.bundle.id}
                  row={row}
                  selected={selectedBundles.has(row.bundle.id)}
                  expanded={expandedKey === BUNDLE_EXPAND_PREFIX + row.bundle.id}
                  deleting={deleteBundle.isPending}
                  onToggleSelect={() => toggleBundle(row.bundle.id)}
                  onToggleExpand={() => toggleExpanded(BUNDLE_EXPAND_PREFIX + row.bundle.id)}
                  onDelete={() => deleteBundle.mutate(row.bundle.id)}
                />
              ))}
              {looseTickets.map((ticket) => (
                <Fragment key={ticket.key}>
                  {/* Single click selects, double click shows details (the two clicks of a double
                      click cancel out, so selection is left as it was); the arrow button stays for
                      keyboard and screen-reader users. Controls inside the row don't toggle, and a
                      single click that ends a text selection doesn't either. */}
                  <tr
                    className="cursor-pointer border-b border-gray-100 hover:bg-gray-50"
                    onClick={(event) => {
                      if (isControl(event.target)) return;
                      if (event.detail === 1 && window.getSelection()?.toString()) return;
                      toggle(ticket.key);
                    }}
                    onDoubleClick={(event) => {
                      if (isControl(event.target)) return;
                      window.getSelection()?.removeAllRanges();
                      setExpandedKey(expandedKey === ticket.key ? null : ticket.key);
                    }}
                  >
                    {/* The whole cell is the hit target: a bare checkbox was small enough that
                        clicks meant to select landed on the row and expanded it instead. */}
                    <td className="p-0">
                      <label className="flex cursor-pointer items-center px-4 py-3">
                        <input
                          type="checkbox"
                          checked={selected.has(ticket.key)}
                          onChange={() => toggle(ticket.key)}
                          aria-label={`Select ticket ${ticket.key}`}
                          className="h-5 w-5 cursor-pointer rounded border-gray-300 text-blue-600 focus-visible:ring-2 focus-visible:ring-blue-600"
                        />
                      </label>
                    </td>
                    <td className="whitespace-nowrap px-4 py-2 font-semibold text-gray-900">
                      {ticket.key}
                    </td>
                    {/* Wraps rather than truncating — a release manager choosing tickets needs to
                        read the whole summary, and an ellipsis hid exactly that. */}
                    <td className="min-w-[18rem] px-4 py-2 text-gray-700">{ticket.summary}</td>
                    <td className="whitespace-nowrap px-4 py-2 text-gray-700">{ticket.issueType}</td>
                    <td className="whitespace-nowrap px-4 py-2 text-gray-700">{ticket.status}</td>
                    <td className="whitespace-nowrap px-4 py-2 text-gray-700">
                      {ticket.resolution ?? '—'}
                    </td>
                    <td className="whitespace-nowrap px-4 py-2 text-gray-700">
                      {ticket.assignee ?? '—'}
                    </td>
                    <td className="whitespace-nowrap px-4 py-2 text-gray-700">{ticket.reporter}</td>
                    <td className="px-4 py-2">
                      <button
                        type="button"
                        onClick={() => setExpandedKey(expandedKey === ticket.key ? null : ticket.key)}
                        aria-expanded={expandedKey === ticket.key}
                        aria-label={
                          expandedKey === ticket.key ? 'Collapse ticket details' : 'Expand ticket details'
                        }
                        className="rounded px-2 text-sm text-gray-600 hover:text-gray-900 focus-visible:ring-2 focus-visible:ring-blue-600"
                      >
                        {expandedKey === ticket.key ? '▾' : '▸'}
                      </button>
                    </td>
                  </tr>
                  {expandedKey === ticket.key && (
                    <tr>
                      <td colSpan={9} className="px-4 pb-4">
                        <div className="max-h-80 overflow-y-auto rounded border border-gray-200 bg-gray-50 p-4">
                          <h3 className="text-sm font-semibold text-gray-900">Description</h3>
                          <p className="mt-2 whitespace-pre-wrap text-sm text-gray-700">
                            {ticket.description || 'No description.'}
                          </p>

                          <h3 className="mt-4 text-sm font-semibold text-gray-900">
                            Comments
                            {ticket.commentTotal > ticket.comments.length
                              ? ` (showing ${ticket.comments.length} of ${ticket.commentTotal})`
                              : ''}
                          </h3>
                          {ticket.comments.length === 0 ? (
                            <p className="mt-2 text-sm text-gray-600">No comments.</p>
                          ) : (
                            <ul className="mt-2 space-y-3">
                              {ticket.comments.map((comment, index) => (
                                <li
                                  key={index}
                                  className="whitespace-pre-wrap border-l-2 border-gray-200 pl-3 text-sm text-gray-700"
                                >
                                  {comment}
                                </li>
                              ))}
                            </ul>
                          )}

                          <div className="mt-4">
                            <TicketCodeLinks ticketKey={ticket.key} />
                          </div>
                        </div>
                      </td>
                    </tr>
                  )}
                </Fragment>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {productMode && (
        <AddToBundleDialog
          open={bundleDialogKeys !== null}
          productId={props.productId!}
          ticketKeys={bundleDialogKeys ?? []}
          bundles={bundlesQuery.data?.bundles ?? []}
          onSaved={() => {
            setSelected(new Set());
            setSelectedBundles(new Set());
          }}
          onClose={() => setBundleDialogKeys(null)}
        />
      )}
    </>
  );
}
