import assert from 'node:assert/strict';
import { test } from 'node:test';
import { createReportBundleVersion } from '../../service/reportBundle.ts';
import { ReportBundleValidationError } from '../../service/reportBundleValidation.ts';
import { initialState } from './initialState';
import { useReportBundleStore } from './store';

const versionDraft = {
  bundleId: 7,
  workspaceId: 3,
  versionName: 'Revenue baseline',
  boundFieldsSnapshot: [],
  presetRowFiltersSnapshot: [],
  rowFilter: [],
  selectedRowKeys: ['row-1'],
};

test('rejects a blank version name through createReportBundleVersion before transport', () => {
  assert.throws(
    () => createReportBundleVersion({ ...versionDraft, versionName: '  ' }),
    (error: unknown) => error instanceof ReportBundleValidationError,
  );
});

test('rejects malformed version requests before transport', () => {
  assert.throws(
    () => createReportBundleVersion({ ...versionDraft, bundleId: 0 }),
    (error: unknown) => error instanceof ReportBundleValidationError,
  );
  assert.throws(
    () => createReportBundleVersion({ ...versionDraft, selectedRowKeys: [1] }),
    (error: unknown) => error instanceof ReportBundleValidationError,
  );
});

test('page refresh preserves selections from every prior page', () => {
  useReportBundleStore.setState({ ...initialState, selectedRowKeys: ['page-1-row', 'page-2-row'] });

  useReportBundleStore.getState().retainSelectedRowKeysForPage(['page-3-row']);

  assert.deepEqual(useReportBundleStore.getState().selectedRowKeys, ['page-1-row', 'page-2-row']);
});

test('complete valid-key refresh removes only keys absent from the complete set', () => {
  useReportBundleStore.setState({ ...initialState, selectedRowKeys: ['page-1-row', 'invalid-row'] });

  useReportBundleStore.getState().reconcileSelectedRowKeys({
    validRowKeys: ['page-1-row', 'page-2-row'],
    isComplete: true,
  });

  assert.deepEqual(useReportBundleStore.getState().selectedRowKeys, ['page-1-row']);
});

test('incomplete valid-key refresh does not remove selected keys', () => {
  useReportBundleStore.setState({ ...initialState, selectedRowKeys: ['page-1-row'] });

  useReportBundleStore.getState().reconcileSelectedRowKeys({
    validRowKeys: ['page-2-row'],
    isComplete: false,
  });

  assert.deepEqual(useReportBundleStore.getState().selectedRowKeys, ['page-1-row']);
});

test('switching bundle and version clears stale version data and row selection', () => {
  useReportBundleStore.setState({
    ...initialState,
    current: { id: 7, workspaceId: 3, name: 'Old bundle' },
    currentVersion: {
      id: 11,
      bundleId: 7,
      workspaceId: 3,
      versionName: 'Old version',
      versionNo: 1,
      boundFieldsSnapshot: [],
      presetRowFiltersSnapshot: [],
      rowFilter: [],
      selectedRowKeys: ['row-1'],
    },
    selectedRowKeys: ['row-1', 'row-2'],
  });

  useReportBundleStore.getState().selectBundle(12);

  assert.equal(useReportBundleStore.getState().current, null);
  assert.equal(useReportBundleStore.getState().currentVersion, null);
  assert.equal(useReportBundleStore.getState().selectedBundleId, 12);
  assert.deepEqual(useReportBundleStore.getState().versions, []);
  assert.deepEqual(useReportBundleStore.getState().selectedRowKeys, []);

  useReportBundleStore.setState({
    ...initialState,
    currentVersion: {
      id: 11,
      bundleId: 12,
      workspaceId: 3,
      versionName: 'Old version',
      versionNo: 1,
      boundFieldsSnapshot: [],
      presetRowFiltersSnapshot: [],
      rowFilter: [],
      selectedRowKeys: ['row-1'],
    },
    selectedRowKeys: ['row-1'],
  });
  useReportBundleStore.getState().selectVersion(15);

  assert.equal(useReportBundleStore.getState().currentVersion, null);
  assert.equal(useReportBundleStore.getState().selectedVersionId, 15);
  assert.deepEqual(useReportBundleStore.getState().selectedRowKeys, []);
});
