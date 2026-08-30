export interface QueryDatasetField {
  fieldId?: string;
  sourceColumn?: string;
  displayName?: string;
  dataType?: string;
  role?: string;
  aggregation?: string;
  filterable?: boolean;
  sortable?: boolean;
  visible?: boolean;
  numberFormat?: string;
  nullDisplay?: string;
}

export interface DatasetFilter {
  fieldId?: string;
  operator?: string;
  value?: string;
  values?: string[];
}

export interface QueryDataset {
  id?: number;
  workspaceId?: number;
  name?: string;
  description?: string;
  datasourceId?: number;
  databaseName?: string;
  schemaName?: string;
  tableName?: string;
  sourceObjectType?: string;
  status?: string;
  version?: number;
  sourceSchemaHash?: string;
  fields?: QueryDatasetField[];
  baseFilters?: DatasetFilter[];
  ownerId?: number;
  gmtCreate?: string;
  gmtModified?: string;
}

export interface QueryDatasetListParams {
  workspaceId?: number;
  searchKey?: string;
  pageNo: number;
  pageSize: number;
}

export interface PreviewResult {
  rows: Record<string, unknown>[];
  total: number;
  pageNo: number;
  pageSize: number;
  columns: string[];
}
