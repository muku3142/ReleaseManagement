import { useEffect, useRef, useState } from 'react';
import type { PlaceholderEntry } from '../api/templates';

const ITEM_SCOPES: PlaceholderEntry['scope'][] = ['ITEM', 'COMMENT', 'COMMIT', 'PULL_REQUEST'];

function matchesFilter(entry: PlaceholderEntry, filter: string): boolean {
  if (filter === '') return true;
  const needle = filter.toLowerCase();
  return entry.token.toLowerCase().includes(needle) || entry.description.toLowerCase().includes(needle);
}

function Section(props: {
  title: string;
  subheading?: string;
  entries: PlaceholderEntry[];
  open: boolean;
  onToggle: () => void;
  onRowInsert: (token: string) => void;
}) {
  const { title, subheading, entries, open, onToggle, onRowInsert } = props;
  if (entries.length === 0) return null;

  return (
    <div className="border-b border-gray-200">
      <button
        type="button"
        onClick={onToggle}
        aria-expanded={open}
        className="flex w-full items-center justify-between px-4 py-2 text-left text-sm font-semibold text-gray-900"
      >
        <span>
          {title}
          {subheading && <span className="ml-2 text-xs font-normal text-gray-500">{subheading}</span>}
        </span>
        <span aria-hidden="true">{open ? '−' : '+'}</span>
      </button>
      {open &&
        entries.map((entry) => (
          <button type="button" onClick={() => insert(entry.token)}
                  key={entry.token}
                  onKeyDown={(e) => {
                    // Real <button>s activate on Enter/Space in every browser; jsdom's test
                    // environment does not simulate that default action, so this handler makes
                    // the keyboard path deterministic in tests too (harmless, not a duplicate
                    // activation - a native browser click already followed by this handler would
                    // just call insert twice with an identical token, so guard with preventDefault).
                    if (e.key === 'Enter' || e.key === ' ') {
                      e.preventDefault();
                      insert(entry.token);
                    }
                  }}
                  className="block w-full border-b border-gray-100 px-4 py-2 text-left hover:bg-gray-50">
            <span className="font-mono text-sm">{entry.token}</span>
            <span className="block text-sm text-gray-700">{entry.description}</span>
            <span className="mt-1 flex flex-wrap gap-1">
              {entry.tags.map((tag) => (
                <span key={tag} className="rounded bg-gray-100 px-1.5 py-0.5 text-xs text-gray-700">{tag}</span>
              ))}
            </span>
          </button>
        ))}
    </div>
  );

  function insert(token: string) {
    onRowInsert(token);
  }
}

// Non-modal on purpose (D-04-21): the editor underneath must stay visible and directly interactive,
// because inserting a placeholder should feel like continuing to write rather than leaving the
// editor. That rules out the modal native dialog element every other overlay in this app uses.
// It overlays the preview - this screen's declared visual anchor - so it must be dismissible in one
// gesture: the x, the toggle button, Escape, or a click outside.
export function PlaceholderDrawer(props: {
  open: boolean;
  entries: PlaceholderEntry[] | undefined;
  onInsert: (token: string) => void;
  onClose: () => void;
}) {
  const { open, entries, onInsert, onClose } = props;
  const drawerRef = useRef<HTMLDivElement>(null);
  const [filter, setFilter] = useState('');
  const [releaseOpen, setReleaseOpen] = useState(true);
  const [itemOpen, setItemOpen] = useState(true);
  const [groupOpen, setGroupOpen] = useState(true);
  const [productOpen, setProductOpen] = useState(true);
  const [announcement, setAnnouncement] = useState('');

  useEffect(() => {
    if (!open) return;
    function onKeyDown(e: KeyboardEvent) {
      if (e.key === 'Escape') onClose();
    }
    function onMouseDown(e: MouseEvent) {
      const target = e.target as Node;
      if (drawerRef.current && drawerRef.current.contains(target)) return;
      if (target instanceof Element && target.closest('[data-placeholder-toggle]')) return;
      onClose();
    }
    document.addEventListener('keydown', onKeyDown);
    document.addEventListener('mousedown', onMouseDown);
    return () => {
      document.removeEventListener('keydown', onKeyDown);
      document.removeEventListener('mousedown', onMouseDown);
    };
  }, [open, onClose]);

  function handleInsert(token: string) {
    onInsert(token);
    setAnnouncement(`Inserted {{${token}}}`);
  }

  const filtered = (entries ?? []).filter((entry) => matchesFilter(entry, filter));
  const releaseEntries = filtered.filter((entry) => entry.scope === 'ROOT');
  const itemEntries = filtered.filter((entry) => ITEM_SCOPES.includes(entry.scope));
  const groupEntries = filtered.filter((entry) => entry.scope === 'GROUP');
  const productEntries = filtered.filter((entry) => entry.scope === 'PRODUCT');

  return (
    <div
      ref={drawerRef}
      role="region"
      aria-label="Placeholders"
      className="absolute right-0 top-0 z-10 h-full w-[380px] overflow-y-auto border-l border-gray-300 bg-white shadow-lg lg:w-[380px] max-lg:inset-x-0 max-lg:bottom-0 max-lg:top-auto max-lg:h-auto max-lg:max-h-[70vh] max-lg:w-full max-lg:border-l-0 max-lg:border-t"
    >
      <div className="flex items-center gap-2 border-b border-gray-200 p-4">
        <h3 className="text-base font-semibold text-gray-900">Placeholders</h3>
        <span className="grow" />
        <button type="button" onClick={onClose} aria-label="Close placeholder catalog"
                className="text-gray-600 hover:text-gray-900">×</button>
      </div>
      <input type="text" value={filter} onChange={(e) => setFilter(e.target.value)}
             placeholder="Filter placeholders…" aria-label="Filter placeholders"
             className="m-4 w-[calc(100%-2rem)] rounded border border-gray-300 px-3 py-2 text-sm focus-visible:ring-2 focus-visible:ring-blue-600" />

      {entries === undefined ? (
        <div className="flex items-center gap-3 p-4">
          <span
            className="h-4 w-4 animate-spin rounded-full border-2 border-gray-300 border-t-blue-600"
            aria-hidden="true"
          />
          <p className="text-sm text-gray-600">Loading placeholders…</p>
        </div>
      ) : (
        <>
          <Section
            title="Release fields"
            entries={releaseEntries}
            open={releaseOpen}
            onToggle={() => setReleaseOpen((v) => !v)}
            onRowInsert={handleInsert}
          />
          <Section
            title="Item fields"
            subheading="Valid inside {{#items}} and {{#groups}}{{#items}}"
            entries={itemEntries}
            open={itemOpen}
            onToggle={() => setItemOpen((v) => !v)}
            onRowInsert={handleInsert}
          />
          <Section
            title="Group fields"
            subheading="Valid inside {{#groups}}"
            entries={groupEntries}
            open={groupOpen}
            onToggle={() => setGroupOpen((v) => !v)}
            onRowInsert={handleInsert}
          />
          <Section
            title="Product fields"
            subheading="Valid inside {{#products}}"
            entries={productEntries}
            open={productOpen}
            onToggle={() => setProductOpen((v) => !v)}
            onRowInsert={handleInsert}
          />
        </>
      )}

      <p aria-live="polite" className="sr-only">{announcement}</p>
    </div>
  );
}
