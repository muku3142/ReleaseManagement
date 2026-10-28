import { useEffect, useRef, useState } from 'react';
import type { Product } from '../api/products';

type Props = { products: Product[]; selected: string[]; onChange: (ids: string[]) => void };

export function ProductFilter({ products, selected, onChange }: Props) {
  const detailsRef = useRef<HTMLDetailsElement>(null);
  const [open, setOpen] = useState(false);

  useEffect(() => {
    if (!open) return;
    const onMouseDown = (event: MouseEvent) => {
      if (!detailsRef.current?.contains(event.target as Node)) {
        detailsRef.current!.open = false;
        setOpen(false);
      }
    };
    document.addEventListener('mousedown', onMouseDown);
    return () => document.removeEventListener('mousedown', onMouseDown);
  }, [open]);

  if (products.length === 0) return null;

  const options = products
    .filter((p) => !p.archived || selected.includes(p.id))
    .sort((a, b) => a.name.toLowerCase().localeCompare(b.name.toLowerCase()));
  const names = selected.map((id) => products.find((p) => p.id === id)?.name).filter(Boolean) as string[];
  const label = names.length === 0 ? 'All products' : names.length <= 2 ? names.join(', ') : `${names.length} products`;

  const toggle = (id: string, checked: boolean) =>
    onChange(checked ? [...selected, id] : selected.filter((s) => s !== id));

  const onKeyDown = (event: React.KeyboardEvent<HTMLDetailsElement>) => {
    if (event.key === 'Escape' && detailsRef.current?.open) {
      detailsRef.current.open = false;
      setOpen(false);
      detailsRef.current.querySelector('summary')?.focus();
    }
  };

  return (
    <div className="flex flex-col gap-1">
      <span className="text-xs text-gray-600">Products</span>
      <details
        ref={detailsRef}
        className="relative"
        onToggle={(event) => setOpen(event.currentTarget.open)}
        onKeyDown={onKeyDown}
      >
        <summary
          title={names.length > 2 ? names.join(', ') : undefined}
          className={`cursor-pointer rounded border border-gray-300 px-3 py-1.5 text-sm focus-visible:ring-2 focus-visible:ring-blue-600${selected.length > 0 ? ' bg-gray-100' : ''}`}
        >
          {label}
        </summary>
        <div className="absolute z-10 mt-1 w-64 max-w-[calc(100vw-2rem)] rounded border border-gray-200 bg-white p-3">
          <fieldset>
            <legend className="sr-only">Filter by product</legend>
            <ul className="max-h-64 overflow-y-auto">
              {options.map((p) => (
                <li key={p.id}>
                  <label className="flex items-center gap-2 py-1 text-sm text-gray-700">
                    <input
                      type="checkbox"
                      checked={selected.includes(p.id)}
                      onChange={(event) => toggle(p.id, event.target.checked)}
                      className="focus-visible:ring-2 focus-visible:ring-blue-600"
                    />
                    {p.name}
                    {p.archived ? ' (archived)' : ''}
                  </label>
                </li>
              ))}
            </ul>
          </fieldset>
          {selected.length > 0 && (
            <div className="mt-3">
              <button type="button" onClick={() => onChange([])} className="text-sm text-blue-600 hover:underline">
                Clear products
              </button>
            </div>
          )}
        </div>
      </details>
    </div>
  );
}
