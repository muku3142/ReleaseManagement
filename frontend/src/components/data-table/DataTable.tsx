import { useId, useRef, type ReactNode } from 'react';
import { useTable, type ColumnDef, type RowData } from '@tanstack/react-table';
import type { LucideIcon } from 'lucide-react';
import { cn } from 'cn';
import { Button } from '@/components/ui/button';
import { Empty, EmptyContent, EmptyHeader, EmptyTitle } from '@/components/ui/empty';
import { Skeleton } from '@/components/ui/skeleton';
import { Table, TableBody, TableCaption, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { useEntranceStagger } from '@/hooks/useEntranceStagger';
import { DataTableToolbar } from './DataTableToolbar';
import { dataTableFeatures, type DataTableFeatures } from './features';

export type FacetedFilter = {
  columnId: string;
  title: string;
  options: { value: string; label: string; icon?: LucideIcon }[];
};

type DataTableProps<TData extends RowData> = {
  columns: ColumnDef<DataTableFeatures, TData, any>[];
  data: TData[];
  getRowId: (row: TData) => string;
  caption: string;
  noun: string;
  toolbar?: ReactNode;
  facetedFilters?: FacetedFilter[];
  emptyState?: ReactNode;
  isLoading?: boolean;
  stickyFirstColumn?: boolean;
  onRowClick?: (row: TData) => void;
};

const SKELETON_ROW_COUNT = 5;
const STICKY_FIRST_CELL =
  'sticky left-0 z-[5] bg-card group-hover:bg-[color-mix(in_oklab,var(--primary)_5%,var(--card))] shadow-[inset_-1px_0_0_var(--border)]';
const ACCENT_FIRST_CELL = 'group-hover:shadow-[inset_2px_0_0_var(--primary)]';
const ACCENT_STICKY_FIRST_CELL = 'group-hover:shadow-[inset_2px_0_0_var(--primary),inset_-1px_0_0_var(--border)]';
// One shadow draws both rules: sticky cells lose their borders, and two shadow utilities would override each other.
const STICKY_FIRST_HEAD = 'left-0 z-20 shadow-[inset_-1px_-1px_0_var(--border)]';

export function DataTable<TData extends RowData>({
  columns,
  data,
  getRowId,
  caption,
  noun,
  toolbar,
  facetedFilters = [],
  emptyState,
  isLoading = false,
  stickyFirstColumn = false,
  onRowClick,
}: DataTableProps<TData>) {
  const captionId = useId();
  // Sort, filter and visibility state lives in the table's own atoms: in memory, per mount, never persisted.
  const table = useTable({
    features: dataTableFeatures,
    data,
    columns,
    getRowId,
    sortDescFirst: false,
  });

  const firstFacetRef = useRef<HTMLButtonElement>(null);
  const visibleColumns = table.getVisibleLeafColumns();
  const rows = table.getRowModel().rows;
  const isFiltered = table.state.columnFilters.length > 0;
  const isStickyFirst = (index: number) => stickyFirstColumn && index === 0;
  const stagger = useEntranceStagger(!isLoading && rows.length > 0);

  const clearFilters = () => {
    table.resetColumnFilters();
    firstFacetRef.current?.focus();
  };

  const fullWidthRow = (content: ReactNode) => (
    <TableRow className="hover:bg-transparent">
      <TableCell colSpan={visibleColumns.length} className="p-0 whitespace-normal">
        {content}
      </TableCell>
    </TableRow>
  );

  const renderBody = () => {
    if (isLoading) {
      return Array.from({ length: SKELETON_ROW_COUNT }, (_, rowIndex) => (
        <TableRow key={rowIndex} className="border-b border-border last:border-0 hover:bg-transparent">
          {visibleColumns.map((column) => (
            <TableCell key={column.id} className="px-4 py-3">
              <Skeleton className="h-4 w-full" />
            </TableCell>
          ))}
        </TableRow>
      ));
    }
    if (data.length === 0) return fullWidthRow(emptyState);
    if (rows.length === 0) {
      return fullWidthRow(
        <Empty>
          <EmptyHeader>
            <EmptyTitle>{`No ${noun} match these filters.`}</EmptyTitle>
          </EmptyHeader>
          <EmptyContent>
            <Button variant="outline" size="sm" onClick={clearFilters}>
              Clear filters
            </Button>
          </EmptyContent>
        </Empty>,
      );
    }
    return rows.map((row, rowIndex) => {
      const entrance = stagger(rowIndex);
      return (
        <TableRow
          key={row.id}
          className={cn(
            'group border-b border-border last:border-0 hover:bg-primary/5 transition-colors duration-(--motion-fast)',
            onRowClick && 'cursor-pointer',
            entrance.className,
          )}
          style={entrance.style}
          onClick={
            onRowClick &&
            ((event) => {
              // The row's own link stays the keyboard path; a row click is a mouse convenience and must not double-fire inner controls.
              if ((event.target as HTMLElement).closest('a, button, input, [role="checkbox"]')) return;
              onRowClick(row.original);
            })
          }
        >
          {row.getVisibleCells().map((cell, index) => (
            <TableCell
              key={cell.id}
              className={cn(
                'px-4 py-3 text-sm text-foreground',
                isStickyFirst(index) && STICKY_FIRST_CELL,
                onRowClick && index === 0 && (isStickyFirst(index) ? ACCENT_STICKY_FIRST_CELL : ACCENT_FIRST_CELL),
                cell.column.columnDef.meta?.cellClassName,
              )}
            >
              <table.FlexRender cell={cell} />
            </TableCell>
          ))}
        </TableRow>
      );
    });
  };

  return (
    <div>
      <DataTableToolbar
        table={table}
        toolbar={toolbar}
        facetedFilters={facetedFilters}
        isFiltered={isFiltered}
        firstFacetRef={firstFacetRef}
      />
      <p className="sr-only" aria-live="polite">
        {isFiltered && !isLoading ? `${rows.length} ${noun} shown.` : ''}
      </p>
      <div
        className="rounded-xl border bg-card shadow-xs"
        {...(isLoading ? { role: 'status', 'aria-busy': true } : {})}
      >
        {isLoading && <span className="sr-only">{`Loading ${noun}…`}</span>}
        <Table
          containerProps={{
            className: 'max-h-[70vh] overflow-auto',
            tabIndex: 0,
            role: 'region',
            'aria-labelledby': captionId,
          }}
        >
          <TableCaption id={captionId} className="sr-only">
            {caption}
          </TableCaption>
          <TableHeader>
            {table.getHeaderGroups().map((headerGroup) => (
              <TableRow key={headerGroup.id} className="hover:bg-transparent">
                {headerGroup.headers.map((header, index) => {
                  const { column } = header;
                  const sorted = column.getIsSorted();
                  return (
                    <TableHead
                      key={header.id}
                      scope="col"
                      aria-sort={
                        column.getCanSort()
                          ? sorted === 'asc'
                            ? 'ascending'
                            : sorted === 'desc'
                              ? 'descending'
                              : 'none'
                          : undefined
                      }
                      className={cn(
                        'sticky top-0 z-10 h-10 bg-card px-4 text-xs font-semibold text-muted-foreground shadow-[inset_0_-1px_0_var(--border)]',
                        isStickyFirst(index) && STICKY_FIRST_HEAD,
                        column.columnDef.meta?.headerClassName,
                      )}
                    >
                      {header.isPlaceholder ? null : <table.FlexRender header={header} />}
                    </TableHead>
                  );
                })}
              </TableRow>
            ))}
          </TableHeader>
          <TableBody>{renderBody()}</TableBody>
        </Table>
      </div>
    </div>
  );
}
