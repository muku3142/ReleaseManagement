import {
  columnFacetingFeature,
  columnFilteringFeature,
  columnVisibilityFeature,
  createFacetedRowModel,
  createFacetedUniqueValues,
  createFilteredRowModel,
  createSortedRowModel,
  filterFn_arrHas,
  filterFn_includesString,
  metaHelper,
  rowSortingFeature,
  sortFn_alphanumeric,
  sortFn_text,
  tableFeatures,
} from '@tanstack/react-table';

export type DataTableColumnMeta = {
  label?: string;
  headerClassName?: string;
  cellClassName?: string;
};

// The single extension point: row selection, expanding and pinning are registered here when a page first needs them.
export const dataTableFeatures = tableFeatures({
  rowSortingFeature,
  columnFilteringFeature,
  columnFacetingFeature,
  columnVisibilityFeature,
  sortedRowModel: createSortedRowModel(),
  filteredRowModel: createFilteredRowModel(),
  facetedRowModel: createFacetedRowModel(),
  facetedUniqueValues: createFacetedUniqueValues(),
  sortFns: { alphanumeric: sortFn_alphanumeric, text: sortFn_text },
  filterFns: { includesString: filterFn_includesString, arrHas: filterFn_arrHas },
  columnMeta: metaHelper<DataTableColumnMeta>(),
});

export type DataTableFeatures = typeof dataTableFeatures;
