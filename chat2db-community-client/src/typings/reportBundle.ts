import type { ExcelColumnBinding } from '@/service/excelReport';
import type { ViewFilter } from '@/typings/savedQueryView';

export type ReportRowKey = string;

export interface IReportBundle {
  readonly id?: number;
  readonly workspaceId?: number;
  readonly name?: string;
  readonly description?: string;
  readonly queryViewId?: number;
  readonly boundFields?: readonly ExcelColumnBinding[];
  readonly presetRowFilters?: readonly ViewFilter[];
  readonly activeVersionId?: number;
  readonly ownerId?: number;
  readonly gmtCreate?: string;
  readonly gmtModified?: string;
}

export interface IReportBundleVersion {
  readonly id?: number;
  readonly workspaceId?: number;
  readonly bundleId?: number;
  readonly versionName: string;
  readonly versionNo: number;
  readonly boundFieldsSnapshot: readonly ExcelColumnBinding[];
  readonly presetRowFiltersSnapshot: readonly ViewFilter[];
  readonly rowFilter: readonly ViewFilter[];
  readonly selectedRowKeys: readonly ReportRowKey[];
  readonly ownerId?: number;
  readonly gmtCreate?: string;
  readonly gmtModified?: string;
}

export interface IReportBundleListParams {
  readonly workspaceId: number;
  readonly searchKey?: string;
  readonly pageNo: number;
  readonly pageSize: number;
}

export interface IReportBundleVersionListParams {
  readonly workspaceId: number;
  readonly bundleId: number;
}

export interface IReportBundleVersionDetailParams {
  readonly workspaceId: number;
  readonly bundleId: number;
  readonly versionId: number;
}

export interface IReportBundleVersionDeleteParams {
  readonly workspaceId: number;
  readonly bundleId: number;
  readonly versionId: number;
}

export interface IReportBundlePresetFiltersUpdateParams {
  readonly workspaceId: number;
  readonly bundleId: number;
  readonly filters: readonly ViewFilter[];
}

export interface IReportBundleVersionExportParams {
  readonly workspaceId: number;
  readonly bundleId: number;
  readonly versionId: number;
  readonly templateId: number;
  readonly runtimeFilters?: readonly ViewFilter[];
}

export interface IReportBundleVersionExportDownloadParams {
  readonly workspaceId: number;
  readonly token: string;
}

export interface IReportDataViewPreviewResult {
  readonly rows: Record<string, unknown>[];
  readonly total: number;
  readonly pageNo: number;
  readonly pageSize: number;
  readonly columns: string[];
  readonly rowKeys: string[];
}

export interface IReportDataViewPreviewParams {
  readonly workspaceId: number;
  readonly versionId: number;
  readonly pageNo: number;
  readonly pageSize: number;
  readonly filterOverrides?: string;
}

export interface IReportBundleExportResult {
  readonly downloadToken?: string;
  readonly exportId?: number;
  readonly rowCount?: number;
  readonly fileSize?: number;
  readonly status?: string;
}

export interface ICreateReportBundleVersionRequest {
  readonly workspaceId: number;
  readonly bundleId: number;
  readonly versionName: string;
  readonly boundFieldsSnapshot: readonly ExcelColumnBinding[];
  readonly presetRowFiltersSnapshot: readonly ViewFilter[];
  readonly rowFilter: readonly ViewFilter[];
  readonly selectedRowKeys: readonly ReportRowKey[];
}

export interface IUpdateReportBundleRequest extends IReportBundle {
  readonly id: number;
  readonly workspaceId: number;
}
