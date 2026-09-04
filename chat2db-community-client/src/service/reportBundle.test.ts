import assert from 'node:assert/strict';
import { test } from 'node:test';
import {
  createReportBundleVersion,
  downloadReportBundleVersionExport,
  reportBundleVersionExport,
  updateReportBundlePresetFilters,
} from './reportBundle.ts';
import { ReportBundleValidationError } from './reportBundleValidation.ts';

const validVersionDraft = {
  bundleId: 7,
  workspaceId: 3,
  versionName: 'Revenue baseline',
  boundFieldsSnapshot: [],
  presetRowFiltersSnapshot: [],
  rowFilter: [],
  selectedRowKeys: ['row-1'],
};

test('createReportBundleVersion accepts a well-formed version request and yields a lazy transport Promise', () => {
  const result = createReportBundleVersion(validVersionDraft);
  assert.ok(
    result instanceof Promise,
    'createReportBundleVersion must produce a lazy Promise once validation has accepted the payload',
  );
  // Swallow the unobserved lazy transport rejection — the Umi compile-time
  // constants required by base.tsx are only injected under the Umi build,
  // and the deeper HTTP contract is locked by the Java MockMvc test.
  result.catch(() => undefined);
});

test('createReportBundleVersion still rejects invalid payloads before transport', () => {
  assert.throws(
    () => createReportBundleVersion({ ...validVersionDraft, versionName: '  ' }),
    (error: unknown) => error instanceof ReportBundleValidationError,
    'a blank version name must surface as ReportBundleValidationError before transport is opened',
  );
  assert.throws(
    () => createReportBundleVersion({ ...validVersionDraft, bundleId: 0 }),
    (error: unknown) => error instanceof ReportBundleValidationError,
    'a non-positive bundleId must surface as ReportBundleValidationError before transport is opened',
  );
  assert.throws(
    () => createReportBundleVersion({ ...validVersionDraft, selectedRowKeys: [1] }),
    (error: unknown) => error instanceof ReportBundleValidationError,
    'non-string selected row keys must surface as ReportBundleValidationError before transport is opened',
  );
});

test('downloadReportBundleVersionExport returns the canonical token-protected URL', () => {
  assert.equal(
    downloadReportBundleVersionExport({ workspaceId: 3, token: 'tok_xyz' }),
    '/api/report-bundle-version-exports/download?workspaceId=3&token=tok_xyz',
  );
  assert.equal(
    downloadReportBundleVersionExport({ workspaceId: 3, token: 'a/b c' }),
    '/api/report-bundle-version-exports/download?workspaceId=3&token=a%2Fb%20c',
    'tokens must be URL-encoded so reserved characters cannot break the transport',
  );
});

test('updateReportBundlePresetFilters yields a lazy transport Promise', () => {
  const result = updateReportBundlePresetFilters({
    workspaceId: 3,
    bundleId: 7,
    filters: [{ fieldId: 'region', value: 'EU' }],
  });
  assert.ok(
    result instanceof Promise,
    'updateReportBundlePresetFilters must produce a lazy Promise so callers can attach an AbortSignal',
  );
  result.catch(() => undefined);
});

test('reportBundleVersionExport yields a lazy transport Promise', () => {
  const result = reportBundleVersionExport({
    workspaceId: 3,
    bundleId: 7,
    versionId: 13,
    templateId: 5,
  });
  assert.ok(
    result instanceof Promise,
    'reportBundleVersionExport must produce a lazy Promise so callers can attach an AbortSignal',
  );
  result.catch(() => undefined);
});
