import { memo, useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { useUpdateEffect } from 'ahooks';
import {
  Button,
  Form,
  Input,
  Select,
  Table,
  Tag,
  Popconfirm,
  Space,
  Checkbox,
} from 'antd';
import { Modal } from '@chat2db/ui';
import ProTable, { ActionType, ProColumns } from '@ant-design/pro-table';
import { Plus, Pencil, Copy, Eye, XCircle, Trash2, Send } from 'lucide-react';
import { useStyles } from './style';
import { useQueryDatasetStore } from '@/store/queryDataset/store';
import useSelectDatabase from '@/hooks/useSelectDatabase';
import sqlService, { type IColumn } from '@/service/sql';
import { TableDataType } from '@/constants/table';
import { QueryDataset, QueryDatasetField } from '@/typings/queryDataset';
import i18n from '@/i18n';
import ModalTitle from '@/components/Modal/ModalTitle';
import feedback from '@/utils/feedback';
import QueryPreview from '@/blocks/QueryPreview';

const ROLE_OPTIONS = [
  { label: 'DIMENSION', value: 'DIMENSION' },
  { label: 'MEASURE', value: 'MEASURE' },
];

const AGGREGATION_OPTIONS = [
  { label: 'NONE', value: '' },
  { label: 'SUM', value: 'SUM' },
  { label: 'AVG', value: 'AVG' },
  { label: 'COUNT', value: 'COUNT' },
  { label: 'MAX', value: 'MAX' },
  { label: 'MIN', value: 'MIN' },
  { label: 'COUNT_DISTINCT', value: 'COUNT_DISTINCT' },
];

const STATUS_MAP: Record<string, { color: string; text: string }> = {
  ENABLED: { color: 'green', text: i18n('queryDataset.status.enabled') },
  DISABLED: { color: 'red', text: i18n('queryDataset.status.disabled') },
  DRAFT: { color: 'orange', text: i18n('queryDataset.status.draft') },
  PUBLISHED: { color: 'blue', text: i18n('queryDataset.status.published') },
};

export default memo(() => {
  const { styles } = useStyles();
  const actionRef = useRef<ActionType>(null);
  const [form] = Form.useForm();
  const [fieldForm] = Form.useForm();
  const tableName = Form.useWatch('tableName', form);

  const {
    list,
    loading,
    preview,
    total,
    pageNo,
    pageSize,
    queryDatasetList,
    queryDatasetDetail,
    createQueryDataset,
    updateQueryDataset,
    deleteQueryDataset,
    publish,
    disable,
    copy,
     getPreview,
   } = useQueryDatasetStore((state) => ({
    list: state.list,
    current: state.current,
    loading: state.loading,
    preview: state.preview,
    total: state.total,
    pageNo: state.pageNo,
    pageSize: state.pageSize,
    queryDatasetList: state.queryDatasetList,
    queryDatasetDetail: state.queryDatasetDetail,
    createQueryDataset: state.createQueryDataset,
    updateQueryDataset: state.updateQueryDataset,
    deleteQueryDataset: state.deleteQueryDataset,
    publish: state.publish,
    disable: state.disable,
    copy: state.copy,
     getPreview: state.getPreview,
   }));

  const { dataSourceList, databaseList, schemaList, selectDatabase, onChangeSelectDatabase } = useSelectDatabase({});
  const [tableList, setTableList] = useState<{ value: string; label: string }[]>([]);
  const [columnList, setColumnList] = useState<IColumn[]>([]);

  useUpdateEffect(() => {
    form.setFieldsValue({
      datasourceId: selectDatabase?.dataSourceId,
      databaseName: selectDatabase?.databaseName,
      schemaName: selectDatabase?.schemaName,
    });
  }, [selectDatabase]);

  useEffect(() => {
    const dataSourceId = selectDatabase?.dataSourceId;
    const databaseName = selectDatabase?.supportDatabase === false
      ? undefined
      : selectDatabase?.databaseName;
    const schemaName = selectDatabase?.schemaName;

    setTableList([]);
    if (dataSourceId === undefined || (!databaseName && !schemaName)) {
      return;
    }

    let active = true;
    sqlService
      .getTableList({ dataSourceId, databaseName, schemaName, pageNo: 1, pageSize: 100 })
      .then((res) => {
        if (active) {
          setTableList((res?.data || []).map((item) => ({ value: item.name, label: item.name })));
        }
      });

    return () => {
      active = false;
    };
  }, [
    selectDatabase?.dataSourceId,
    selectDatabase?.databaseName,
    selectDatabase?.schemaName,
    selectDatabase?.supportDatabase,
  ]);

  useEffect(() => {
    const dataSourceId = selectDatabase?.dataSourceId;
    const databaseName = selectDatabase?.supportDatabase === false
      ? undefined
      : selectDatabase?.databaseName;
    const schemaName = selectDatabase?.schemaName;

    setColumnList([]);
    if (dataSourceId === undefined || (!databaseName && !schemaName) || !tableName) {
      return;
    }

    let active = true;
    sqlService
      .getColumnList({ dataSourceId, databaseName: databaseName ?? '', schemaName, tableName })
      .then((res) => {
        if (active) {
          setColumnList(res?.data || []);
        }
      });

    return () => {
      active = false;
    };
  }, [
    selectDatabase?.dataSourceId,
    selectDatabase?.databaseName,
    selectDatabase?.schemaName,
    selectDatabase?.supportDatabase,
    tableName,
  ]);

  // Modal state
  const [modalOpen, setModalOpen] = useState(false);
  const [editingRecord, setEditingRecord] = useState<QueryDataset | null>(null);
  const [fields, setFields] = useState<QueryDatasetField[]>([]);
  const skipInitialEditAutofillRef = useRef(false);

  useEffect(() => {
    if (columnList.length === 0) {
      if (!tableName || !selectDatabase?.dataSourceId) {
        setFields([]);
      }
      return;
    }

    if (skipInitialEditAutofillRef.current) {
      skipInitialEditAutofillRef.current = false;
      return;
    }

    setFields(
      columnList.map((column) => {
        const isMeasure = column.dataType === TableDataType.NUMERIC;
        return {
          fieldId: undefined,
          sourceColumn: column.name,
          displayName: column.name,
          dataType: column.dataType || column.columnType,
          role: isMeasure ? 'MEASURE' : 'DIMENSION',
          aggregation: isMeasure ? 'SUM' : '',
          filterable: true,
          sortable: true,
          visible: true,
        };
      }),
    );
  }, [columnList, selectDatabase?.dataSourceId, tableName]);

  // Preview modal state
  const [previewOpen, setPreviewOpen] = useState(false);
  const [previewingId, setPreviewingId] = useState<number | null>(null);

  useEffect(() => {
    queryDatasetList({ pageNo: 1, pageSize: 20 });
  }, []);

  // ---- Load list ----
  const loadList = useCallback(
    (p?: number, ps?: number) => {
      queryDatasetList({ pageNo: p ?? pageNo, pageSize: ps ?? pageSize });
    },
    [pageNo, pageSize, queryDatasetList],
  );

  // ---- Open create modal ----
  const handleOpenCreate = useCallback(() => {
    setEditingRecord(null);
    skipInitialEditAutofillRef.current = false;
    setFields([]);
    form.resetFields();
    fieldForm.resetFields();
    setModalOpen(true);
  }, [form, fieldForm]);

  // ---- Open edit modal ----
  const handleOpenEdit = useCallback(
    async (record: QueryDataset) => {
      try {
        await queryDatasetDetail(record.id!);
      } catch {
        // fallback to the row data
      }
      const detail = useQueryDatasetStore.getState().current || record;
      setEditingRecord(detail);
      form.setFieldsValue({
        name: detail.name,
        description: detail.description,
        datasourceId: detail.datasourceId,
        databaseName: detail.databaseName,
        schemaName: detail.schemaName,
        tableName: detail.tableName,
        sourceObjectType: detail.sourceObjectType,
      });
      setFields(detail.fields || []);
      skipInitialEditAutofillRef.current = (detail.fields || []).length > 0;
      setModalOpen(true);
    },
    [form, queryDatasetDetail],
  );

  // ---- Save (create or update) ----
  const handleSave = useCallback(async () => {
    try {
      const values = await form.validateFields();
      const payload = {
        ...values,
        fields,
      };

      if (editingRecord?.id) {
        await updateQueryDataset({ id: editingRecord.id, ...payload });
        feedback.success(i18n('common.tips.updateSuccess'));
      } else {
        await createQueryDataset(payload);
        feedback.success(i18n('common.tips.createSuccess'));
      }
      setModalOpen(false);
      loadList(1);
    } catch (e: any) {
      if (e?.errorFields) return; // form validation error
      feedback.error(e?.message || i18n('queryDataset.message.operationFailed'));
    }
  }, [form, fields, editingRecord, updateQueryDataset, createQueryDataset, loadList]);

  // ---- Delete ----
  const handleDelete = useCallback(
    async (id: number) => {
      try {
        await deleteQueryDataset(id);
        feedback.success(i18n('common.text.successfullyDelete'));
      } catch (e: any) {
        feedback.error(e?.message || i18n('common.text.errorDelete'));
      }
    },
    [deleteQueryDataset],
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
    async (record: QueryDataset) => {
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
        await getPreview(id, 1, 20);
      } catch (e: any) {
        feedback.error(e?.message || i18n('queryDataset.message.previewFailed'));
      }
    },
    [getPreview],
  );

  // ---- Field editor helpers ----
  const addField = useCallback(() => {
    setFields((prev) => [
      ...prev,
      {
        fieldId: undefined,
        sourceColumn: '',
        displayName: '',
        dataType: '',
        role: 'DIMENSION',
        aggregation: '',
        filterable: true,
        sortable: true,
        visible: true,
      },
    ]);
  }, []);

  const removeField = useCallback((index: number) => {
    setFields((prev) => prev.filter((_, i) => i !== index));
  }, []);

  const updateField = useCallback(
    (index: number, partial: Partial<QueryDatasetField>) => {
      setFields((prev) =>
        prev.map((f, i) => (i === index ? { ...f, ...partial } : f)),
      );
    },
    [],
  );

  const DATA_TYPE_OPTIONS = Object.values(TableDataType).map((value) => ({ label: value, value }));

  // ---- ProTable columns ----
  const columns: ProColumns<QueryDataset>[] = useMemo(
    () => [
      {
        title: i18n('common.label.name'),
        dataIndex: 'name',
        key: 'name',
        ellipsis: true,
        width: 180,
      },
      {
        title: i18n('common.text.tableName'),
        dataIndex: 'tableName',
        key: 'tableName',
        width: 140,
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
        title: i18n('queryDataset.version'),
        dataIndex: 'version',
        key: 'version',
        width: 80,
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
        width: 280,
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
              {i18n('savedQueryView.action.publish')}
            </Button>
            <Button
              type="link"
              size="small"
              icon={<XCircle size={14} />}
              onClick={() => handleDisable(record.id!)}
            >
              {i18n('savedQueryView.action.disable')}
            </Button>
            <Button
              type="link"
              size="small"
              icon={<Eye size={14} />}
              onClick={() => handlePreview(record.id!)}
            >
              {i18n('savedQueryView.action.preview')}
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
    [handleOpenEdit, handleCopy, handlePublish, handleDisable, handlePreview, handleDelete],
  );

  return (
    <>
      <ProTable<QueryDataset>
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
                ? i18n('queryDataset.modal.editTitle')
                : i18n('queryDataset.modal.createTitle')
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
          onValuesChange={(changedValues, allValues) => {
            if ('datasourceId' in changedValues) {
              form.setFieldValue('databaseName', undefined);
              form.setFieldValue('schemaName', undefined);
            } else if ('databaseName' in changedValues) {
              form.setFieldValue('schemaName', undefined);
            }
            onChangeSelectDatabase({
              dataSourceId: allValues.datasourceId,
              databaseName: allValues.databaseName,
              schemaName: allValues.schemaName,
});

          }}
          style={{ maxHeight: 500, overflowY: 'auto' }}
        >
          <Form.Item
            label={i18n('common.label.name')}
            name="name"
            rules={[{ required: true, message: i18n('common.form.error.required') }]}
          >
            <Input placeholder={i18n('queryDataset.placeholder.name')} />
          </Form.Item>
          <Form.Item
            label={i18n('common.label.description')}
            name="description"
          >
            <Input.TextArea placeholder={i18n('queryDataset.placeholder.description')} rows={2} />
          </Form.Item>
          <Form.Item label={i18n('common.dataSource.title')} name="datasourceId">
            <Select
              showSearch
              options={dataSourceList || []}
              placeholder={i18n('common.dataSource.title')}
            />
          </Form.Item>
          {selectDatabase?.supportDatabase !== false && (
            <Form.Item label={i18n('common.database.title')} name="databaseName">
              <Select
                showSearch
                options={databaseList || []}
                placeholder={i18n('common.database.title')}
              />
            </Form.Item>
          )}
          {selectDatabase?.supportSchema !== false && (
            <Form.Item label={i18n('common.schema.title')} name="schemaName">
              <Select
                showSearch
                options={schemaList || []}
                placeholder={i18n('common.schema.title')}
              />
            </Form.Item>
          )}
          <Form.Item
            label={i18n('common.text.tableName')}
            name="tableName"
            rules={[{ required: true, message: i18n('common.form.error.required') }]}
          >
            <Select
              showSearch
              options={tableList}
              disabled={!selectDatabase?.dataSourceId}
              placeholder={i18n('common.text.tableName')}
            />
          </Form.Item>
          <Form.Item label={i18n('queryDataset.sourceObjectType')} name="sourceObjectType">
            <Select
              placeholder={i18n('queryDataset.placeholder.selectType')}
              allowClear
              options={[
                { label: 'TABLE', value: 'TABLE' },
                { label: 'VIEW', value: 'VIEW' },
              ]}
            />
          </Form.Item>

          {/* Field Editor */}
          <div style={{ marginTop: 16, marginBottom: 8 }}>
            <strong>{i18n('queryDataset.fields.title')}</strong>
            <Button
              type="dashed"
              size="small"
              icon={<Plus size={12} />}
              style={{ marginLeft: 8 }}
              onClick={addField}
            >
              {i18n('queryDataset.fields.add')}
            </Button>
          </div>
          <Table
            dataSource={fields}
            rowKey={(_, index) => String(index ?? 0)}
            pagination={false}
            size="small"
            bordered
            className={styles.fieldEditor}
            columns={[
              {
                title: i18n('queryDataset.field.sourceColumn'),
                dataIndex: 'sourceColumn',
                width: 140,
                render: (_, __, index) => (
                  <Select
                    size="small"
                    popupMatchSelectWidth={false}
                    showSearch
                    value={fields[index]?.sourceColumn || undefined}
                    placeholder={i18n('queryDataset.placeholder.columnName')}
                    options={columnList.map((column) => ({ label: column.name, value: column.name }))}
                    onChange={(value) => {
                      const column = columnList.find((item) => item.name === value);
                      const patch: Partial<QueryDatasetField> = { sourceColumn: value };
                      if (column) {
                        patch.dataType = column.dataType || column.columnType;
                        if (!fields[index]?.displayName) {
                          patch.displayName = value;
                        }
                      }
                      updateField(index, patch);
                    }}
                  />
                ),
              },
              {
                title: i18n('queryDataset.field.displayName'),
                dataIndex: 'displayName',
                width: 120,
                render: (_, __, index) => (
                  <Input
                    size="small"
                    value={fields[index]?.displayName}
                    onChange={(e) => updateField(index, { displayName: e.target.value })}
                    placeholder={i18n('queryDataset.placeholder.displayName')}
                  />
                ),
              },
              {
                title: i18n('queryDataset.field.dataType'),
                dataIndex: 'dataType',
                width: 100,
                render: (_, __, index) => (
                  <Select
                    size="small"
                    popupMatchSelectWidth={false}
                    showSearch
                    value={fields[index]?.dataType || undefined}
                    placeholder={i18n('queryDataset.placeholder.dataType')}
                    options={DATA_TYPE_OPTIONS}
                    onChange={(value) => updateField(index, { dataType: value })}
                  />
                ),
              },
              {
                title: i18n('queryDataset.field.role'),
                dataIndex: 'role',
                width: 120,
                render: (_, __, index) => (
                  <Select
                    size="small"
                    popupMatchSelectWidth={false}
                    value={fields[index]?.role || 'DIMENSION'}
                    style={{ width: '100%' }}
                    onChange={(value) => {
                      const updates: Partial<QueryDatasetField> = { role: value };
                      if (value === 'DIMENSION') {
                        updates.aggregation = '';
                      }
                      const column = columnList.find((item) => item.name === fields[index]?.sourceColumn);
                      if (column) {
                        updates.dataType = column.dataType || column.columnType;
                      } else if (!fields[index]?.dataType && fields[index]?.aggregation === 'COUNT') {
                        updates.dataType = TableDataType.NUMERIC;
                      }
                      updateField(index, updates);
                    }}
                    options={ROLE_OPTIONS}
                  />
                ),
              },
              {
                title: i18n('queryDataset.field.aggregation'),
                dataIndex: 'aggregation',
                width: 140,
                render: (_, __, index) => {
                  const isMeasure = fields[index]?.role === 'MEASURE';
                  return (
                    <Select
                      size="small"
                      popupMatchSelectWidth={false}
                      value={fields[index]?.aggregation || ''}
                      style={{ width: '100%' }}
                      disabled={!isMeasure}
                      onChange={(value) => {
                        const updates: Partial<QueryDatasetField> = { aggregation: value };
                        const column = columnList.find((item) => item.name === fields[index]?.sourceColumn);
                        if (column) {
                          updates.dataType = column.dataType || column.columnType;
                        } else if (!fields[index]?.dataType && (value === 'COUNT' || value === 'COUNT_DISTINCT')) {
                          updates.dataType = TableDataType.NUMERIC;
                        }
                        updateField(index, updates);
                      }}
                      options={AGGREGATION_OPTIONS}
                    />
                  );
                },
              },
              {
                title: i18n('queryDataset.field.visible'),
                dataIndex: 'visible',
                width: 70,
                render: (_, __, index) => (
                  <Checkbox
                    checked={fields[index]?.visible !== false}
                    onChange={(e) => updateField(index, { visible: e.target.checked })}
                  />
                ),
              },
              {
                title: i18n('queryDataset.field.filterable'),
                dataIndex: 'filterable',
                width: 80,
                render: (_, __, index) => (
                  <Checkbox
                    checked={fields[index]?.filterable !== false}
                    onChange={(e) => updateField(index, { filterable: e.target.checked })}
                  />
                ),
              },
              {
                title: i18n('queryDataset.field.sortable'),
                dataIndex: 'sortable',
                width: 80,
                render: (_, __, index) => (
                  <Checkbox
                    checked={fields[index]?.sortable !== false}
                    onChange={(e) => updateField(index, { sortable: e.target.checked })}
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
                    onClick={() => removeField(index)}
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
              getPreview(previewingId, p, ps);
            }
          }}
        />
      </Modal>
    </>
  );
});
