import { IPageResponse } from '@/typings';
import { PreviewResult } from '@/typings/queryDataset';
import {
  SavedQueryView,
  SavedQueryViewListParams,
  PreviewSavedQueryViewParams,
} from '@/typings/savedQueryView';
import createRequest from './base';

/** Get paginated saved query view list */
export const savedQueryViewList = createRequest<
  SavedQueryViewListParams,
  IPageResponse<SavedQueryView>
>('/api/saved-query-views', { method: 'get' });

/** Get saved query view detail by id */
export const savedQueryViewDetail = createRequest<{ id: number }, SavedQueryView>(
  '/api/saved-query-views/:id',
  { method: 'get' },
);

/** Create a new saved query view */
export const createSavedQueryView = createRequest<SavedQueryView, number>(
  '/api/saved-query-views',
  { method: 'post' },
);

/** Update an existing saved query view */
export const updateSavedQueryView = createRequest<SavedQueryView, void>(
  '/api/saved-query-views/:id',
  { method: 'put' },
);

/** Delete a saved query view */
export const deleteSavedQueryView = createRequest<{ id: number }, string>(
  '/api/saved-query-views/:id',
  { method: 'delete' },
);

/** Validate a saved query view */
export const validateSavedQueryView = createRequest<{ id: number }, unknown[]>(
  '/api/saved-query-views/:id/validate',
  { method: 'post' },
);

/** Publish a saved query view */
export const publishSavedQueryView = createRequest<{ id: number }, void>(
  '/api/saved-query-views/:id/publish',
  { method: 'post' },
);

/** Disable a saved query view */
export const disableSavedQueryView = createRequest<{ id: number }, void>(
  '/api/saved-query-views/:id/disable',
  { method: 'post' },
);

/** Copy a saved query view */
export const copySavedQueryView = createRequest<{ id: number; name?: string }, number>(
  '/api/saved-query-views/:id/copy',
  { method: 'post' },
);

/** Preview saved query view results with optional filter overrides (JSON string) */
export const previewSavedQueryView = createRequest<
  PreviewSavedQueryViewParams,
  PreviewResult
>('/api/saved-query-views/:id/preview', { method: 'get' });