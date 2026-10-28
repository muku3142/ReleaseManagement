import type { ReactNode, Ref } from 'react';
import type { RowData, Table } from '@tanstack/react-table';
import { X } from 'lucide-react';
import { Button } from '@/components/ui/button';
import type { FacetedFilter } from './DataTable';
import { DataTableFacetedFilter } from './DataTableFacetedFilter';
import { DataTableViewOptions } from './DataTableViewOptions';
import type { DataTableFeatures } from './features';

type DataTableToolbarProps<TData extends RowData> = {
  table: Table<DataTableFeatures, TData>;
  toolbar?: ReactNode;
  facetedFilters: FacetedFilter[];
  isFiltered: boolean;
  firstFacetRef: Ref<HTMLButtonElement>;
};

export function DataTableToolbar<TData extends RowData>({
  table,
  toolbar,
  facetedFilters,
  isFiltered,
  firstFacetRef,
}: DataTableToolbarProps<TData>) {
  return (
    <div className="mb-4 flex flex-wrap items-center gap-2">
      {toolbar}
      {facetedFilters.map((filter, index) => {
        const column = table.getColumn(filter.columnId);
        return (
          column && (
            <DataTableFacetedFilter
              key={filter.columnId}
              column={column}
              title={filter.title}
              options={filter.options}
              triggerRef={index === 0 ? firstFacetRef : undefined}
            />
          )
        );
      })}
      {isFiltered && (
        <Button variant="ghost" size="sm" onClick={() => table.resetColumnFilters()}>
          Clear filters
          <X aria-hidden="true" />
        </Button>
      )}
      <DataTableViewOptions table={table} />
    </div>
  );
}
