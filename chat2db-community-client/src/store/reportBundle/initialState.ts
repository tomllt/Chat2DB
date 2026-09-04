import type { IReportBundle, IReportBundleVersion } from '@/typings/reportBundle';

export interface IReportBundleState {
  readonly list: readonly IReportBundle[];
  readonly versions: readonly IReportBundleVersion[];
  readonly current: IReportBundle | null;
  readonly currentVersion: IReportBundleVersion | null;
  readonly selectedBundleId: number | null;
  readonly selectedVersionId: number | null;
  readonly selectedRowKeys: readonly string[];
  readonly loading: boolean;
  readonly total: number;
  readonly pageNo: number;
  readonly pageSize: number;
}

export const initialState: IReportBundleState = {
  list: [],
  versions: [],
  current: null,
  currentVersion: null,
  selectedBundleId: null,
  selectedVersionId: null,
  selectedRowKeys: [],
  loading: false,
  total: 0,
  pageNo: 1,
  pageSize: 20,
};
