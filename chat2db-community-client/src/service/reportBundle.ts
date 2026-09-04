import type { IPageResponse } from '@/typings';
import type {
  ICreateReportBundleVersionRequest,
  IReportBundle,
  IReportBundleExportResult,
  IReportBundleListParams,
  IReportBundlePresetFiltersUpdateParams,
  IReportBundleVersion,
  IReportBundleVersionDeleteParams,
  IReportBundleVersionDetailParams,
  IReportBundleVersionExportDownloadParams,
  IReportBundleVersionExportParams,
  IReportBundleVersionListParams,
  IReportDataViewPreviewParams,
  IReportDataViewPreviewResult,
  IUpdateReportBundleRequest,
} from '@/typings/reportBundle';
import type { IOptions } from './base';
import { validateReportBundleVersionRequest } from './reportBundleValidation.ts';

type Request<P, R> = (params: P, restParams?: { signal: AbortSignal | null }) => Promise<R>;

const createLazyRequest = <P, R>(url: string, options: IOptions): Request<P, R> => {
  return (params: P, restParams?: { signal: AbortSignal | null }) =>
    import('./base').then(({ default: createRequest }) => createRequest<P, R>(url, options)(params, restParams));
};

/**
 * Wraps a URL template that interpolates two path placeholders before
 * delegating to `createRequest`. The shared `createRequest` helper only
 * substitutes the first `:name` it finds, so bundleId + versionId routes
 * (listVersions, getVersion, deleteVersion, exportVersion) must resolve
 * their templates explicitly before reaching the shared helper.
 */
const createMultiPathLazyRequest = <P extends Record<string, unknown>, R>(
  buildUrl: (params: P) => string,
  pathParams: ReadonlyArray<keyof P>,
  options: IOptions,
): Request<P, R> => {
  return (params: P, restParams?: { signal: AbortSignal | null }) =>
    import('./base').then(({ default: createRequest }) => {
      const url = buildUrl(params);
      const remaining = { ...params };
      pathParams.forEach((key) => {
        delete (remaining as Record<string, unknown>)[key as string];
      });
      return createRequest<P, R>(url, options)(remaining as P, restParams);
    });
};

export const reportBundleList = createLazyRequest<IReportBundleListParams, IPageResponse<IReportBundle>>(
  '/api/report-bundles',
  { method: 'get' },
);

export const reportBundleDetail = createLazyRequest<{ id: number; workspaceId: number }, IReportBundle>(
  '/api/report-bundles/:id',
  { method: 'get' },
);

export const createReportBundle = createLazyRequest<IReportBundle, number>('/api/report-bundles', { method: 'post' });

export const updateReportBundle = (params: IUpdateReportBundleRequest): Promise<void> => {
  const query = new URLSearchParams();
  query.set('workspaceId', String(params.workspaceId));
  const url = `/api/report-bundles/${params.id}?${query.toString()}`;
  const requestFn = createLazyRequest<unknown, void>(url, { method: 'put' });
  return requestFn({
    name: params.name,
    description: params.description,
    queryViewId: params.queryViewId,
    boundFields: params.boundFields,
    presetRowFilters: params.presetRowFilters,
    activeVersionId: params.activeVersionId,
  });
};

export const deleteReportBundle = createLazyRequest<{ id: number; workspaceId: number }, void>('/api/report-bundles/:id', {
  method: 'delete',
});

export const reportBundleVersionList = createLazyRequest<IReportBundleVersionListParams, IReportBundleVersion[]>(
  '/api/report-bundles/:bundleId/versions',
  { method: 'get' },
);

export const reportBundleVersionDetail = createMultiPathLazyRequest<
  IReportBundleVersionDetailParams,
  IReportBundleVersion
>((params) => `/api/report-bundles/${params.bundleId}/versions/${params.versionId}`, ['bundleId', 'versionId'], {
  method: 'get',
});

