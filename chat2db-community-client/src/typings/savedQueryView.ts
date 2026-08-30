export interface ViewDimension {
  fieldId?: string;
  role?: string;
  sortDirection?: string;
}

export interface ViewMeasure {
  fieldId?: string;
  aggregation?: string;
}

export interface ViewFilter {
  fieldId?: string;
  filterType?: string;
  operator?: string;
  value?: string;
  values?: string[];
}

export interface ViewSort {
  fieldId?: string;
  direction?: string;
}

export interface SavedQueryView {
  id?: number;
  workspaceId?: number;
  datasetId?: number;
  datasetVersion?: number;
  name?: string;
  description?: string;
  dimensions?: ViewDimension[];
  measures?: ViewMeasure[];
  rowFields?: string[];
  columnFields?: string[];
  filters?: ViewFilter[];
  sort?: ViewSort[];
  pageSize?: number;
  status?: string;
  version?: number;
  ownerId?: number;
  gmtCreate?: string;
  gmtModified?: string;
}

export interface SavedQueryViewListParams {
  workspaceId?: number;
  searchKey?: string;
  pageNo: number;
  pageSize: number;
}

export interface PreviewSavedQueryViewParams {
  id: number;
  pageNo?: number;
  pageSize?: number;
  filterOverrides?: string;
}