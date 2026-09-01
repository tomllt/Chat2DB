import { memo, useCallback, useEffect, useMemo, useRef, useState } from 'react';
import {
  Button,
  Form,
  Input,
  Select,
  Switch,
  Table,
  Tag,
  Popconfirm,
  Space,
} from 'antd';
import { Modal } from '@chat2db/ui';
import ProTable, { ActionType, ProColumns } from '@ant-design/pro-table';
import { Plus, Pencil, Copy, Eye, XCircle, Trash2, Send, FileDown } from 'lucide-react';
import { useStyles } from './style';
import { useSavedQueryViewStore } from '@/store/savedQueryView/store';
import { SavedQueryView, ViewFilter, ViewDimension, ViewMeasure, ViewSort } from '@/typings/savedQueryView';
import i18n from '@/i18n';
import ModalTitle from '@/components/Modal/ModalTitle';
import feedback from '@/utils/feedback';
import QueryPreview from '@/blocks/QueryPreview';
import ExcelExportModal from '@/blocks/ExcelExportModal';

const STATUS_MAP: Record<string, { color: string; text: string }> = {
  PUBLISHED: { color: 'blue', text: i18n('savedQueryView.status.published') },
  DRAFT: { color: 'orange', text: i18n('savedQueryView.status.draft') },
  DISABLED: { color: 'red', text: i18n('savedQueryView.status.disabled') },
  INVALID: { color: 'grey', text: i18n('savedQueryView.status.invalid') },
};

const FILTER_TYPE_OPTIONS = [
  { label: i18n('savedQueryView.filterType.text'), value: 'TEXT' },
  { label: i18n('savedQueryView.filterType.numeric'), value: 'NUMERIC' },
  { label: i18n('savedQueryView.filterType.date'), value: 'DATE' },
  { label: i18n('savedQueryView.filterType.boolean'), value: 'BOOLEAN' },
];

const TEXT_OPERATORS = [
  { label: i18n('savedQueryView.operator.eq'), value: 'EQ' },
  { label: i18n('savedQueryView.operator.neq'), value: 'NEQ' },
  { label: i18n('savedQueryView.operator.contains'), value: 'CONTAINS' },
  { label: i18n('savedQueryView.operator.in'), value: 'IN' },
];

const NUMERIC_OPERATORS = [
  { label: i18n('savedQueryView.operator.eq'), value: 'EQ' },
  { label: i18n('savedQueryView.operator.neq'), value: 'NEQ' },
  { label: i18n('savedQueryView.operator.gt'), value: 'GT' },
  { label: i18n('savedQueryView.operator.gte'), value: 'GTE' },
  { label: i18n('savedQueryView.operator.lt'), value: 'LT' },
  { label: i18n('savedQueryView.operator.lte'), value: 'LTE' },
  { label: i18n('savedQueryView.operator.between'), value: 'BETWEEN' },
];

const DATE_OPERATORS = [
  { label: i18n('savedQueryView.operator.dateBefore'), value: 'DATE_BEFORE' },
  { label: i18n('savedQueryView.operator.dateAfter'), value: 'DATE_AFTER' },
  { label: i18n('savedQueryView.operator.dateRange'), value: 'DATE_RANGE' },
];

const BOOLEAN_OPERATORS = [
  { label: i18n('savedQueryView.operator.eq'), value: 'EQ' },
  { label: i18n('savedQueryView.operator.neq'), value: 'NEQ' },
];

const ROLE_OPTIONS = [
  { label: i18n('savedQueryView.role.row'), value: 'ROW' },
  { label: i18n('savedQueryView.role.column'), value: 'COLUMN' },
];

const SORT_DIRECTION_OPTIONS = [
  { label: i18n('savedQueryView.sort.none'), value: 'NONE' },
  { label: 'ASC', value: 'ASC' },
  { label: 'DESC', value: 'DESC' },
];

