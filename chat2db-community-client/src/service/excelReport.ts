import createRequest from './base';

// --- Types ---

/** A rectangular range of merged cells within a sheet. */
export interface MergeRange {
  startRow?: number;
  endRow?: number;
  startColumn?: number;
  endColumn?: number;
}

/** Per-sheet layout and data-binding configuration of an Excel report template. */
export interface SheetConfig {
  sheetName?: string;
  dataStartRow?: number;
  dataStartColumn?: number;
  headerMapping?: string;
  rowExpansionMode?: string;
  freezeRows?: number;
  freezeColumns?: number;
  mergeRanges?: MergeRange[];
  autoWidth?: boolean;
  emptyResultBehavior?: string;
  fieldBindings?: ExcelColumnBinding[];
}

/** Binding of a query field to a target Excel column. */
export interface ExcelColumnBinding {
  queryFieldId?: string;
  targetColumn?: string;
  displayName?: string;
  numberFormat?: string;
  nullDisplay?: string;
  alignment?: string;
  exportEnabled?: boolean;
}

/** An uploaded Excel report template bound to a saved query view. */
export interface ExcelReportTemplate {
  id?: number;
  workspaceId?: number;
  name?: string;
  description?: string;
  templateFile?: string;
  fileHash?: string;
  templateVersion?: number;
  queryViewId?: number;
  sheetConfigs?: SheetConfig[];
  status?: string;
  ownerId?: number;
  gmtCreate?: string;
  gmtModified?: string;
}

/** Result of an Excel export operation. */
export interface ExportResult {
  downloadToken?: string;
  exportId?: number;
  rowCount?: number;
  fileSize?: number;
  status?: string;
}

// --- Template CRUD ---
export const excelTemplateList = createRequest<
  { workspaceId?: number; pageNo?: number; pageSize?: number; searchKey?: string },
  unknown
>('/api/excel-report-templates', { method: 'get' });

export const excelTemplateDetail = createRequest<{ id: number }, ExcelReportTemplate>(
  '/api/excel-report-templates/:id',
  { method: 'get' },
);

export const createExcelTemplate = createRequest<ExcelReportTemplate, number>(
  '/api/excel-report-templates',
  { method: 'post' },
);

export const updateExcelTemplate = createRequest<ExcelReportTemplate, void>(
  '/api/excel-report-templates/:id',
  { method: 'put' },
);

export const deleteExcelTemplate = createRequest<{ id: number }, string>(
  '/api/excel-report-templates/:id',
  { method: 'delete' },
);

export const validateExcelTemplate = createRequest<{ id: number }, unknown[]>(
  '/api/excel-report-templates/:id/validate',
  { method: 'post' },
);

export const copyExcelTemplate = createRequest<{ id: number; name?: string }, unknown>(
  '/api/excel-report-templates/:id/copy',
  { method: 'post' },
);

// --- Upload (multipart formData) ---
export const uploadExcelTemplate = createRequest<
  { workspaceId: number; name: string; description: string; queryViewId: number; file: File },
  number
>('/api/excel-report-templates/upload', { method: 'post', contentType: 'formData' });

// --- Sheet config & bindings ---
export const getTemplateSheetNames = createRequest<{ id: number }, string[]>(
  '/api/excel-report-templates/:id/sheet-names',
  { method: 'get' },
);

export const updateSheetConfigs = createRequest<{ id: number; sheetConfigs: SheetConfig[] }, void>(
  '/api/excel-report-templates/:id/sheet-configs',
  { method: 'put' },
);

export const updateFieldBindings = createRequest<
  { id: number; sheetName: string; bindings: ExcelColumnBinding[] },
  void
>('/api/excel-report-templates/:id/field-bindings', { method: 'put' });

// --- Export ---
// Backend contract: `templateId` (required) and `filterOverrides` (optional) must be
// URL query params, NOT the request body. createRequest only supports query params
// for GET/DELETE, so we append them to the URL and let createRequest do the
// :queryViewId path substitution plus its standard response unwrapping.
export const exportExcel = (params: {
  queryViewId: number;
  templateId: number;
  filterOverrides?: string;
}): Promise<ExportResult> => {
  const query = new URLSearchParams();
  query.set('templateId', String(params.templateId));
  if (params.filterOverrides) {
    query.set('filterOverrides', params.filterOverrides);
  }
  const requestFn = createRequest<{ queryViewId: number }, ExportResult>(
    `/api/saved-query-views/:queryViewId/export/excel?${query.toString()}`,
    { method: 'post' },
  );
  return requestFn({ queryViewId: params.queryViewId });
};

// --- Download (binary, NOT createRequest) ---
export const downloadExcel = (exportId: number, token: string): string => {
  return `/api/excel-exports/${exportId}/download?token=${token}`;
};