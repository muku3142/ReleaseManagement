import type { Column, RowData } from '@tanstack/react-table';
import { ArrowDown, ArrowUp, ArrowUpDown } from 'lucide-react';
import { cn } from 'cn';
import { Button } from '@/components/ui/button';
import type { DataTableFeatures } from './features';

type DataTableColumnHeaderProps<TData extends RowData> = {
  column: Column<DataTableFeatures, TData, any>;
  title: string;
  className?: string;
};

export function DataTableColumnHeader<TData extends RowData>({ column, title, className }: DataTableColumnHeaderProps<TData>) {
  if (!column.getCanSort()) {
    return <span className={className}>{title}</span>;
  }

  const sorted = column.getIsSorted();
  const SortIcon = sorted === 'asc' ? ArrowUp : sorted === 'desc' ? ArrowDown : ArrowUpDown;

  return (
    <Button
      variant="ghost"
      size="sm"
      className={cn('-ml-3 h-8', className)}
      aria-label={`Sort by ${title}`}
      onClick={() => column.toggleSorting()}
    >
      {title}
      <SortIcon aria-hidden="true" className={cn('size-3.5', sorted ? 'text-foreground' : 'text-muted-foreground')} />
    </Button>
  );
}
