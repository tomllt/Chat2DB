import type { ExcelColumnBinding } from '@/service/excelReport';
import type { ViewFilter } from '@/typings/savedQueryView';
import type { ReportRowKey } from '@/typings/reportBundle';

export const resolveFilterFieldId = (
  displayedColumn: string,
  bindings: readonly ExcelColumnBinding[],
): string | undefined => {
  const binding = bindings.find((candidate) => [
    candidate.targetColumn,
    candidate.displayName,
    candidate.queryFieldId,
  ].includes(displayedColumn));
  return binding?.queryFieldId || (bindings.length ? undefined : displayedColumn);
};

export const createViewFilter = (
  displayedColumn: string,
  bindings: readonly ExcelColumnBinding[],
  operator: string,
  value: string,
): ViewFilter | undefined => {
  const fieldId = resolveFilterFieldId(displayedColumn, bindings);
  const trimmedValue = value.trim();
  if (!fieldId || !trimmedValue) return undefined;
  return { fieldId, operator, value: trimmedValue };
};

export const hasStableRowKeys = (
  rows: readonly Record<string, unknown>[],
  rowKeys: readonly ReportRowKey[],
): boolean => rows.length > 0 && rowKeys.length === rows.length && rowKeys.every((key) => key.trim().length > 0);
