import { memo, useCallback, useEffect, useMemo, useRef, useState } from 'react';
import {
  Button,
  Form,
  Input,
  InputNumber,
  Select,
  Switch,
  Table,
  Tabs,
  Tag,
  Upload,
  Popconfirm,
  Space,
  Typography,
  type UploadFile,
} from 'antd';
import { Modal } from '@chat2db/ui';
import ProTable, { ActionType, ProColumns } from '@ant-design/pro-table';
import { Plus, Pencil, Copy, ShieldCheck, Trash2, UploadCloud, FileDown } from 'lucide-react';
import { useStyles } from './style';
import {
  ExcelReportTemplate,
  SheetConfig,
  ExcelColumnBinding,
  excelTemplateList,
  excelTemplateDetail,
  uploadExcelTemplate,
  updateSheetConfigs,
  updateFieldBindings,
  validateExcelTemplate,
  copyExcelTemplate,
  deleteExcelTemplate,
} from '@/service/excelReport';
import { savedQueryViewList } from '@/service/savedQueryView';
import { SavedQueryView } from '@/typings/savedQueryView';
import { QueryDataset, QueryDatasetField } from '@/typings/queryDataset';
import { queryDatasetDetail } from '@/service/queryDataset';
import i18n from '@/i18n';
import ModalTitle from '@/components/Modal/ModalTitle';
import feedback from '@/utils/feedback';
import ExcelExportModal from '@/blocks/ExcelExportModal';

const STATUS_MAP: Record<string, { color: string; text: string }> = {
  VALID: { color: 'green', text: i18n('excelReportTemplate.status.valid') },
  INVALID: { color: 'red', text: i18n('excelReportTemplate.status.invalid') },
  DISABLED: { color: 'default', text: i18n('excelReportTemplate.status.disabled') },
};

const EMPTY_RESULT_OPTIONS = [
  { label: i18n('excelReportTemplate.emptyResult.emptySheet'), value: 'EMPTY_SHEET' },
  { label: i18n('excelReportTemplate.emptyResult.skipSheet'), value: 'SKIP_SHEET' },
  { label: i18n('excelReportTemplate.emptyResult.error'), value: 'ERROR' },
];

const ALIGNMENT_OPTIONS = [
  { label: i18n('excelReportTemplate.alignment.left'), value: 'LEFT' },
  { label: i18n('excelReportTemplate.alignment.center'), value: 'CENTER' },
  { label: i18n('excelReportTemplate.alignment.right'), value: 'RIGHT' },
];

/** The list response returned by the excel-report-templates endpoint. */
interface TemplateListResponse {
  data: ExcelReportTemplate[];
  pageNo: number;
  pageSize: number;
  total: number;
}

/** Raw shape of a validation finding returned by the validate endpoint. */
interface ValidationFinding {
  errorCode?: string;
  message?: string;
  sheetName?: string;
  cellRange?: string;
  warning?: boolean;
}

