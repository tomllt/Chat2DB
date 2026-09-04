import { useCallback, useEffect, useMemo, useState } from 'react';
import {
  Alert,
  Button,
  Empty,
  Form,
  Input,
  Modal,
  Select,
  Space,
  Switch,
  Table,
  Tabs,
  Tag,
  Typography,
  message,
} from 'antd';
import { ArrowLeft, Plus, Save, Trash2 } from 'lucide-react';
import { useLocation, useNavigate } from 'umi';

import i18n from '@/i18n';
import { FieldKanban } from '@/blocks/FieldKanban';
import { bindingsAreEqual, itemsToBindings, partitionFields, updateBoundItemBinding, type IKanbanFieldItem } from '@/blocks/FieldKanban/kanbanLogic';
import {
  reportBundleDetail,
  updateReportBundle,
} from '@/service/reportBundle';
import type { ExcelColumnBinding } from '@/service/excelReport';
import { savedQueryViewDetail } from '@/service/savedQueryView';
import { queryDatasetDetail } from '@/service/queryDataset';
import type { QueryDatasetField } from '@/typings/queryDataset';
import type { SavedQueryView, ViewFilter } from '@/typings/savedQueryView';
import type { IReportBundle } from '@/typings/reportBundle';
import { useOrgStore } from '@/store/organization';
import { useReportBundleStore } from '@/store/reportBundle/store';
import { useStyles } from './style';

const FILTER_OPERATORS = [
  { label: i18n('reportBundle.editor.filters.operators.equals'), value: 'EQ' },
  { label: i18n('reportBundle.editor.filters.operators.contains'), value: 'CONTAINS' },
  { label: i18n('reportBundle.editor.filters.operators.notEqual'), value: 'NEQ' },
];

const ALIGNMENT_OPTIONS = [
  { label: i18n('reportBundle.editor.bindingEdit.alignmentLeft'), value: 'LEFT' },
  { label: i18n('reportBundle.editor.bindingEdit.alignmentCenter'), value: 'CENTER' },
  { label: i18n('reportBundle.editor.bindingEdit.alignmentRight'), value: 'RIGHT' },
];

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

