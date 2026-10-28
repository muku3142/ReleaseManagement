import type { RowData, Table } from '@tanstack/react-table';
import { Settings2 } from 'lucide-react';
import { Button } from '@/components/ui/button';
import {
  DropdownMenu,
  DropdownMenuCheckboxItem,
  DropdownMenuContent,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu';
import type { DataTableFeatures } from './features';

export function DataTableViewOptions<TData extends RowData>({ table }: { table: Table<DataTableFeatures, TData> }) {
  const hideableColumns = table.getAllColumns().filter((column) => column.getCanHide());

  return (
    <DropdownMenu>
      <DropdownMenuTrigger asChild>
        <Button variant="outline" size="sm" className="ml-auto">
          <Settings2 aria-hidden="true" />
          Columns
        </Button>
      </DropdownMenuTrigger>
      <DropdownMenuContent align="end">
        {hideableColumns.map((column) => (
          <DropdownMenuCheckboxItem
            key={column.id}
            checked={column.getIsVisible()}
            onCheckedChange={(checked) => column.toggleVisibility(checked)}          >
            {column.columnDef.meta?.label ?? column.id}
          </DropdownMenuCheckboxItem>
        ))}
      </DropdownMenuContent>
    </DropdownMenu>
  );
}
