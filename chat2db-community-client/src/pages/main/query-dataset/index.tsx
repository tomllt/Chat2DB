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
  message,
  Checkbox,
} from 'antd';
import { Modal } from '@chat2db/ui';
import ProTable, { ActionType, ProColumns } from '@ant-design/pro-table';
import { Plus, Pencil, Copy, Eye, CheckCircle, XCircle, Trash2, Send } from 'lucide-react';
import { useStyles } from './style';
import { useQueryDatasetStore } from '@/store/queryDataset/store';
import { QueryDataset, QueryDatasetField, PreviewResult } from '@/typings/queryDataset';
import i18n from '@/i18n';
import ModalTitle from '@/components/Modal/ModalTitle';
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
  ENABLED: { color: 'green', text: 'Enabled' },
  DISABLED: { color: 'red', text: 'Disabled' },
  DRAFT: { color: 'orange', text: 'Draft' },
  PUBLISHED: { color: 'blue', text: 'Published' },
};

export default memo(() => {
  const { styles } = useStyles();
  const actionRef = useRef<ActionType>(null);
  const [form] = Form.useForm();
  const [fieldForm] = Form.useForm();

  const {
    list,
    current,
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
    setLoading,
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
    setLoading: state.setLoading,
  }));

  // Modal state
  const [modalOpen, setModalOpen] = useState(false);
  const [editingRecord, setEditingRecord] = useState<QueryDataset | null>(null);
  const [fields, setFields] = useState<QueryDatasetField[]>([]);

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
        message.success(i18n('common.tips.updateSuccess'));
      } else {
        await createQueryDataset(payload);
        message.success(i18n('common.tips.createSuccess'));
      }
      setModalOpen(false);
      loadList(1);
    } catch (e: any) {
      if (e?.errorFields) return; // form validation error
      message.error(e?.message || 'Operation failed');
    }
  }, [form, fields, editingRecord, updateQueryDataset, createQueryDataset, loadList]);

  // ---- Delete ----
  const handleDelete = useCallback(
    async (id: number) => {
      try {
        await deleteQueryDataset(id);
        message.success(i18n('common.text.successfullyDelete'));
      } catch (e: any) {
        message.error(e?.message || i18n('common.text.errorDelete'));
      }
    },
    [deleteQueryDataset],
  );

  // ---- Publish ----
  const handlePublish = useCallback(
    async (id: number) => {
      try {
        await publish(id);
        message.success('Published successfully');
        loadList();
      } catch (e: any) {
        message.error(e?.message || 'Publish failed');
      }
    },
    [publish, loadList],
  );

  // ---- Disable ----
  const handleDisable = useCallback(
    async (id: number) => {
      try {
        await disable(id);
        message.success('Disabled successfully');
        loadList();
      } catch (e: any) {
        message.error(e?.message || 'Disable failed');
      }
    },
    [disable, loadList],
  );

  // ---- Copy ----
  const handleCopy = useCallback(
    async (record: QueryDataset) => {
      try {
        const newId = await copy(record.id!);
        message.success(i18n('common.button.copySuccessfully'));
        loadList(1);
        return newId;
      } catch (e: any) {
        message.error(e?.message || 'Copy failed');
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
        message.error(e?.message || 'Preview failed');
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
        title: 'Version',
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
              Preview
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
          showTotal: (t) => `Total ${t} items`,
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
                ? 'Edit Query Dataset'
                : 'Create Query Dataset'
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
            <Input placeholder="Dataset name" />
          </Form.Item>
          <Form.Item
            label={i18n('common.label.description')}
            name="description"
          >
            <Input.TextArea placeholder="Description (optional)" rows={2} />
          </Form.Item>
          <Form.Item label="Data Source ID" name="datasourceId">
            <Input placeholder="Data source ID" />
          </Form.Item>
          <Form.Item label={i18n('common.database.title')} name="databaseName">
            <Input placeholder="Database name" />
          </Form.Item>
          <Form.Item label="Schema" name="schemaName">
            <Input placeholder="Schema name" />
          </Form.Item>
          <Form.Item
            label={i18n('common.text.tableName')}
            name="tableName"
            rules={[{ required: true, message: i18n('common.form.error.required') }]}
          >
            <Input placeholder="Table name" />
          </Form.Item>
          <Form.Item label="Source Object Type" name="sourceObjectType">
            <Select
              placeholder="Select type"
              allowClear
              options={[
                { label: 'TABLE', value: 'TABLE' },
                { label: 'VIEW', value: 'VIEW' },
              ]}
            />
          </Form.Item>

          {/* Field Editor */}
          <div style={{ marginTop: 16, marginBottom: 8 }}>
            <strong>Fields</strong>
            <Button
              type="dashed"
              size="small"
              icon={<Plus size={12} />}
              style={{ marginLeft: 8 }}
              onClick={addField}
            >
              Add Field
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
                title: 'Source Column',
                dataIndex: 'sourceColumn',
                width: 140,
                render: (_, __, index) => (
                  <Input
                    size="small"
                    value={fields[index]?.sourceColumn}
                    onChange={(e) => updateField(index, { sourceColumn: e.target.value })}
                    placeholder="column name"
                  />
                ),
              },
              {
                title: 'Display Name',
                dataIndex: 'displayName',
                width: 120,
                render: (_, __, index) => (
                  <Input
                    size="small"
                    value={fields[index]?.displayName}
                    onChange={(e) => updateField(index, { displayName: e.target.value })}
                    placeholder="display name"
                  />
                ),
              },
              {
                title: 'Data Type',
                dataIndex: 'dataType',
                width: 100,
                render: (_, __, index) => (
                  <Input
                    size="small"
                    value={fields[index]?.dataType}
                    onChange={(e) => updateField(index, { dataType: e.target.value })}
                    placeholder="type"
                  />
                ),
              },
              {
                title: 'Role',
                dataIndex: 'role',
                width: 120,
                render: (_, __, index) => (
                  <Select
                    size="small"
                    value={fields[index]?.role || 'DIMENSION'}
                    style={{ width: '100%' }}
                    onChange={(value) => {
                      const updates: Partial<QueryDatasetField> = { role: value };
                      if (value === 'DIMENSION') {
                        updates.aggregation = '';
                      }
                      updateField(index, updates);
                    }}
                    options={ROLE_OPTIONS}
                  />
                ),
              },
              {
                title: 'Aggregation',
                dataIndex: 'aggregation',
                width: 140,
                render: (_, __, index) => {
                  const isMeasure = fields[index]?.role === 'MEASURE';
                  return (
                    <Select
                      size="small"
                      value={fields[index]?.aggregation || ''}
                      style={{ width: '100%' }}
                      disabled={!isMeasure}
                      onChange={(value) => updateField(index, { aggregation: value })}
                      options={AGGREGATION_OPTIONS}
                    />
                  );
                },
              },
              {
                title: 'Visible',
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
                title: 'Filterable',
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
                title: 'Sortable',
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
            title="Query Preview"
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