export default function ReportBundleEditor() {
  const { styles } = useStyles();
  const navigate = useNavigate();
  const location = useLocation();
  const workspaceId = useOrgStore((state) => state.curOrg?.id ?? 0);
  const setCurrent = useReportBundleStore((state) => state.setCurrent);
  const initialBundle = useReportBundleStore.getState().current;

  const bundleId = Number(new URLSearchParams(location.search).get('bundleId'));
  const [bundle, setBundle] = useState<IReportBundle | null>(initialBundle?.id === bundleId ? initialBundle : null);
  const [view, setView] = useState<SavedQueryView | null>(null);
  const [fields, setFields] = useState<QueryDatasetField[]>([]);
  const [available, setAvailable] = useState<IKanbanFieldItem[]>([]);
  const [bound, setBound] = useState<IKanbanFieldItem[]>([]);
  const [presetFilters, setPresetFilters] = useState<ViewFilter[]>([]);
  const [draftDirty, setDraftDirty] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [editingBinding, setEditingBinding] = useState<IKanbanFieldItem | null>(null);
  const [editingForm] = Form.useForm<Partial<ExcelColumnBinding>>();

  useEffect(() => {
    if (!Number.isInteger(bundleId) || bundleId <= 0) {
      setError(i18n('reportBundle.message.operationFailed'));
      return;
    }
    let active = true;
    setLoading(true);
    setError(null);
    (async () => {
      try {
        const bundleResponse = (await reportBundleDetail({ id: bundleId, workspaceId })) as IReportBundle;
        if (!active) return;
        setBundle(bundleResponse);
        setPresetFilters([...(bundleResponse.presetRowFilters || [])]);
        if (bundleResponse.queryViewId) {
          try {
            const viewResponse = (await savedQueryViewDetail({ id: bundleResponse.queryViewId })) as SavedQueryView;
            if (!active) return;
            setView(viewResponse);
            if (viewResponse.datasetId) {
              const dataset = (await queryDatasetDetail({ id: viewResponse.datasetId })) as {
                fields?: QueryDatasetField[];
              };
              if (!active) return;
              setFields(dataset.fields || []);
            } else {
              setFields([]);
            }
          } catch (viewError) {
            if (active) setError(errorText(viewError));
            setFields([]);
          }
        } else {
          setView(null);
          setFields([]);
        }
        setCurrent(bundleResponse);
      } catch (bundleError) {
        if (active) setError(errorText(bundleError));
      } finally {
        if (active) setLoading(false);
      }
    })();
    return () => {
      active = false;
    };
  }, [bundleId, setCurrent, workspaceId]);

  useEffect(() => {
    const partitioned = partitionFields(fields, bundle?.boundFields || []);
    setAvailable([...partitioned.available]);
    setBound([...partitioned.bound]);
  }, [bundle?.id, fields]);

  useEffect(() => {
    if (!fields.length) return;
    const bindingsFromState = itemsToBindings(bound);
    const draftBindings = bundle?.boundFields || [];
    if (!bindingsAreEqual(bindingsFromState, draftBindings)) {
      setDraftDirty(true);
    }
  }, [bound, bundle?.boundFields, fields.length]);

  const handleKanbanChange = useCallback(
    (next: { available: readonly IKanbanFieldItem[]; bound: readonly IKanbanFieldItem[] }) => {
      setAvailable([...next.available]);
      setBound([...next.bound]);
    },
    [],
  );

  const handleEditBinding = useCallback((item: IKanbanFieldItem) => {
    setEditingBinding(item);
    editingForm.setFieldsValue({
      displayName: item.binding.displayName || item.displayName,
      targetColumn: item.binding.targetColumn || '',
      numberFormat: item.binding.numberFormat,
      nullDisplay: item.binding.nullDisplay,
      alignment: item.binding.alignment,
      exportEnabled: item.binding.exportEnabled !== false,
    });
  }, [editingForm]);

  const handleSaveBinding = useCallback(async () => {
    if (!editingBinding) return;
    let values: Partial<ExcelColumnBinding>;
    try {
      values = await editingForm.validateFields();
    } catch (formError) {
      message.error(errorText(formError));
      return;
    }
    setBound((current) => updateBoundItemBinding(current, editingBinding.fieldId, values));
    setEditingBinding(null);
    setDraftDirty(true);
  }, [editingBinding, editingForm]);

  const handleAddFilter = useCallback(() => {
    setPresetFilters((current) => [
      ...current,
      { fieldId: bound[0]?.fieldId, operator: 'EQ', value: '' },
    ]);
    setDraftDirty(true);
  }, [bound]);

  const handleUpdateFilter = useCallback((index: number, partial: Partial<ViewFilter>) => {
    setPresetFilters((current) => current.map((filter, idx) => (idx === index ? { ...filter, ...partial } : filter)));
    setDraftDirty(true);
  }, []);

  const handleRemoveFilter = useCallback((index: number) => {
    setPresetFilters((current) => current.filter((_, idx) => idx !== index));
    setDraftDirty(true);
  }, []);

  const handleSaveDraft = useCallback(async () => {
    if (!bundle?.id) return;
    setLoading(true);
    try {
      const nextBindings = itemsToBindings(bound);
      const sanitizedFilters = presetFilters.filter((filter) => filter.fieldId && (filter.value || filter.values));
      await updateReportBundle({
        ...bundle,
        id: bundle.id,
        workspaceId,
        boundFields: nextBindings,
        presetRowFilters: sanitizedFilters,
      });
      setBundle((current) => (current ? {
        ...current,
        boundFields: nextBindings,
        presetRowFilters: sanitizedFilters,
      } : current));
      setPresetFilters(sanitizedFilters);
      setDraftDirty(false);
      message.success(i18n('reportBundle.editor.actions.saved'));
    } catch (saveError) {
      message.error(errorText(saveError));
    } finally {
      setLoading(false);
    }
  }, [bound, bundle, presetFilters, workspaceId]);

  const boundFieldOptions = useMemo(
    () => bound.map((item) => ({
      label: `${item.displayName}${item.binding.targetColumn ? ` → ${item.binding.targetColumn}` : ''}`,
      value: item.fieldId,
    })),
    [bound],
  );

  const fieldsTab = (
    <Space direction="vertical" size="middle" style={{ width: '100%' }}>
      {fields.length === 0 ? (
        <Empty description={i18n('reportBundle.editor.fields.noFields')} />
      ) : (
        <FieldKanban
          available={available}
          bound={bound}
          onChange={handleKanbanChange}
          onEdit={handleEditBinding}
        />
      )}
    </Space>
  );

  const filterTab = (
    <Space direction="vertical" size="middle" style={{ width: '100%' }}>
      <header style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <Typography.Title level={4} style={{ margin: 0 }}>{i18n('reportBundle.editor.filters.title')}</Typography.Title>
        <Button type="dashed" icon={<Plus size={14} />} onClick={handleAddFilter} disabled={bound.length === 0}>
          {i18n('reportBundle.editor.filters.add')}
        </Button>
      </header>
      {bound.length === 0 ? (
        <Alert type="info" showIcon message={i18n('reportBundle.editor.fields.noFields')} />
      ) : presetFilters.length === 0 ? (
        <Empty description={i18n('reportBundle.editor.filters.empty')} />
      ) : (
        <Table<ViewFilter>
          rowKey={(_, index) => String(index ?? 0)}
          dataSource={presetFilters}
          pagination={false}
          size="small"
          columns={[
            {
              title: i18n('reportBundle.editor.filters.field'),
              dataIndex: 'fieldId',
              render: (value: string | undefined, _, index) => (
                <Select
                  size="small"
                  style={{ minWidth: 160 }}
                  value={value}
                  placeholder={i18n('reportBundle.editor.filters.fieldPlaceholder')}
                  options={boundFieldOptions}
                  onChange={(next) => handleUpdateFilter(index ?? 0, { fieldId: next })}
                />
              ),
            },
            {
              title: i18n('reportBundle.editor.filters.operator'),
              dataIndex: 'operator',
              width: 140,
              render: (value: string | undefined, _, index) => (
                <Select
                  size="small"
                  style={{ width: '100%' }}
                  value={value || 'EQ'}
                  options={FILTER_OPERATORS}
                  onChange={(next) => handleUpdateFilter(index ?? 0, { operator: next })}
                />
              ),
            },
            {
              title: i18n('reportBundle.editor.filters.value'),
              dataIndex: 'value',
              render: (value: string | undefined, _, index) => (
                <Input
                  size="small"
                  value={value || ''}
                  placeholder={i18n('reportBundle.editor.filters.valuePlaceholder')}
                  onChange={(event) => handleUpdateFilter(index ?? 0, { value: event.target.value })}
                />
              ),
            },
            {
              title: '',
              width: 60,
              render: (_, __, index) => (
                <Button type="link" danger size="small" icon={<Trash2 size={14} />} onClick={() => handleRemoveFilter(index ?? 0)}>
                  {i18n('reportBundle.editor.filters.remove')}
                </Button>
              ),
            },
          ]}
        />
      )}
    </Space>
  );

  return (
    <main className={styles.container} aria-label={i18n('reportBundle.editor.title')}>
      <header className={styles.header}>
        <Space>
          <Button icon={<ArrowLeft size={16} />} onClick={() => navigate('/report-bundle')}>
            {i18n('common.button.close')}
          </Button>
          <div>
            <Typography.Title level={2} className={styles.title}>
              {bundle?.name || i18n('reportBundle.editor.title')}
            </Typography.Title>
            <Typography.Text type="secondary">
              {view?.name ? `${i18n('reportBundle.table.queryView')}: ${view.name}` : i18n('reportBundle.empty')}
              {draftDirty ? <Tag color="warning" style={{ marginLeft: 8 }}>●</Tag> : null}
            </Typography.Text>
          </div>
        </Space>
        <Space>
          <Button onClick={() => navigate(`/report-bundle/data-view?bundleId=${bundleId}`)}>
            {i18n('reportBundle.action.dataView')}
          </Button>
          <Button type="primary" icon={<Save size={14} />} loading={loading} disabled={!draftDirty || !bundle?.id} onClick={handleSaveDraft}>
            {i18n('reportBundle.editor.actions.save')}
          </Button>
        </Space>
      </header>
      {error ? <Alert type="error" showIcon message={error} style={{ marginBottom: 16 }} /> : null}
      <Tabs
        defaultActiveKey="fields"
        items={[
          { key: 'fields', label: i18n('reportBundle.editor.tab.fields'), children: fieldsTab },
          { key: 'filters', label: i18n('reportBundle.editor.tab.filters'), children: filterTab },
        ]}
      />
      <Modal
        open={Boolean(editingBinding)}
        title={i18n('reportBundle.editor.bindingEdit.title')}
        onCancel={() => setEditingBinding(null)}
        onOk={handleSaveBinding}
        okText={i18n('common.button.save')}
        cancelText={i18n('reportBundle.editor.bindingEdit.close')}
        destroyOnClose
      >
        <Form form={editingForm} layout="vertical" component={false}>
          <Form.Item name="displayName" label={i18n('reportBundle.editor.bindingEdit.displayName')}>
            <Input />
          </Form.Item>
          <Form.Item name="targetColumn" label={i18n('reportBundle.editor.bindingEdit.targetColumn')} rules={[{ pattern: /^[A-Za-z]+$/, message: i18n('reportBundle.editor.bindingEdit.targetColumnValidation') }]}>
            <Input style={{ textTransform: 'uppercase' }} />
          </Form.Item>
          <Form.Item name="numberFormat" label={i18n('reportBundle.editor.bindingEdit.numberFormat')}>
            <Input />
          </Form.Item>
          <Form.Item name="nullDisplay" label={i18n('reportBundle.editor.bindingEdit.nullDisplay')}>
            <Input />
          </Form.Item>
          <Form.Item name="alignment" label={i18n('reportBundle.editor.bindingEdit.alignment')}>
            <Select allowClear options={ALIGNMENT_OPTIONS} />
          </Form.Item>
          <Form.Item name="exportEnabled" label={i18n('reportBundle.editor.bindingEdit.exportEnabled')} valuePropName="checked">
            <Switch />
          </Form.Item>
        </Form>
      </Modal>
    </main>
  );
}