const createReportBundleVersionRequest = (params: ICreateReportBundleVersionRequest): Promise<IReportBundleVersion> => {
  const query = new URLSearchParams();
  query.set('workspaceId', String(params.workspaceId));
  const url = `/api/report-bundles/${params.bundleId}/versions?${query.toString()}`;
  const requestFn = createLazyRequest<unknown, IReportBundleVersion>(url, { method: 'post' });
  return requestFn({
    versionName: params.versionName,
    boundFieldsSnapshot: params.boundFieldsSnapshot,
    presetRowFiltersSnapshot: params.presetRowFiltersSnapshot,
    rowFilter: params.rowFilter,
    selectedRowKeys: params.selectedRowKeys,
  });
};

export const createReportBundleVersion = (params: unknown) => {
  validateReportBundleVersionRequest(params);
  return createReportBundleVersionRequest(params as ICreateReportBundleVersionRequest);
};

/**
 * Replaces the previous version-update endpoint, which targeted a non-existent
 * backend route. Versions are immutable; the only mutating operation the
 * backend exposes at the bundle level is updating the bundle's preset
 * filters via `PUT /api/report-bundles/{bundleId}/preset-filters`.
 *
 * The backend expects `workspaceId` as a query parameter and the JSON body
 * to be a raw `List<ViewFilter>` array (not a wrapper object). Mirrors the
 * established `exportExcel` pattern in `excelReport.ts` — build the query
 * string explicitly so the shared `createRequest` helper sends only the
 * intended body shape.
 */
export const updateReportBundlePresetFilters = (
  params: IReportBundlePresetFiltersUpdateParams,
): Promise<void> => {
  const query = new URLSearchParams();
  query.set('workspaceId', String(params.workspaceId));
  const url = `/api/report-bundles/${params.bundleId}/preset-filters?${query.toString()}`;
  const requestFn = createLazyRequest<unknown, void>(url, { method: 'put' });
  return requestFn(params.filters);
};

export const deleteReportBundleVersion = createMultiPathLazyRequest<
  IReportBundleVersionDeleteParams,
  void
>((params) => `/api/report-bundles/${params.bundleId}/versions/${params.versionId}`, ['bundleId', 'versionId'], {
  method: 'delete',
});

export const reportBundleDataViewPreview = createLazyRequest<
  IReportDataViewPreviewParams,
  IReportDataViewPreviewResult
>('/api/report-bundle-versions/:versionId/preview', { method: 'get' });

/**
 * Issues a snapshot export of an immutable bundle version. The backend expects
 * `workspaceId` and `templateId` as query parameters, and the body (when
 * present) to be the raw runtime filter array — not a wrapper object. Mirrors
 * the established `exportExcel` pattern in `excelReport.ts`: build the query
 * string explicitly so the shared `createRequest` helper sends only the
 * intended body shape.
 */
export const reportBundleVersionExport = (
  params: IReportBundleVersionExportParams,
): Promise<IReportBundleExportResult> => {
  const query = new URLSearchParams();
  query.set('workspaceId', String(params.workspaceId));
  query.set('templateId', String(params.templateId));
  const url = `/api/report-bundles/${params.bundleId}/versions/${params.versionId}/export?${query.toString()}`;
  const requestFn = createLazyRequest<unknown, IReportBundleExportResult>(url, { method: 'post' });
  return requestFn(params.runtimeFilters ?? []);
};

/**
 * The download endpoint resolves a token previously issued by
 * `reportBundleVersionExport` into a binary `.xlsx` response. It returns
 * the absolute URL string so callers can hand it to a browser navigation
 * (the shared `createRequest` helper assumes a JSON envelope and would
 * corrupt the binary payload). The token is the only access check the
 * backend performs, so the URL must be kept confidential in transit.
 */
export const downloadReportBundleVersionExport = (
  params: IReportBundleVersionExportDownloadParams,
): string => {
  const workspaceId = encodeURIComponent(String(params.workspaceId));
  const token = encodeURIComponent(params.token);
  return `/api/report-bundle-version-exports/download?workspaceId=${workspaceId}&token=${token}`;
};
