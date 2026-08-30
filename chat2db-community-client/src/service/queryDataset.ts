import { IPageResponse } from '@/typings';
import { QueryDataset, QueryDatasetListParams, PreviewResult } from '@/typings/queryDataset';
import createRequest from './base';

/** Get paginated query dataset list */
export const queryDatasetList = createRequest<QueryDatasetListParams, IPageResponse<QueryDataset>>(
  '/api/query-datasets',
  { method: 'get' },
);

/** Get dataset detail by id */
export const queryDatasetDetail = createRequest<{ id: number }, QueryDataset>('/api/query-datasets/:id', {
  method: 'get',
});

/** Create a new dataset */
export const createQueryDataset = createRequest<QueryDataset, number>('/api/query-datasets', { method: 'post' });

/** Update an existing dataset */
export const updateQueryDataset = createRequest<QueryDataset, void>('/api/query-datasets/:id', {
  method: 'put',
});

/** Delete a dataset */
export const deleteQueryDataset = createRequest<{ id: number }, string>('/api/query-datasets/:id', {
  method: 'delete',
});

/** Validate a dataset */
export const validateQueryDataset = createRequest<{ id: number }, unknown[]>('/api/query-datasets/:id/validate', {
  method: 'post',
});

/** Publish a dataset */
export const publishQueryDataset = createRequest<{ id: number }, void>('/api/query-datasets/:id/publish', {
  method: 'post',
});

/** Disable a dataset */
export const disableQueryDataset = createRequest<{ id: number }, void>('/api/query-datasets/:id/disable', {
  method: 'post',
});

/** Copy a dataset */
export const copyQueryDataset = createRequest<{ id: number; name?: string }, number>(
  '/api/query-datasets/:id/copy',
  { method: 'post' },
);

/** Preview dataset query results */
export const previewQueryDataset = createRequest<
  { id: number; pageNo?: number; pageSize?: number },
  PreviewResult
>('/api/query-datasets/:id/preview', { method: 'get' });
