import type { ICreateReportBundleVersionRequest } from '../typings/reportBundle';

export class ReportBundleValidationError extends Error {
  public constructor(message: string) {
    super(message);
    this.name = 'ReportBundleValidationError';
  }
}

export const validateReportBundleVersionRequest = (
  params: unknown,
): asserts params is ICreateReportBundleVersionRequest => {
  if (!isRecord(params)) {
    throw new ReportBundleValidationError('report bundle version request must be an object');
  }
  if (typeof params.versionName !== 'string' || !params.versionName.trim()) {
    throw new ReportBundleValidationError('versionName must not be blank');
  }
  if (!isPositiveInteger(params.workspaceId) || !isPositiveInteger(params.bundleId)) {
    throw new ReportBundleValidationError('workspaceId and bundleId must be positive integers');
  }
  if (
    !Array.isArray(params.boundFieldsSnapshot) ||
    !Array.isArray(params.presetRowFiltersSnapshot) ||
    !Array.isArray(params.rowFilter) ||
    !Array.isArray(params.selectedRowKeys) ||
    params.selectedRowKeys.some((key) => typeof key !== 'string')
  ) {
    throw new ReportBundleValidationError('version snapshots and selectedRowKeys must be arrays');
  }
};

const isRecord = (value: unknown): value is Record<string, unknown> => typeof value === 'object' && value !== null;

const isPositiveInteger = (value: unknown): value is number =>
  typeof value === 'number' && Number.isInteger(value) && value > 0;