export default memo(() => {
  const { styles } = useStyles();
  const actionRef = useRef<ActionType>(null);
  const [form] = Form.useForm();

  const {
    list,
    current,
    loading,
    preview,
    total,
    pageNo,
    pageSize,
    savedQueryViewList,
    savedQueryViewDetail,
    createSavedQueryView,
    updateSavedQueryView,
    deleteSavedQueryView,
    publish,
    disable,
    copy,
    getPreview,
    setLoading,
  } = useSavedQueryViewStore((state) => ({
    list: state.list,
    current: state.current,
    loading: state.loading,
    preview: state.preview,
    total: state.total,
    pageNo: state.pageNo,
    pageSize: state.pageSize,
    savedQueryViewList: state.savedQueryViewList,
    savedQueryViewDetail: state.savedQueryViewDetail,
    createSavedQueryView: state.createSavedQueryView,
    updateSavedQueryView: state.updateSavedQueryView,
    deleteSavedQueryView: state.deleteSavedQueryView,
    publish: state.publish,
    disable: state.disable,
    copy: state.copy,
    getPreview: state.getPreview,
    setLoading: state.setLoading,
  }));

  // Modal state
  const [modalOpen, setModalOpen] = useState(false);
  const [editingRecord, setEditingRecord] = useState<SavedQueryView | null>(null);

  // Preview modal state
  const [previewOpen, setPreviewOpen] = useState(false);
  const [previewingId, setPreviewingId] = useState<number | null>(null);

  // Export modal state
  const [exportOpen, setExportOpen] = useState(false);
  const [exportViewId, setExportViewId] = useState<number | null>(null);

  // Filter editor state
  const [filters, setFilters] = useState<ViewFilter[]>([]);
  const [dimensions, setDimensions] = useState<ViewDimension[]>([]);
  const [measures, setMeasures] = useState<ViewMeasure[]>([]);
  const [sorts, setSorts] = useState<ViewSort[]>([]);

  useEffect(() => {
    savedQueryViewList({ pageNo: 1, pageSize: 20 });
  }, []);

  // ---- Load list ----
  const loadList = useCallback(
    (p?: number, ps?: number) => {
      savedQueryViewList({ pageNo: p ?? pageNo, pageSize: ps ?? pageSize });
    },
    [pageNo, pageSize, savedQueryViewList],
  );

  // ---- Open create modal ----
  const handleOpenCreate = useCallback(() => {
    setEditingRecord(null);
    setFilters([]);
    setDimensions([]);
    setMeasures([]);
    setSorts([]);
    form.resetFields();
    setModalOpen(true);
  }, [form]);

  // ---- Open edit modal ----
  const handleOpenEdit = useCallback(
    async (record: SavedQueryView) => {
      try {
        await savedQueryViewDetail(record.id!);
      } catch {
        // fallback to the row data
      }
      const detail = useSavedQueryViewStore.getState().current || record;
      setEditingRecord(detail);
      form.setFieldsValue({
        name: detail.name,
        description: detail.description,
        datasetId: detail.datasetId,
        datasetVersion: detail.datasetVersion,
        rowFields: detail.rowFields,
        columnFields: detail.columnFields,
        pageSize: detail.pageSize,
      });
      setFilters(detail.filters || []);
      setDimensions(detail.dimensions || []);
      setMeasures(detail.measures || []);
      setSorts(detail.sort || []);
      setModalOpen(true);
    },
    [form, savedQueryViewDetail],
  );

  // ---- Save (create or update) ----
  const handleSave = useCallback(async () => {
    try {
      const values = await form.validateFields();
      const payload = {
        ...values,
        filters,
        dimensions,
        measures,
        sort: sorts,
      };

      if (editingRecord?.id) {
        await updateSavedQueryView({ id: editingRecord.id, ...payload });
        feedback.success(i18n('common.tips.updateSuccess'));
      } else {
        await createSavedQueryView(payload);
        feedback.success(i18n('common.tips.createSuccess'));
      }
      setModalOpen(false);
      loadList(1);
    } catch (e: any) {
      if (e?.errorFields) return; // form validation error
      feedback.error(e?.message || i18n('queryDataset.message.operationFailed'));
    }
  }, [form, filters, dimensions, measures, sorts, editingRecord, updateSavedQueryView, createSavedQueryView, loadList]);

  // ---- Delete ----
  const handleDelete = useCallback(
    async (id: number) => {
      try {
        await deleteSavedQueryView(id);
        feedback.success(i18n('common.text.successfullyDelete'));
      } catch (e: any) {
        feedback.error(e?.message || i18n('common.text.errorDelete'));
      }
    },
    [deleteSavedQueryView],
  );

  // ---- Publish ----
  const handlePublish = useCallback(
    async (id: number) => {
      try {
        await publish(id);
        feedback.success(i18n('queryDataset.message.publishSuccess'));
        loadList();
      } catch (e: any) {
        feedback.error(e?.message || i18n('queryDataset.message.publishFailed'));
      }
    },
    [publish, loadList],
  );

  // ---- Disable ----
  const handleDisable = useCallback(
    async (id: number) => {
      try {
        await disable(id);
        feedback.success(i18n('queryDataset.message.disableSuccess'));
        loadList();
      } catch (e: any) {
        feedback.error(e?.message || i18n('queryDataset.message.disableFailed'));
      }
    },
    [disable, loadList],
  );

  // ---- Copy ----
  const handleCopy = useCallback(
    async (record: SavedQueryView) => {
      try {
        const newId = await copy(record.id!);
        feedback.success(i18n('common.button.copySuccessfully'));
        loadList(1);
        return newId;
      } catch (e: any) {
        feedback.error(e?.message || i18n('queryDataset.message.copyFailed'));
      }
    },
    [copy, loadList],
  );

  // ---- Preview ----
  const handlePreview = useCallback(
    async (id: number) => {
      setPreviewingId(id);
      setPreviewOpen(true);
      try {
        // Get the current record's filters (or empty if not yet loaded)
        const record = useSavedQueryViewStore.getState().current;
        const filterOverrides = record?.filters?.length ? JSON.stringify(record.filters) : undefined;
        await getPreview(id, 1, 20, filterOverrides);
      } catch (e: any) {
        feedback.error(e?.message || i18n('queryDataset.message.previewFailed'));
      }
    },
    [getPreview],
  );

  // ---- Open export modal ----
  const handleOpenExport = useCallback((id: number) => {
    setExportViewId(id);
    setExportOpen(true);
  }, []);

  // ---- Filter editor helpers ----
  const addFilter = useCallback(() => {
    setFilters((prev) => [
      ...prev,
      { fieldId: '', filterType: 'TEXT', operator: 'EQ', value: '', values: [] },
    ]);
  }, []);

  const removeFilter = useCallback((index: number) => {
    setFilters((prev) => prev.filter((_, i) => i !== index));
  }, []);

  const updateFilter = useCallback(
    (index: number, partial: Partial<ViewFilter>) => {
      setFilters((prev) => prev.map((f, i) => (i === index ? { ...f, ...partial } : f)));
    },
    [],
  );

  // ---- Dimension editor helpers ----
  const addDimension = useCallback(() => {
    setDimensions((prev) => [...prev, { fieldId: '', role: 'ROW', sortDirection: 'NONE' }]);
  }, []);

  const removeDimension = useCallback((index: number) => {
    setDimensions((prev) => prev.filter((_, i) => i !== index));
  }, []);

  const updateDimension = useCallback(
    (index: number, partial: Partial<ViewDimension>) => {
      setDimensions((prev) => prev.map((d, i) => (i === index ? { ...d, ...partial } : d)));
    },
    [],
  );

  // ---- Measure editor helpers ----
  const addMeasure = useCallback(() => {
    setMeasures((prev) => [...prev, { fieldId: '', aggregation: 'SUM' }]);
  }, []);

  const removeMeasure = useCallback((index: number) => {
    setMeasures((prev) => prev.filter((_, i) => i !== index));
  }, []);

  const updateMeasure = useCallback(
    (index: number, partial: Partial<ViewMeasure>) => {
      setMeasures((prev) => prev.map((m, i) => (i === index ? { ...m, ...partial } : m)));
    },
    [],
  );

  // ---- Sort editor helpers ----
  const addSort = useCallback(() => {
    setSorts((prev) => [...prev, { fieldId: '', direction: 'ASC' }]);
  }, []);

  const removeSort = useCallback((index: number) => {
    setSorts((prev) => prev.filter((_, i) => i !== index));
  }, []);

  const updateSort = useCallback(
    (index: number, partial: Partial<ViewSort>) => {
      setSorts((prev) => prev.map((s, i) => (i === index ? { ...s, ...partial } : s)));
    },
    [],
  );

  // Get operator options based on filter type
  const getOperatorOptions = useCallback((filterType?: string) => {
    switch (filterType) {
      case 'TEXT': return TEXT_OPERATORS;
      case 'NUMERIC': return NUMERIC_OPERATORS;
      case 'DATE': return DATE_OPERATORS;
      case 'BOOLEAN': return BOOLEAN_OPERATORS;
      default: return TEXT_OPERATORS;
    }
  }, []);

  // ---- ProTable columns ----
  const columns: ProColumns<SavedQueryView>[] = useMemo(
    () => [
      {
        title: i18n('common.label.name'),
        dataIndex: 'name',
        key: 'name',
        ellipsis: true,
        width: 180,
      },
      {
        title: i18n('savedQueryView.datasetId'),
        dataIndex: 'datasetId',
        key: 'datasetId',
        width: 100,
      },
      {
        title: i18n('savedQueryView.version'),
        dataIndex: 'version',
        key: 'version',
        width: 80,
      },
      {
        title: i18n('common.text.status'),
        dataIndex: 'status',
        key: 'status',
        width: 100,
        render: (_, record) => {
          const status = STATUS_MAP[record.status ?? 'DRAFT'] || STATUS_MAP.DRAFT;
          return <Tag color={status.color}>{status.text}</Tag>;
        },
      },
      {
        title: i18n('common.text.createTime'),
        dataIndex: 'gmtModified',
        key: 'gmtModified',
        width: 170,
        valueType: 'dateTime',
      },
      {
        title: i18n('common.text.action'),
        key: 'action',
        width: 300,
        fixed: 'right',
        render: (_, record) => (
          <Space size="small" wrap>
            <Button
              type="link"
              size="small"
              icon={<Pencil size={14} />}
              onClick={() => handleOpenEdit(record)}
            >
              {i18n('common.button.edit')}
            </Button>
            <Button
              type="link"
              size="small"
              icon={<Copy size={14} />}
              onClick={() => handleCopy(record)}
            >
              {i18n('common.button.copy')}
            </Button>
            <Button
              type="link"
              size="small"
              icon={<Send size={14} />}
              onClick={() => handlePublish(record.id!)}
            >
              Publish
            </Button>
            <Button
              type="link"
              size="small"
              icon={<XCircle size={14} />}
              onClick={() => handleDisable(record.id!)}
            >
              Disable
            </Button>
            <Button
              type="link"
              size="small"
              icon={<Eye size={14} />}
              onClick={() => handlePreview(record.id!)}
            >
i18n('savedQueryView.action.preview')
            </Button>
            <Button
              type="link"
              size="small"
              icon={<FileDown size={14} />}
              onClick={() => handleOpenExport(record.id!)}
            >
{i18n('savedQueryView.action.exportExcel')}
            </Button>
            <Popconfirm
              title={i18n('common.tips.delete.confirm')}
              onConfirm={() => handleDelete(record.id!)}
            >
              <Button type="link" size="small" danger icon={<Trash2 size={14} />}>
                {i18n('common.button.delete')}
              </Button>
            </Popconfirm>
          </Space>
        ),
      },
    ],
    [handleOpenEdit, handleCopy, handlePublish, handleDisable, handlePreview, handleOpenExport, handleDelete],
  );

  return (
    <>
      <ProTable<SavedQueryView>
        actionRef={actionRef}
        rowKey="id"
        columns={columns}
        dataSource={list}
        loading={loading}
        pagination={{
          total,
          current: pageNo,
          pageSize,
          showSizeChanger: true,
          showTotal: (t) => i18n('queryDataset.total', t),
          onChange: (p, ps) => loadList(p, ps),
        }}
        search={false}
        options={{
          density: false,
          fullScreen: false,
          reload: () => loadList(1),
          setting: true,
        }}
        toolBarRender={() => [
          <Button
            key="add"
            type="primary"
            icon={<Plus size={14} />}
            onClick={handleOpenCreate}
          >
            {i18n('common.button.add')}
          </Button>,
        ]}
        scroll={{ x: 900 }}
      />

      {/* Create/Edit Modal */}
      <Modal
        title={
          <ModalTitle
            iconCode="icon-table"
            title={
              editingRecord?.id
                ? i18n('savedQueryView.modal.editTitle')
                : i18n('savedQueryView.modal.createTitle')
            }
          />
        }
        maskClosable={false}
        open={modalOpen}
        onOk={handleSave}
        onCancel={() => setModalOpen(false)}
        okText={i18n('common.button.confirm')}
        cancelText={i18n('common.button.cancel')}
        destroyOnClose
        width={800}
      >
        <Form
          form={form}
          layout="vertical"
          autoComplete="off"
          style={{ maxHeight: 500, overflowY: 'auto' }}
        >
          <Form.Item
            label={i18n('common.label.name')}
            name="name"
            rules={[{ required: true, message: i18n('common.form.error.required') }]}
          >
            <Input placeholder={i18n('savedQueryView.placeholder.viewName')} />
          </Form.Item>
          <Form.Item
            label={i18n('common.label.description')}
            name="description"
          >
            <Input.TextArea placeholder={i18n('savedQueryView.placeholder.description')} rows={2} />
          </Form.Item>
          <Form.Item label={i18n('savedQueryView.datasetId')} name="datasetId">
            <Input placeholder={i18n('savedQueryView.placeholder.publishedDatasetId')} />
          </Form.Item>
          <Form.Item label={i18n('savedQueryView.datasetVersion')} name="datasetVersion">
            <Input placeholder={i18n('savedQueryView.placeholder.datasetVersion')} />
          </Form.Item>
          <Form.Item label={i18n('savedQueryView.rowFields')} name="rowFields">
            <Select
              mode="tags"
              placeholder={i18n('savedQueryView.placeholder.rowFields')}
              allowClear
            />
          </Form.Item>
          <Form.Item label={i18n('savedQueryView.columnFields')} name="columnFields">
            <Select
              mode="tags"
              placeholder={i18n('savedQueryView.placeholder.columnFields')}
              allowClear
            />
          </Form.Item>
          <Form.Item label={i18n('savedQueryView.pageSize')} name="pageSize">
            <Input type="number" placeholder={i18n('savedQueryView.placeholder.pageSize')} />
          </Form.Item>

          {/* Dimensions Editor */}
          <div style={{ marginTop: 16, marginBottom: 8 }}>
            <strong>{i18n('savedQueryView.section.dimensions')}</strong>
            <Button
              type="dashed"
              size="small"
              icon={<Plus size={12} />}
              style={{ marginLeft: 8 }}
              onClick={addDimension}
            >
              {i18n('savedQueryView.button.addDimension')}
            </Button>
          </div>
          <Table
            dataSource={dimensions}
            rowKey={(_, index) => String(index ?? 0)}
            pagination={false}
            size="small"
            bordered
            className={styles.fieldEditor}
            columns={[
              {
                title: i18n('savedQueryView.table.fieldId'),
                dataIndex: 'fieldId',
                width: 140,
                render: (_, __, index) => (
                  <Input
                    size="small"
                    value={dimensions[index]?.fieldId}
                    onChange={(e) => updateDimension(index, { fieldId: e.target.value })}
                    placeholder={i18n('savedQueryView.placeholder.fieldId')}
                  />
                ),
              },
              {
                title: i18n('savedQueryView.table.role'),
                dataIndex: 'role',
                width: 120,
                render: (_, __, index) => (
                  <Select
                    size="small"
                    value={dimensions[index]?.role || 'ROW'}
                    style={{ width: '100%' }}
                    onChange={(value) => updateDimension(index, { role: value })}
                    options={ROLE_OPTIONS}
                  />
                ),
              },
              {
                title: i18n('savedQueryView.table.sortDirection'),
                dataIndex: 'sortDirection',
                width: 120,
                render: (_, __, index) => (
                  <Select
                    size="small"
                    value={dimensions[index]?.sortDirection || 'NONE'}
                    style={{ width: '100%' }}
                    onChange={(value) => updateDimension(index, { sortDirection: value })}
                    options={SORT_DIRECTION_OPTIONS}
                  />
                ),
              },
              {
                title: 'Action',
                width: 60,
                render: (_, __, index) => (
                  <Button
                    type="link"
                    danger
                    size="small"
                    onClick={() => removeDimension(index)}
                  >
                    {i18n('common.button.delete')}
                  </Button>
                ),
              },
            ]}
          />

          {/* Measures Editor */}
          <div style={{ marginTop: 16, marginBottom: 8 }}>
            <strong>{i18n('savedQueryView.section.measures')}</strong>
            <Button
              type="dashed"
              size="small"
              icon={<Plus size={12} />}
              style={{ marginLeft: 8 }}
              onClick={addMeasure}
            >
              {i18n('savedQueryView.button.addMeasure')}
            </Button>
          </div>
          <Table
            dataSource={measures}
            rowKey={(_, index) => String(index ?? 0)}
            pagination={false}
            size="small"
            bordered
            className={styles.fieldEditor}
            columns={[
              {
                title: i18n('savedQueryView.table.fieldId'),
                dataIndex: 'fieldId',
                width: 140,
                render: (_, __, index) => (
                  <Input
                    size="small"
                    value={measures[index]?.fieldId}
                    onChange={(e) => updateMeasure(index, { fieldId: e.target.value })}
                    placeholder={i18n('savedQueryView.placeholder.fieldId')}
                  />
                ),
              },
              {
                title: i18n('savedQueryView.table.aggregation'),
                dataIndex: 'aggregation',
                width: 120,
                render: (_, __, index) => (
                  <Select
                    size="small"
                    value={measures[index]?.aggregation || 'SUM'}
                    style={{ width: '100%' }}
                    onChange={(value) => updateMeasure(index, { aggregation: value })}
                    options={[
                      { label: 'SUM', value: 'SUM' },
                      { label: 'AVG', value: 'AVG' },
                      { label: 'COUNT', value: 'COUNT' },
                      { label: 'MAX', value: 'MAX' },
                      { label: 'MIN', value: 'MIN' },
                      { label: 'COUNT_DISTINCT', value: 'COUNT_DISTINCT' },
                    ]}
                  />
                ),
              },
              {
                title: 'Action',
                width: 60,
                render: (_, __, index) => (
                  <Button
                    type="link"
                    danger
                    size="small"
                    onClick={() => removeMeasure(index)}
                  >
                    {i18n('common.button.delete')}
                  </Button>
                ),
              },
            ]}
          />

          {/* Filters Editor */}
          <div style={{ marginTop: 16, marginBottom: 8 }}>
            <strong>{i18n('savedQueryView.section.filters')}</strong>
            <Button
              type="dashed"
              size="small"
              icon={<Plus size={12} />}
              style={{ marginLeft: 8 }}
              onClick={addFilter}
            >
              {i18n('savedQueryView.button.addFilter')}
            </Button>
          </div>
          <Table
            dataSource={filters}
            rowKey={(_, index) => String(index ?? 0)}
            pagination={false}
            size="small"
            bordered
            className={styles.fieldEditor}
            columns={[
              {
                title: i18n('savedQueryView.table.fieldId'),
                dataIndex: 'fieldId',
                width: 120,
                render: (_, __, index) => (
                  <Input
                    size="small"
                    value={filters[index]?.fieldId}
                    onChange={(e) => updateFilter(index, { fieldId: e.target.value })}
                    placeholder={i18n('savedQueryView.placeholder.fieldId')}
                  />
                ),
              },
              {
                title: i18n('savedQueryView.table.filterType'),
                dataIndex: 'filterType',
                width: 110,
                render: (_, __, index) => (
                  <Select
                    size="small"
                    value={filters[index]?.filterType || 'TEXT'}
                    style={{ width: '100%' }}
                    onChange={(value) => {
                      const updates: Partial<ViewFilter> = { filterType: value, operator: 'EQ' };
                      updateFilter(index, updates);
                    }}
                    options={FILTER_TYPE_OPTIONS}
                  />
                ),
              },
              {
                title: i18n('savedQueryView.table.operator'),
                dataIndex: 'operator',
                width: 130,
                render: (_, __, index) => {
                  const filterType = filters[index]?.filterType;
                  return (
                    <Select
                      size="small"
                      value={filters[index]?.operator || 'EQ'}
                      style={{ width: '100%' }}
                      onChange={(value) => updateFilter(index, { operator: value })}
                      options={getOperatorOptions(filterType)}
                    />
                  );
                },
              },
              {
                title: i18n('savedQueryView.table.value'),
                dataIndex: 'value',
                width: 120,
                render: (_, __, index) => (
                  <Input
                    size="small"
                    value={filters[index]?.value}
                    onChange={(e) => updateFilter(index, { value: e.target.value })}
                    placeholder={i18n('savedQueryView.placeholder.fieldId')}
                  />
                ),
              },
              {
                title: 'Action',
                width: 60,
                render: (_, __, index) => (
                  <Button
                    type="link"
                    danger
                    size="small"
                    onClick={() => removeFilter(index)}
                  >
                    {i18n('common.button.delete')}
                  </Button>
                ),
              },
            ]}
          />

          {/* Sort Editor */}
          <div style={{ marginTop: 16, marginBottom: 8 }}>
            <strong>{i18n('savedQueryView.section.sort')}</strong>
            <Button
              type="dashed"
              size="small"
              icon={<Plus size={12} />}
              style={{ marginLeft: 8 }}
              onClick={addSort}
            >
              {i18n('savedQueryView.button.addSort')}
            </Button>
          </div>
          <Table
            dataSource={sorts}
            rowKey={(_, index) => String(index ?? 0)}
            pagination={false}
            size="small"
            bordered
            className={styles.fieldEditor}
            columns={[
              {
                title: i18n('savedQueryView.table.fieldId'),
                dataIndex: 'fieldId',
                width: 140,
                render: (_, __, index) => (
                  <Input
                    size="small"
                    value={sorts[index]?.fieldId}
                    onChange={(e) => updateSort(index, { fieldId: e.target.value })}
                    placeholder={i18n('savedQueryView.placeholder.fieldId')}
                  />
                ),
              },
              {
                title: i18n('savedQueryView.table.direction'),
                dataIndex: 'direction',
                width: 120,
                render: (_, __, index) => (
                  <Select
                    size="small"
                    value={sorts[index]?.direction || 'ASC'}
                    style={{ width: '100%' }}
                    onChange={(value) => updateSort(index, { direction: value })}
                    options={[
                      { label: 'ASC', value: 'ASC' },
                      { label: 'DESC', value: 'DESC' },
                    ]}
                  />
                ),
              },
              {
                title: 'Action',
                width: 60,
                render: (_, __, index) => (
                  <Button
                    type="link"
                    danger
                    size="small"
                    onClick={() => removeSort(index)}
                  >
                    {i18n('common.button.delete')}
                  </Button>
                ),
              },
            ]}
          />
        </Form>
      </Modal>

      {/* Preview Modal */}
      <Modal
        title={
          <ModalTitle
            iconCode="icon-eye"
            title={i18n('queryDataset.modal.previewTitle')}
          />
        }
        maskClosable={false}
        open={previewOpen}
        onCancel={() => {
          setPreviewOpen(false);
          setPreviewingId(null);
        }}
        footer={null}
        width={900}
        destroyOnClose
      >
        <QueryPreview
          preview={preview}
          loading={loading}
          onPageChange={(p, ps) => {
            if (previewingId != null) {
              const record = useSavedQueryViewStore.getState().current;
              const filterOverrides = record?.filters?.length ? JSON.stringify(record.filters) : undefined;
              getPreview(previewingId, p, ps, filterOverrides);
            }
          }}
        />
      </Modal>

      {/* Export Excel Modal */}
      {exportViewId != null && (
        <ExcelExportModal
          open={exportOpen}
          queryViewId={exportViewId}
          currentFilters={undefined}
          onClose={() => {
            setExportOpen(false);
            setExportViewId(null);
          }}
        />
      )}
    </>
  );
});