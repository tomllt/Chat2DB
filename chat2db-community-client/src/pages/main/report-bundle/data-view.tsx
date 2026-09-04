import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { Alert, Button, Empty, Input, Modal, Popover, Select, Space, Table, Tag, Typography, message } from 'antd';
import { ArrowLeft, Filter, RefreshCw, Save } from 'lucide-react';
import { useLocation, useNavigate } from 'umi';

import i18n from '@/i18n';
import {
  createReportBundleVersion,
  reportBundleDataViewPreview,
  reportBundleDetail,
  reportBundleVersionList,
} from '@/service/reportBundle';
import type { ViewFilter } from '@/typings/savedQueryView';
import type { IReportBundle, IReportBundleVersion, IReportDataViewPreviewResult, ReportRowKey } from '@/typings/reportBundle';
import { useOrgStore } from '@/store/organization';
import { useReportBundleStore } from '@/store/reportBundle/store';
import { createViewFilter, hasStableRowKeys, resolveFilterFieldId } from './dataViewLogic';
import { useStyles } from './style';

const FILTER_OPERATORS = [
  { label: i18n('reportBundle.editor.filters.operators.equals'), value: 'EQ' },
  { label: i18n('reportBundle.editor.filters.operators.contains'), value: 'CONTAINS' },
  { label: i18n('reportBundle.editor.filters.operators.notEqual'), value: 'NEQ' },
];

interface IPreviewResponse extends IReportDataViewPreviewResult {
  data?: IReportDataViewPreviewResult;
}

const errorText = (error: unknown): string => {
  if (typeof error === 'string') return error;
  if (!error || typeof error !== 'object') return '';
  const value = error as { message?: unknown; errorMessage?: unknown };
  if (typeof value.message === 'object' && value.message) {
    const nested = value.message as { message?: unknown; errorMessage?: unknown };
    return String(nested.message || nested.errorMessage || '');
  }
  return String(value.message || value.errorMessage || '');
};

const isDuplicateVersionError = (error: unknown) => {
  const errorMessage = errorText(error).toLowerCase();
  return errorMessage.includes('ex_020') || errorMessage.includes('already exists');
};

const mergeFilters = (presetFilters: readonly ViewFilter[], runtimeFilters: readonly ViewFilter[]) => {
  const merged = new Map<string, ViewFilter>();
  [...presetFilters, ...runtimeFilters].forEach((filter) => {
    if (filter.fieldId) merged.set(filter.fieldId, { ...filter });
  });
  return [...merged.values()];
};

