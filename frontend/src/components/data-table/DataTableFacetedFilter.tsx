import type { Ref } from 'react';
import type { Column, RowData } from '@tanstack/react-table';
import { Check, CirclePlus } from 'lucide-react';
import { cn } from 'cn';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import {
  Command,
  CommandEmpty,
  CommandGroup,
  CommandInput,
  CommandItem,
  CommandList,
  CommandSeparator,
} from '@/components/ui/command';
import { Popover, PopoverContent, PopoverTrigger } from '@/components/ui/popover';
import { Separator } from '@/components/ui/separator';
import type { FacetedFilter } from './DataTable';
import type { DataTableFeatures } from './features';

const MAX_VALUE_BADGES = 2;

type DataTableFacetedFilterProps<TData extends RowData> = Omit<FacetedFilter, 'columnId'> & {
  column: Column<DataTableFeatures, TData, unknown>;
  triggerRef?: Ref<HTMLButtonElement>;
};

export function DataTableFacetedFilter<TData extends RowData>({
  column,
  title,
  options,
  triggerRef,
}: DataTableFacetedFilterProps<TData>) {
  const counts = column.getFacetedUniqueValues();
  const selected = new Set(column.getFilterValue() as string[] | undefined);
  const selectedOptions = options.filter((option) => selected.has(option.value));

  const toggle = (value: string) => {
    const next = new Set(selected);
    if (next.has(value)) next.delete(value);
    else next.add(value);
    column.setFilterValue(next.size ? Array.from(next) : undefined);
  };

  return (
    <Popover>
      <PopoverTrigger asChild>
        <Button ref={triggerRef} variant="outline" size="sm" data-slot="faceted-filter-trigger">
          <CirclePlus aria-hidden="true" />
          {title}
          {selected.size > 0 && (
            <>
              <Separator orientation="vertical" className="mx-1 h-4" />
              {selected.size > MAX_VALUE_BADGES ? (
                <Badge variant="neutral">{`${selected.size} selected`}</Badge>
              ) : (
                selectedOptions.map((option) => (
                  <Badge key={option.value} variant="neutral">
                    {option.label}
                  </Badge>
                ))
              )}
            </>
          )}
        </Button>
      </PopoverTrigger>
      <PopoverContent className="w-52 p-0" align="start">
        <Command>
          <CommandInput placeholder={title} />
          <CommandList>
            <CommandEmpty>No results found.</CommandEmpty>
            <CommandGroup>
              {options.map((option) => {
                const isSelected = selected.has(option.value);
                const Icon = option.icon;
                return (
                  <CommandItem key={option.value} value={option.label} onSelect={() => toggle(option.value)}>
                    <span
                      aria-hidden="true"
                      className={cn(
                        'flex size-4 items-center justify-center rounded-sm border border-primary',
                        isSelected ? 'bg-primary text-primary-foreground' : '[&_svg]:invisible',
                      )}
                    >
                      <Check className="size-3.5 text-current" />
                    </span>
                    {Icon && <Icon aria-hidden="true" className="text-muted-foreground" />}
                    <span>{option.label}</span>
                    <span className="ml-auto text-xs text-muted-foreground tabular-nums">{counts.get(option.value) ?? 0}</span>
                  </CommandItem>
                );
              })}
            </CommandGroup>
            {selected.size > 0 && (
              <>
                <CommandSeparator />
                <CommandGroup>
                  <CommandItem onSelect={() => column.setFilterValue(undefined)} className="justify-center text-center">
                    Clear filter
                  </CommandItem>
                </CommandGroup>
              </>
            )}
          </CommandList>
        </Command>
      </PopoverContent>
    </Popover>
  );
}