export default memo(() => {
  const { styles } = useStyles();
  const actionRef = useRef<ActionType>(null);

  // ---- List state ----
  const [list, setList] = useState<ExcelReportTemplate[]>([]);
  const [total, setTotal] = useState(0);
  const [pageNo, setPageNo] = useState(1);
  const [pageSize, setPageSize] = useState(20);
  const [loading, setLoading] = useState(false);

  // ---- View options (published views for create/bindings) ----
  const [viewList, setViewList] = useState<SavedQueryView[]>([]);
  const [viewFieldMap, setViewFieldMap] = useState<Record<number, QueryDatasetField[]>>({});

  // ---- Create modal state ----
  const [createOpen, setCreateOpen] = useState(false);
  const [createForm] = Form.useForm();
  const [fileList, setFileList] = useState<UploadFile[]>([]);

  // ---- Edit modal state ----
  const [editOpen, setEditOpen] = useState(false);
  const [editingRecord, setEditingRecord] = useState<ExcelReportTemplate | null>(null);

  // ---- Sheet config editor state ----
  const [sheetConfigs, setSheetConfigs] = useState<SheetConfig[]>([]);

  // ---- Field binding editor state (per sheet, indexed by sheetName) ----
  const [activeSheet, setActiveSheet] = useState<string>('');
  const [bindings, setBindings] = useState<Record<string, ExcelColumnBinding[]>>({});

  // ---- Validate modal state ----
  const [validateOpen, setValidateOpen] = useState(false);
  const [validationErrors, setValidationErrors] = useState<ValidationFinding[]>([]);
  const [validating, setValidating] = useState(false);

  // ---- Export modal state (uses shared component) ----
  const [exportOpen, setExportOpen] = useState(false);
  const [exportTemplate, setExportTemplate] = useState<ExcelReportTemplate | null>(null);

  // ---- Bindings edit form state ----
  const [bindingForm] = Form.useForm();

  // ---- Load template list ----
  const loadList = useCallback(
    async (p?: number, ps?: number) => {
      setLoading(true);
      try {
        const res = (await excelTemplateList({
          pageNo: p ?? pageNo,
          pageSize: ps ?? pageSize,
        })) as unknown as TemplateListResponse;
        setList(res.data || []);
        setTotal(res.total || 0);
        setPageNo(res.pageNo ?? p ?? pageNo);
        setPageSize(res.pageSize ?? ps ?? pageSize);
      } catch (e: any) {
        feedback.error(e?.message || i18n('excelReportTemplate.message.operationFailed'));
      } finally {
        setLoading(false);
      }
    },
    [pageNo, pageSize],
  );

  // ---- Load published query views ----
  const loadViews = useCallback(async () => {
    try {
      const res = await savedQueryViewList({ pageNo: 1, pageSize: 100 });
      setViewList(res.data || []);
    } catch (e: any) {
      feedback.error(e?.message || i18n('excelReportTemplate.message.loadQueryViewsFailed'));
    }
  }, []);

  useEffect(() => {
    loadList(1, 20);
    loadViews();
  }, [loadList, loadViews]);

  // ---- Resolve view name (falls back to linked view id) ----
  const getViewName = useCallback(
    (viewId?: number) => {
      if (viewId == null) return '-';
      const view = viewList.find((v) => v.id === viewId);
      return view?.name ?? String(viewId);
    },
    [viewList],
  );

  // ---- Resolve dataset field options for a view (for bindings) ----
  const getViewFieldOptions = useCallback(
    (viewId?: number) => {
      const fields = viewId != null ? viewFieldMap[viewId] || [] : [];
      return fields
        .filter((f) => f.fieldId)
        .map((f) => ({
          label: f.displayName || f.fieldId || f.sourceColumn || '',
          value: f.fieldId as string,
        }));
    },
    [viewFieldMap],
  );

  // ---- Load the dataset fields behind a view (for binding selector) ----
  const loadViewFields = useCallback(
    async (viewId?: number) => {
      if (viewId == null || viewFieldMap[viewId]) return;
      try {
        const view = viewList.find((v) => v.id === viewId);
        if (!view?.datasetId) return;
        const dataset = await queryDatasetDetail({ id: view.datasetId });
        setViewFieldMap((prev) => ({
          ...prev,
          [viewId]: (dataset as QueryDataset)?.fields || [],
        }));
      } catch {
        // Non-fatal: binding selector remains empty until fields can be resolved
      }
    },
    [viewList, viewFieldMap],
  );

  // ---- Open edit modal ----
  const handleOpenEdit = useCallback(
    async (record: ExcelReportTemplate) => {
      let detail = record;
      try {
        if (record.id != null) {
          const res = await excelTemplateDetail({ id: record.id });
          if (res) detail = res;
        }
      } catch {
        // fall back to row data
      }
      setEditingRecord(detail);
      const configs = detail.sheetConfigs || [];
      setSheetConfigs(configs);
      const map: Record<string, ExcelColumnBinding[]> = {};
      configs.forEach((c) => {
        if (c.sheetName) map[c.sheetName] = c.fieldBindings || [];
      });
      setBindings(map);
      const firstSheet = configs[0]?.sheetName || '';
      setActiveSheet(firstSheet);
      if (firstSheet) {
        bindingForm.setFieldsValue(map[firstSheet] || []);
      }
      loadViewFields(detail.queryViewId);
      setEditOpen(true);
    },
    [bindingForm, loadViewFields],
  );

  // ---- Open create modal ----
  const handleOpenCreate = useCallback(() => {
    createForm.resetFields();
    setFileList([]);
    setCreateOpen(true);
  }, [createForm]);

  // ---- Create submit (upload) ----
  const handleCreate = useCallback(async () => {
    let values: any;
    try {
      values = await createForm.validateFields();
    } catch (e: any) {
      if (e?.errorFields) return;
      feedback.error(e?.message || i18n('excelReportTemplate.message.validationFailed'));
      return;
    }
    const file = fileList[0]?.originFileObj;
    if (!file) {
      feedback.error(i18n('excelReportTemplate.message.selectXlsx'));
      return;
    }
    try {
      const id = await uploadExcelTemplate({
        workspaceId: values.workspaceId || 0,
        name: values.name,
        description: values.description || '',
        queryViewId: values.queryViewId,
        file: file as File,
      });
      feedback.success(i18n('common.tips.createSuccess'));
      setCreateOpen(false);
      setFileList([]);
      createForm.resetFields();
      if (id != null) {
        await handleOpenEdit({ id, queryViewId: values.queryViewId } as ExcelReportTemplate);
      } else {
        loadList(1);
      }
    } catch (e: any) {
      feedback.error(e?.message || i18n('excelReportTemplate.message.uploadFailed'));
    }
  }, [createForm, fileList, handleOpenEdit, loadList]);

  // ---- Save sheet configs ----
  const handleSaveSheetConfigs = useCallback(async () => {
    if (!editingRecord?.id) return;
    try {
      await updateSheetConfigs({ id: editingRecord.id, sheetConfigs });
      feedback.success(i18n('common.tips.updateSuccess'));
      loadList();
    } catch (e: any) {
      feedback.error(e?.message || i18n('excelReportTemplate.message.saveSheetConfigsFailed'));
    }
  }, [editingRecord, sheetConfigs, loadList]);

  // ---- Update a single sheet config row ----
  const updateSheetConfig = useCallback((index: number, partial: Partial<SheetConfig>) => {
    setSheetConfigs((prev) => prev.map((c, i) => (i === index ? { ...c, ...partial } : c)));
  }, []);

  // ---- Save field bindings for the active sheet ----
  const handleSaveBindings = useCallback(async () => {
    if (!editingRecord?.id || !activeSheet) return;
    const rows: ExcelColumnBinding[] = [];
    try {
      const values = await bindingForm.validateFields();
      const entries = Array.isArray(values) ? values : [];
      entries.forEach((entry: any) => {
        if (!entry?.queryFieldId || !entry?.targetColumn) return;
        rows.push({
          queryFieldId: entry.queryFieldId,
          targetColumn: String(entry.targetColumn).toUpperCase(),
          displayName: entry.displayName,
          numberFormat: entry.numberFormat,
          nullDisplay: entry.nullDisplay,
          alignment: entry.alignment,
          exportEnabled: entry.exportEnabled !== false,
        });
      });
    } catch (e: any) {
      if (e?.errorFields) {
        feedback.error(i18n('excelReportTemplate.message.fixBindingErrors'));
        return;
      }
      feedback.error(e?.message || i18n('excelReportTemplate.message.bindingValidationFailed'));
      return;
    }
    try {
      await updateFieldBindings({
        id: editingRecord.id,
        sheetName: activeSheet,
        bindings: rows,
      });
      setBindings((prev) => ({ ...prev, [activeSheet]: rows }));
      feedback.success(i18n('common.tips.updateSuccess'));
    } catch (e: any) {
      feedback.error(e?.message || i18n('excelReportTemplate.message.saveFieldBindingsFailed'));
    }
  }, [editingRecord, activeSheet, bindingForm]);

  // ---- Validate ----
  const handleValidate = useCallback(
    async (id: number) => {
      setValidating(true);
      setValidateOpen(true);
      setValidationErrors([]);
      try {
        const res = await validateExcelTemplate({ id });
        setValidationErrors(Array.isArray(res) ? (res as unknown as ValidationFinding[]) : []);
      } catch (e: any) {
        setValidationErrors([{ message: e?.message || i18n('excelReportTemplate.message.validationFailed') }]);
      } finally {
        setValidating(false);
      }
    },
    [],
  );

  // ---- Copy ----
  const handleCopy = useCallback(
    async (record: ExcelReportTemplate) => {
      try {
        await copyExcelTemplate({ id: record.id! });
        feedback.success(i18n('common.button.copySuccessfully'));
        loadList(1);
      } catch (e: any) {
        feedback.error(e?.message || i18n('excelReportTemplate.message.copyFailed'));
      }
    },
    [loadList],
  );

  // ---- Delete ----
  const handleDelete = useCallback(
    async (id: number) => {
      try {
        await deleteExcelTemplate({ id });
        feedback.success(i18n('common.text.successfullyDelete'));
        loadList(1);
      } catch (e: any) {
        feedback.error(e?.message || i18n('common.text.errorDelete'));
      }
    },
    [loadList],
  );

  // ---- Open export modal (shared component handles the export call) ----
  const handleOpenExport = useCallback((record: ExcelReportTemplate) => {
    setExportTemplate(record);
    setExportOpen(true);
  }, []);

  // ---- Upload validation helpers ----
  const beforeUpload = useCallback((file: File) => {
    const isXlsx = file.name.toLowerCase().endsWith('.xlsx');
    if (!isXlsx) {
      feedback.error(i18n('excelReportTemplate.message.onlyXlsx'));
      return Upload.LIST_IGNORE;
    }
    return false; // block auto upload; the form submit triggers the real upload
  }, []);

  const handleUploadChange = useCallback(
    ({ fileList: newList }: { fileList: UploadFile[] }) => {
      setFileList(newList.slice(-1));
    },
    [],
  );

  // ---- Build binding rows for the active sheet ----
  const activeBindings = useMemo(() => (activeSheet ? bindings[activeSheet] || [] : []), [bindings, activeSheet]);

  const addBinding = useCallback(() => {
    if (!activeSheet) return;
    setBindings((prev) => ({
      ...prev,
      [activeSheet]: [...(prev[activeSheet] || []), { exportEnabled: true } as ExcelColumnBinding],
    }));
  }, [activeSheet]);

  const removeBinding = useCallback(
    (index: number) => {
      if (!activeSheet) return;
      setBindings((prev) => ({
        ...prev,
        [activeSheet]: (prev[activeSheet] || []).filter((_, i) => i !== index),
      }));
    },
    [activeSheet],
  );

  const updateBinding = useCallback(
    (index: number, partial: Partial<ExcelColumnBinding>) => {
      if (!activeSheet) return;
      setBindings((prev) => {
        const rows = prev[activeSheet] || [];
        const next = rows.map((b, i) => (i === index ? { ...b, ...partial } : b));
        return { ...prev, [activeSheet]: next };
      });
    },
    [activeSheet],
  );

  // ---- Tabs items for edit modal ----
  const editTabItems = useMemo(
    () => [
      {
        key: 'sheet-configs',
        label: i18n('excelReportTemplate.sheetConfig.title'),
        children: (
          <div>
            <Table<SheetConfig>
              dataSource={sheetConfigs}
              rowKey="sheetName"
              pagination={false}
              size="small"
              bordered
              className={styles.bindingTable}
              columns={[
                {
                  title: i18n('excelReportTemplate.sheetConfig.sheetName'),
                  dataIndex: 'sheetName',
                  width: 130,
                  render: (_, record) => <Typography.Text strong>{record.sheetName}</Typography.Text>,
                },
                {
                  title: i18n('excelReportTemplate.sheetConfig.dataStartRow'),
                  dataIndex: 'dataStartRow',
                  width: 110,
                  render: (_, __, index) => (
                    <InputNumber
                      size="small"
                      min={0}
                      style={{ width: '100%' }}
                      value={sheetConfigs[index]?.dataStartRow}
                      onChange={(value) => updateSheetConfig(index, { dataStartRow: value ?? 0 })}
                    />
                  ),
                },
                {
                  title: i18n('excelReportTemplate.sheetConfig.dataStartColumn'),
                  dataIndex: 'dataStartColumn',
                  width: 120,
                  render: (_, __, index) => (
                    <InputNumber
                      size="small"
                      min={0}
                      style={{ width: '100%' }}
                      value={sheetConfigs[index]?.dataStartColumn}
                      onChange={(value) => updateSheetConfig(index, { dataStartColumn: value ?? 0 })}
                    />
                  ),
                },
                {
                  title: i18n('excelReportTemplate.sheetConfig.freezeRows'),
                  dataIndex: 'freezeRows',
                  width: 100,
                  render: (_, __, index) => (
                    <InputNumber
                      size="small"
                      min={0}
                      style={{ width: '100%' }}
                      value={sheetConfigs[index]?.freezeRows}
                      onChange={(value) => updateSheetConfig(index, { freezeRows: value ?? 0 })}
                    />
                  ),
                },
                {
                  title: i18n('excelReportTemplate.sheetConfig.freezeColumns'),
                  dataIndex: 'freezeColumns',
                  width: 110,
                  render: (_, __, index) => (
                    <InputNumber
                      size="small"
                      min={0}
                      style={{ width: '100%' }}
                      value={sheetConfigs[index]?.freezeColumns}
                      onChange={(value) => updateSheetConfig(index, { freezeColumns: value ?? 0 })}
                    />
                  ),
                },
                {
                  title: i18n('excelReportTemplate.sheetConfig.emptyResult'),
                  dataIndex: 'emptyResultBehavior',
                  width: 130,
                  render: (_, __, index) => (
                    <Select
                      size="small"
                      style={{ width: '100%' }}
                      value={sheetConfigs[index]?.emptyResultBehavior || 'EMPTY_SHEET'}
                      onChange={(value) => updateSheetConfig(index, { emptyResultBehavior: value })}
                      options={EMPTY_RESULT_OPTIONS}
                    />
                  ),
                },
                {
                  title: i18n('excelReportTemplate.sheetConfig.autoWidth'),
                  dataIndex: 'autoWidth',
                  width: 90,
                  render: (_, __, index) => (
                    <Switch
                      size="small"
                      checked={sheetConfigs[index]?.autoWidth === true}
                      onChange={(checked) => updateSheetConfig(index, { autoWidth: checked })}
                    />
                  ),
                },
              ]}
            />
            <Button type="primary" style={{ marginTop: 16 }} onClick={handleSaveSheetConfigs}>
{i18n('common.button.save')}
            </Button>
          </div>
        ),
      },
      {
        key: 'field-bindings',
        label: i18n('excelReportTemplate.binding.title'),
        children: (
          <div>
            <Space style={{ marginBottom: 8 }} wrap>
              {i18n('excelReportTemplate.sheetConfig.sheetName')}:
              <Select
                size="small"
                style={{ width: 200 }}
                value={activeSheet || undefined}
                placeholder={i18n('excelReportTemplate.placeholder.selectSheet')}
                onChange={(value) => {
                  setActiveSheet(value);
                  bindingForm.setFieldsValue(bindings[value] || []);
                }}
                options={sheetConfigs
                  .filter((c) => c.sheetName)
                  .map((c) => ({ label: c.sheetName!, value: c.sheetName! }))}
              />
              <Button type="dashed" size="small" icon={<Plus size={12} />} onClick={addBinding}>
                {i18n('excelReportTemplate.binding.title')}
              </Button>
            </Space>
            <Form form={bindingForm} component={false}>
              <Table<ExcelColumnBinding>
                dataSource={activeBindings}
                rowKey={(_, index) => String(index ?? 0)}
                pagination={false}
                size="small"
                bordered
                className={styles.bindingTable}
                columns={[
                  {
                    title: i18n('excelReportTemplate.binding.queryField'),
                    dataIndex: 'queryFieldId',
                    width: 170,
                    render: (_, __, index) => (
                      <Form.Item
                        name={[index, 'queryFieldId']}
                        rules={[{ required: true, message: 'Field required' }]}
                        style={{ marginBottom: 0 }}
                      >
                        <Select
                          size="small"
                          style={{ width: '100%' }}
                          placeholder={i18n('excelReportTemplate.placeholder.selectField')}
                          options={getViewFieldOptions(editingRecord?.queryViewId)}
                          onChange={(value) => updateBinding(index, { queryFieldId: value })}
                        />
                      </Form.Item>
                    ),
                  },
                  {
                    title: i18n('excelReportTemplate.binding.targetColumn'),
                    dataIndex: 'targetColumn',
                    width: 120,
                    render: (_, __, index) => (
                      <Form.Item
                        name={[index, 'targetColumn']}
                        rules={[
                          { required: true, message: 'Column required' },
                          { pattern: /^[A-Za-z]+$/, message: 'A-Z letters only' },
                        ]}
                        style={{ marginBottom: 0 }}
                      >
                        <Input
                          size="small"
                          placeholder="e.g. A"
                          style={{ textTransform: 'uppercase' }}
                          onChange={(e) => updateBinding(index, { targetColumn: e.target.value })}
                        />
                      </Form.Item>
                    ),
                  },
                  {
                    title: i18n('excelReportTemplate.binding.displayName'),
                    dataIndex: 'displayName',
                    width: 130,
                    render: (_, __, index) => (
                      <Form.Item name={[index, 'displayName']} style={{ marginBottom: 0 }}>
                        <Input
                          size="small"
                          placeholder="header text"
                          onChange={(e) => updateBinding(index, { displayName: e.target.value })}
                        />
                      </Form.Item>
                    ),
                  },
                  {
                    title: i18n('excelReportTemplate.binding.numberFormat'),
                    dataIndex: 'numberFormat',
                    width: 120,
                    render: (_, __, index) => (
                      <Form.Item name={[index, 'numberFormat']} style={{ marginBottom: 0 }}>
                        <Input
                          size="small"
                          placeholder="e.g. 0.00"
                          onChange={(e) => updateBinding(index, { numberFormat: e.target.value })}
                        />
                      </Form.Item>
                    ),
                  },
                  {
                    title: i18n('excelReportTemplate.binding.nullDisplay'),
                    dataIndex: 'nullDisplay',
                    width: 110,
                    render: (_, __, index) => (
                      <Form.Item name={[index, 'nullDisplay']} style={{ marginBottom: 0 }}>
                        <Input
                          size="small"
                          placeholder="e.g. -"
                          onChange={(e) => updateBinding(index, { nullDisplay: e.target.value })}
                        />
                      </Form.Item>
                    ),
                  },
                  {
                    title: i18n('excelReportTemplate.binding.alignment'),
                    dataIndex: 'alignment',
                    width: 110,
                    render: (_, __, index) => (
                      <Form.Item name={[index, 'alignment']} style={{ marginBottom: 0 }}>
                        <Select
                          size="small"
                          style={{ width: '100%' }}
                          allowClear
                          placeholder={i18n('excelReportTemplate.placeholder.auto')}
                          options={ALIGNMENT_OPTIONS}
                          onChange={(value) => updateBinding(index, { alignment: value })}
                        />
                      </Form.Item>
                    ),
                  },
                  {
                    title: 'Export',
                    dataIndex: 'exportEnabled',
                    width: 80,
                    render: (_, __, index) => (
                      <Form.Item
                        name={[index, 'exportEnabled']}
                        valuePropName="checked"
                        style={{ marginBottom: 0 }}
                      >
                        <Switch
                          size="small"
                          onChange={(checked) => updateBinding(index, { exportEnabled: checked })}
                        />
                      </Form.Item>
                    ),
                  },
                  {
                    title: 'Action',
                    width: 60,
                    render: (_, __, index) => (
                      <Button type="link" danger size="small" onClick={() => removeBinding(index)}>
                        {i18n('common.button.delete')}
                      </Button>
                    ),
                  },
                ]}
              />
            </Form>
            <Button type="primary" style={{ marginTop: 16 }} onClick={handleSaveBindings}>
{i18n('common.button.save')}
            </Button>
          </div>
        ),
      },
    ],
    [
      sheetConfigs,
      activeSheet,
      activeBindings,
      bindings,
      styles.bindingTable,
      getViewFieldOptions,
      editingRecord,
      bindingForm,
      updateSheetConfig,
      handleSaveSheetConfigs,
      addBinding,
      removeBinding,
      updateBinding,
      handleSaveBindings,
    ],
  );

  // ---- ProTable columns ----
  const columns: ProColumns<ExcelReportTemplate>[] = useMemo(
    () => [
      {
        title: i18n('common.label.name'),
        dataIndex: 'name',
        key: 'name',
        ellipsis: true,
        width: 180,
      },
      {
        title: i18n('common.text.status'),
        dataIndex: 'status',
        key: 'status',
        width: 100,
        render: (_, record) => {
          const status = STATUS_MAP[record.status || 'VALID'] || STATUS_MAP.VALID;
          return <Tag color={status.color}>{status.text}</Tag>;
        },
      },
      {
        title: i18n('excelReportTemplate.templateVersion'),
        dataIndex: 'templateVersion',
        key: 'templateVersion',
        width: 130,
      },
      {
        title: i18n('excelReportTemplate.queryView'),
        dataIndex: 'queryViewId',
        key: 'queryViewId',
        width: 150,
        ellipsis: true,
        render: (_, record) => getViewName(record.queryViewId),
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
        width: 330,
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
              icon={<ShieldCheck size={14} />}
              onClick={() => handleValidate(record.id!)}
            >
{i18n('excelReportTemplate.action.validate')}
            </Button>
            <Button
              type="link"
              size="small"
              icon={<FileDown size={14} />}
              onClick={() => handleOpenExport(record)}
            >
{i18n('excelReportTemplate.action.exportExcel')}
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
    [getViewName, handleOpenEdit, handleCopy, handleValidate, handleOpenExport, handleDelete],
  );

  return (
    <>
      <ProTable<ExcelReportTemplate>
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
          showTotal: (t) => i18n('queryPreview.total', t),
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
        scroll={{ x: 1000 }}
      />

      {/* Create Modal */}
      <Modal
        title={
          <ModalTitle
            iconCode="icon-table"
            title={i18n('excelReportTemplate.modal.uploadTitle')}
          />
        }
        maskClosable={false}
        open={createOpen}
        onOk={handleCreate}
        onCancel={() => setCreateOpen(false)}
        okText={i18n('common.button.confirm')}
        cancelText={i18n('common.button.cancel')}
        destroyOnClose
        width={520}
      >
        <Form form={createForm} layout="vertical" autoComplete="off">
          <Form.Item
            label={i18n('common.label.name')}
            name="name"
            rules={[{ required: true, message: i18n('common.form.error.required') }]}
          >
            <Input placeholder={i18n('excelReportTemplate.placeholder.templateName')} />
          </Form.Item>
          <Form.Item
            label={i18n('common.label.description')}
            name="description"
          >
            <Input.TextArea placeholder={i18n('excelReportTemplate.placeholder.description')} rows={2} />
          </Form.Item>
          <Form.Item
            label={i18n('excelReportTemplate.queryView')}
            name="queryViewId"
            rules={[{ required: true, message: i18n('common.form.error.required') }]}
          >
            <Select
              placeholder={i18n('excelReportTemplate.placeholder.publishedQueryView')}
              showSearch
              optionFilterProp="label"
              options={viewList
                .filter((v) => v.status === 'PUBLISHED')
                .map((v) => ({ label: v.name || String(v.id), value: v.id! }))}
            />
          </Form.Item>
          <Form.Item label={i18n('excelReportTemplate.placeholder.templateFile')} required>
            <Upload
              accept=".xlsx"
              beforeUpload={beforeUpload}
              fileList={fileList}
              onChange={handleUploadChange}
              maxCount={1}
            >
              <Button icon={<UploadCloud size={14} />}>{i18n('excelReportTemplate.action.exportExcel')}</Button>
            </Upload>
          </Form.Item>
        </Form>
      </Modal>

      {/* Edit Modal */}
      <Modal
        title={
          <ModalTitle
            iconCode="icon-table"
            title={i18n('excelReportTemplate.modal.editTitle')}
          />
        }
        maskClosable={false}
        open={editOpen}
        onCancel={() => setEditOpen(false)}
        okText={i18n('common.button.close')}
        cancelText={i18n('common.button.cancel')}
        footer={[
          <Button key="close" onClick={() => setEditOpen(false)}>
            {i18n('common.button.close')}
          </Button>,
        ]}
        destroyOnClose
        width={1000}
      >
        {editingRecord && (
          <div>
            <Typography.Title level={5} style={{ marginTop: 0 }}>
              {editingRecord.name}
            </Typography.Title>
            <Tabs items={editTabItems} />
          </div>
        )}
      </Modal>

      {/* Validate Modal */}
      <Modal
        title={
          <ModalTitle
            iconCode="icon-shield-check"
            title={i18n('excelReportTemplate.modal.validateTitle')}
          />
        }
        open={validateOpen}
        onCancel={() => setValidateOpen(false)}
        footer={[
          <Button key="close" onClick={() => setValidateOpen(false)}>
            {i18n('common.button.close')}
          </Button>,
        ]}
        destroyOnClose
        width={640}
      >
        {validating ? (
          <div style={{ textAlign: 'center', padding: 24 }}>{i18n('excelReportTemplate.message.validationFailed')}</div>
        ) : validationErrors.length === 0 ? (
          <Typography.Text type="success">{i18n('excelReportTemplate.message.validateSuccess')}</Typography.Text>
        ) : (
          <div className={styles.validationErrors}>
            <Table<ValidationFinding>
              dataSource={validationErrors.map((e, i) => ({ ...e, key: i })) as any}
              rowKey="key"
              pagination={false}
              size="small"
              columns={[
                {
                  title: i18n('excelReportTemplate.table.sheet'),
                  dataIndex: 'sheetName',
                  width: 140,
                  render: (_, record) => record.sheetName || '-',
                },
                {
                  title: i18n('excelReportTemplate.table.cellRange'),
                  dataIndex: 'cellRange',
                  width: 120,
                  render: (_, record) => record.cellRange || '-',
                },
                {
                  title: i18n('excelReportTemplate.table.message'),
                  dataIndex: 'message',
                  render: (_, record) => (
                    <Space size={4}>
                      {record.warning === true && <Tag color="orange">WARN</Tag>}
                      <span>{record.message || record.errorCode || '-'}</span>
                    </Space>
                  ),
                },
              ]}
            />
          </div>
        )}
      </Modal>

      {/* Export Modal */}
      {exportTemplate?.queryViewId != null && (
        <ExcelExportModal
          open={exportOpen}
          queryViewId={exportTemplate.queryViewId}
          templateId={exportTemplate.id}
          onClose={() => {
            setExportOpen(false);
            setExportTemplate(null);
          }}
        />
      )}
    </>
  );
});