export default function ReportBundleDataView() {
  const { styles } = useStyles();
  const navigate = useNavigate();
  const location = useLocation();
  const workspaceId = useOrgStore((state) => state.curOrg?.id ?? 0);
  const selectedBundleId = useReportBundleStore((state) => state.selectedBundleId);
  const selectedVersionId = useReportBundleStore((state) => state.selectedVersionId);
  const selectedRowKeys = useReportBundleStore((state) => state.selectedRowKeys);
  const store = useReportBundleStore();
  const bundleId = Number(new URLSearchParams(location.search).get('bundleId'));
  const requestedVersionId = Number(new URLSearchParams(location.search).get('versionId'));
  const [bundle, setBundle] = useState<IReportBundle | null>(useReportBundleStore.getState().current);
  const [versions, setVersions] = useState<IReportBundleVersion[]>(
    [...useReportBundleStore.getState().versions],
  );
  const [version, setVersion] = useState<IReportBundleVersion | null>(useReportBundleStore.getState().currentVersion);
  const [rows, setRows] = useState<Record<string, unknown>[]>([]);
  const [columns, setColumns] = useState<string[]>([]);
  const [rowKeys, setRowKeys] = useState<ReportRowKey[]>([]);
  const [total, setTotal] = useState(0);
  const [pageNo, setPageNo] = useState(1);
  const [pageSize, setPageSize] = useState(20);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [filterField, setFilterField] = useState<string | null>(null);
  const [filterOperator, setFilterOperator] = useState('EQ');
  const [filterValue, setFilterValue] = useState('');
  const [runtimeFilters, setRuntimeFilters] = useState<ViewFilter[]>([]);
  const effectiveFilters = useMemo(
    () => mergeFilters(version?.presetRowFiltersSnapshot || [], runtimeFilters),
    [runtimeFilters, version?.presetRowFiltersSnapshot],
  );
  const [saveOpen, setSaveOpen] = useState(false);
  const [versionName, setVersionName] = useState('');
  const [saveLoading, setSaveLoading] = useState(false);
  const previewRequestRef = useRef<AbortController | null>(null);
  const previewGenerationRef = useRef(0);

  useEffect(() => {
    if (selectedBundleId !== bundleId) {
      useReportBundleStore.getState().selectBundle(bundleId);
      return;
    }
    if (selectedVersionId !== version?.id) {
      useReportBundleStore.setState({
        selectedVersionId: version?.id || null,
        currentVersion: version,
        selectedRowKeys: [...(version?.selectedRowKeys || [])],
      });
    }
  }, [bundleId, selectedBundleId, selectedVersionId, version]);

  useEffect(() => {
    if (!Number.isInteger(bundleId) || bundleId <= 0) {
      setError(i18n('reportBundle.message.operationFailed'));
      return;
    }
    let active = true;
    setError(null);
    Promise.all([
      store.current?.id === bundleId
        ? Promise.resolve(store.current)
        : reportBundleDetail({ id: bundleId, workspaceId }),
      reportBundleVersionList({ bundleId, workspaceId }),
    ]).then(([loadedBundle, loadedVersions]) => {
      if (!active) return;
      const nextBundle = loadedBundle as IReportBundle;
      const nextVersions = (loadedVersions as IReportBundleVersion[]) || [];
      const nextVersion = nextVersions.find((item) => item.id === requestedVersionId)
        || nextVersions.find((item) => item.id === nextBundle.activeVersionId)
        || nextVersions[0]
        || null;
      const currentStore = useReportBundleStore.getState();
      const persistedSelection = currentStore.selectedRowKeys;
      const hasPersistedSelection = currentStore.selectedBundleId === bundleId
        && currentStore.selectedVersionId === nextVersion?.id;
      const nextSelectedRowKeys = hasPersistedSelection ? persistedSelection : (nextVersion?.selectedRowKeys || []);
      setBundle(nextBundle);
      setVersions(nextVersions);
      setVersion(nextVersion);
      if (currentStore.selectedBundleId !== bundleId) currentStore.selectBundle(bundleId);
      currentStore.setCurrent(nextBundle);
      currentStore.setVersions(nextVersions);
      if (currentStore.selectedVersionId !== nextVersion?.id) {
        useReportBundleStore.setState({
          selectedVersionId: nextVersion?.id || null,
          currentVersion: nextVersion,
          selectedRowKeys: [...nextSelectedRowKeys],
        });
      } else {
        currentStore.setCurrentVersion(nextVersion);
        currentStore.setSelectedRowKeys(nextSelectedRowKeys);
      }
    })
      .catch((loadError) => { if (active) setError(errorText(loadError)); });
    return () => { active = false; };
  }, [bundleId, requestedVersionId, workspaceId]);

  const loadPreview = useCallback(async () => {
    if (!version?.id) return;
    previewRequestRef.current?.abort();
    const controller = new AbortController();
    previewRequestRef.current = controller;
    const generation = ++previewGenerationRef.current;
    setLoading(true);
    setError(null);
    try {
      const response = await reportBundleDataViewPreview({
        workspaceId,
        versionId: version.id,
        pageNo,
        pageSize,
         filterOverrides: effectiveFilters.length ? JSON.stringify(effectiveFilters) : undefined,
       }, { signal: controller.signal });
       if (generation !== previewGenerationRef.current) return;
       const result = (response as IPreviewResponse).data || response as IReportDataViewPreviewResult;
       setRows(result.rows || []);
       setColumns(result.columns || []);
       setRowKeys(result.rowKeys || []);
       setTotal(result.total || 0);
     } catch (previewError) {
       if (generation === previewGenerationRef.current && !(previewError instanceof DOMException && previewError.name === 'AbortError')) {
         setError(errorText(previewError));
       }
     } finally {
       if (generation === previewGenerationRef.current) setLoading(false);
     }
  }, [effectiveFilters, pageNo, pageSize, version?.id, workspaceId]);

  useEffect(() => {
    loadPreview();
    return () => {
      previewGenerationRef.current += 1;
      previewRequestRef.current?.abort();
    };
  }, [loadPreview]);

  useEffect(() => {
    setRuntimeFilters([...(version?.rowFilter || [])]); setPageNo(1);
  }, [version?.id]);

  const selectVersion = (id: number) => {
     const next = versions.find((item) => item.id === id) || null;
     setVersion(next);
     setRuntimeFilters([]);
     setFilterField(null);
     useReportBundleStore.setState({
       selectedVersionId: id,
       currentVersion: next,
       selectedRowKeys: [...(next?.selectedRowKeys || [])],
     });
     navigate(`/report-bundle/data-view?bundleId=${bundleId}&versionId=${id}`);
  };

  const applyFilter = () => {
    if (!filterField) return;
    const nextFilter = createViewFilter(filterField, version?.boundFieldsSnapshot || [], filterOperator, filterValue);
    if (!nextFilter) return;
    setRuntimeFilters((current) => [...current.filter((item) => item.fieldId !== nextFilter.fieldId), nextFilter]);
    setPageNo(1);
    setFilterField(null);
    setFilterValue('');
  };

  const boundFields = version?.boundFieldsSnapshot || [];
  const stableRowKeys = hasStableRowKeys(rows, rowKeys);
  const rowKey = (record: Record<string, unknown>, index: number) => {
    if (rowKeys[index]) return rowKeys[index];
    if (record.__row_key) return String(record.__row_key);
    return `__unstable:${pageNo}:${index}`;
  };
  const pageKeys = rows.map(rowKey);
  const selection = useMemo(() => ({
    selectedRowKeys: stableRowKeys ? selectedRowKeys.filter((key) => pageKeys.includes(key)) : [],
    onChange: stableRowKeys
      ? (checkedPageKeys: React.Key[]) => {
        const checked = checkedPageKeys.map(String);
        const nextKeys = [...selectedRowKeys.filter((key) => !pageKeys.includes(key)), ...checked];
        useReportBundleStore.getState().setSelectedRowKeys(nextKeys);
      }
      : () => {},
    getCheckboxProps: () => ({ disabled: !stableRowKeys }),
  }), [pageKeys, selectedRowKeys, stableRowKeys]);

  const tableColumns = useMemo(() => columns.map((column) => {
    const sourceFieldId = resolveFilterFieldId(column, boundFields) || column;
    return {
      title: <Popover open={filterField === column} onOpenChange={(open) => { if (open) setFilterField(column); }} content={filterField === column ? <Space direction="vertical"><Select value={filterOperator} options={FILTER_OPERATORS} onChange={setFilterOperator} /><Input autoFocus value={filterValue} onChange={(event) => setFilterValue(event.target.value)} onPressEnter={applyFilter} placeholder={sourceFieldId} /><Button type="primary" onClick={applyFilter}>{i18n('common.button.confirm')}</Button></Space> : null}><Button type="text" icon={<Filter size={13} />}>{column}</Button></Popover>,
      dataIndex: column,
      key: column,
      render: (value: unknown) => value == null ? '-' : String(value),
    };
  }), [boundFields, columns, filterField, filterOperator, filterValue]);

  const saveAsNewVersion = async () => {
    const name = versionName.trim();
    if (!name) { message.error(i18n('reportBundle.validation.nameRequired')); return; }
    if (!bundleId || !bundle) return;
    setSaveLoading(true);
    try {
      const created = await createReportBundleVersion({
        workspaceId,
        bundleId,
        versionName: name,
        boundFieldsSnapshot: [...(version?.boundFieldsSnapshot || [])],
        presetRowFiltersSnapshot: [...(version?.presetRowFiltersSnapshot || [])],
        rowFilter: [...runtimeFilters],
        selectedRowKeys: [...selectedRowKeys],
      });
      const newVersion = created as IReportBundleVersion;
      const currentStore = useReportBundleStore.getState();
      const reconciledVersions = currentStore.versions.some((item) => item.id === newVersion.id)
        ? currentStore.versions
        : [...currentStore.versions, newVersion];
       currentStore.setVersions(reconciledVersions);
       setVersions([...reconciledVersions]);
       setVersion(newVersion);
       useReportBundleStore.setState({
         selectedVersionId: newVersion.id || null,
         currentVersion: newVersion,
         selectedRowKeys: [...(newVersion.selectedRowKeys || [])],
       });
       setSaveOpen(false);
      setVersionName('');
      message.success(i18n('reportBundle.message.saved'));
      navigate(`/report-bundle/data-view?bundleId=${bundleId}&versionId=${newVersion.id}`);
    } catch (saveError) {
      message.error(isDuplicateVersionError(saveError)
        ? i18n('reportBundle.message.versionDuplicate')
        : errorText(saveError));
    } finally { setSaveLoading(false); }
  };

  if (error && !bundle) return <main className={styles.container}><Alert type="error" showIcon message={error} action={<Button icon={<RefreshCw size={14} />} onClick={() => window.location.reload()}>{i18n('common.button.refresh')}</Button>} /></main>;

  return <main className={styles.container} aria-label={i18n('reportBundle.action.dataView')}>
    <header className={styles.header}>
      <Space><Button icon={<ArrowLeft size={16} />} onClick={() => navigate('/report-bundle')}>{i18n('common.button.close')}</Button><div><Typography.Title level={2} className={styles.title}>{bundle?.name || i18n('reportBundle.action.dataView')}</Typography.Title><Typography.Text type="secondary">{version ? `${version.versionName} · v${version.versionNo}` : i18n('reportBundle.empty')}</Typography.Text></div></Space>
      <Space><Select aria-label={i18n('reportBundle.dataView.versionLabel')} value={version?.id} options={versions.map((item) => ({ value: item.id, label: `${item.versionName} · v${item.versionNo}` }))} onChange={selectVersion} placeholder={i18n('reportBundle.empty')} /><Button type="primary" icon={<Save size={16} />} disabled={!version} onClick={() => setSaveOpen(true)}>{i18n('common.button.save')}</Button></Space>
    </header>
    {error && <Alert type="error" showIcon message={error} action={<Button onClick={loadPreview}>{i18n('common.button.refresh')}</Button>} style={{ marginBottom: 16 }} />}
     <Space style={{ marginBottom: 16 }}><Tag color="blue">{i18n('reportBundle.dataView.selected', selectedRowKeys.length)}</Tag>{effectiveFilters.map((filter) => <Tag key={filter.fieldId}>{filter.fieldId} {filter.operator} {filter.value}</Tag>)}{!stableRowKeys && rows.length > 0 ? <Tag color="warning">{i18n('reportBundle.warning.rowKeysUnavailable')}</Tag> : null}</Space>
    <div className={styles.tableCard}>{!loading && !error && !version ? <Empty description={i18n('reportBundle.empty')} /> : <Table rowKey={rowKey} loading={loading} columns={tableColumns} dataSource={rows} rowSelection={selection} locale={{ emptyText: loading ? i18n('common.button.execute') : i18n('reportBundle.empty') }} pagination={{ current: pageNo, pageSize, total, showSizeChanger: true, onChange: (nextPage, nextSize) => { setPageNo(nextPage); setPageSize(nextSize); } }} scroll={{ x: 'max-content' }} />}</div>
    <Modal open={saveOpen} title={i18n('reportBundle.modal.createTitle')} okText={i18n('common.button.save')} confirmLoading={saveLoading} onCancel={() => setSaveOpen(false)} onOk={saveAsNewVersion} destroyOnClose><Typography.Paragraph>{i18n('reportBundle.field.name')}</Typography.Paragraph><Input autoFocus value={versionName} onChange={(event) => setVersionName(event.target.value)} onPressEnter={saveAsNewVersion} placeholder={i18n('reportBundle.placeholder.name')} /></Modal>
  </main>;
}
