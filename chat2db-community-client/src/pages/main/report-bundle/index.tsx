import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { Button, Empty, Form, Input, Modal, Popconfirm, Space, Table, Typography, message } from 'antd';
import { Files, Pencil, Plus, Trash2, Table2 } from 'lucide-react';
import { useNavigate } from 'umi';

import i18n from '@/i18n';
import { createReportBundle, deleteReportBundle, reportBundleList, updateReportBundle } from '@/service/reportBundle';
import type { IReportBundle } from '@/typings/reportBundle';
import { useOrgStore } from '@/store/organization';
import { beginLatestRequest, invalidateLatestRequest, isLatestRequest } from '@/utils/latestRequest';
import { useReportBundleStore } from '@/store/reportBundle/store';
import { useStyles } from './style';

interface IBundleListResponse {
  data?: IReportBundle[];
  pageNo?: number;
  pageSize?: number;
  total?: number;
}

export default function ReportBundlePage() {
  const { styles } = useStyles();
  const navigate = useNavigate();
  const workspaceId = useOrgStore((state) => state.curOrg?.id ?? 0);
  const { setCurrent, selectBundle, setLoading } = useReportBundleStore();
  const [items, setItems] = useState<IReportBundle[]>([]);
  const [total, setTotal] = useState(0);
  const [pageNo, setPageNo] = useState(1);
  const [pageSize, setPageSize] = useState(20);
  const [loading, setLocalLoading] = useState(false);
  const [searchKey, setSearchKey] = useState('');
  const [modalOpen, setModalOpen] = useState(false);
  const [editing, setEditing] = useState<IReportBundle | null>(null);
  const requestGenerationRef = useRef(0);
  const requestControllerRef = useRef<AbortController | null>(null);
  const [form] = Form.useForm<{ name: string; description?: string; queryViewId?: number }>();

  const load = useCallback(async (nextPageNo: number, nextPageSize: number) => {
    requestControllerRef.current?.abort();
    const controller = new AbortController();
    requestControllerRef.current = controller;
    const requestGeneration = beginLatestRequest(requestGenerationRef);
    setLocalLoading(true);
    setLoading(true);
    try {
      const result = (await reportBundleList({
        workspaceId,
        searchKey: searchKey.trim() || undefined,
        pageNo: nextPageNo,
        pageSize: nextPageSize,
      }, { signal: controller.signal })) as IBundleListResponse;
      if (!isLatestRequest(requestGenerationRef, requestGeneration)) return;
      setItems(result.data || []);
      setTotal(result.total || 0);
      setPageNo(result.pageNo ?? nextPageNo);
      setPageSize(result.pageSize ?? nextPageSize);
    } catch (error) {
      if (controller.signal.aborted || !isLatestRequest(requestGenerationRef, requestGeneration)) return;
      message.error(error instanceof Error ? error.message : i18n('reportBundle.message.operationFailed'));
    } finally {
      if (isLatestRequest(requestGenerationRef, requestGeneration)) {
        setLocalLoading(false);
        setLoading(false);
      }
    }
  }, [searchKey, setLoading, workspaceId]);

  useEffect(() => {
    load(1, pageSize);
    return () => {
      requestControllerRef.current?.abort();
      invalidateLatestRequest(requestGenerationRef);
    };
  }, [load, pageSize]);
  const openCreate = () => { setEditing(null); form.resetFields(); setModalOpen(true); };
  const openEdit = (record: IReportBundle) => { setEditing(record); form.setFieldsValue({ name: record.name || '', description: record.description || '', queryViewId: record.queryViewId }); setModalOpen(true); };
  const submit = async () => {
    const values = await form.validateFields(); const name = values.name.trim();
    if (!name) { form.setFields([{ name: 'name', errors: [i18n('reportBundle.validation.nameRequired')] }]); return; }
    try {
      const payload = { ...values, name, workspaceId };
      if (editing?.id) await updateReportBundle({ ...editing, ...payload, id: editing.id, workspaceId });
      else await createReportBundle(payload as IReportBundle);
      setModalOpen(false); message.success(i18n('reportBundle.message.saved')); await load(pageNo, pageSize);
    } catch (error) { message.error(error instanceof Error ? error.message : i18n('reportBundle.message.operationFailed')); }
  };
  const remove = async (record: IReportBundle) => {
    if (!record.id) return;
    try {       await deleteReportBundle({ id: record.id, workspaceId }); message.success(i18n('reportBundle.message.deleted')); await load(pageNo, pageSize); }
    catch (error) { message.error(error instanceof Error ? error.message : i18n('reportBundle.message.operationFailed')); }
  };
  const open = (record: IReportBundle, target: 'editor' | 'data-view') => {
    if (!record.id) return;
    selectBundle(record.id);
    setCurrent(record);
    navigate(`/report-bundle/${target}?bundleId=${record.id}`);
  };
  const columns = useMemo(() => [
    { title: i18n('reportBundle.table.name'), dataIndex: 'name', key: 'name', render: (name: string) => <Typography.Text strong>{name}</Typography.Text> },
    { title: i18n('reportBundle.table.description'), dataIndex: 'description', key: 'description', render: (value: string) => value || '-' },
    { title: i18n('reportBundle.table.queryView'), dataIndex: 'queryViewId', key: 'queryViewId', render: (value: number) => value ?? '-' },
    { title: i18n('reportBundle.table.updated'), dataIndex: 'gmtModified', key: 'gmtModified', render: (value: string) => value || '-' },
    { title: i18n('reportBundle.table.actions'), key: 'actions', render: (_: unknown, record: IReportBundle) => <div className={styles.actions}>
      <Button icon={<Pencil size={15} />} onClick={() => openEdit(record)}>{i18n('common.button.edit')}</Button>
      <Button icon={<Table2 size={15} />} onClick={() => open(record, 'data-view')}>{i18n('reportBundle.action.dataView')}</Button>
       <Button type="primary" icon={<Files size={15} />} onClick={() => open(record, 'editor')}>{i18n('reportBundle.action.configure')}</Button>
      <Popconfirm title={i18n('reportBundle.confirm.delete')} onConfirm={() => remove(record)} okText={i18n('common.button.confirm')} cancelText={i18n('common.button.cancel')}><Button danger icon={<Trash2 size={15} />}>{i18n('common.button.delete')}</Button></Popconfirm>
    </div> },
  ], [styles.actions]);

  return <main className={styles.container} aria-label={i18n('reportBundle.title')}>
    <header className={styles.header}><div><Typography.Title level={2} className={styles.title}>{i18n('reportBundle.title')}</Typography.Title><Typography.Text type="secondary">{i18n('reportBundle.subtitle')}</Typography.Text></div><Button type="primary" icon={<Plus size={16} />} onClick={openCreate}>{i18n('reportBundle.action.create')}</Button></header>
    <Space direction="vertical" size="middle" style={{ width: '100%' }}><Input.Search allowClear placeholder={i18n('reportBundle.placeholder.search')} onSearch={(value) => setSearchKey(value.trim())} onChange={(event) => { if (!event.target.value) setSearchKey(''); }} /><div className={styles.tableCard}>{items.length === 0 && !loading ? <Empty className={styles.empty} image={Empty.PRESENTED_IMAGE_SIMPLE} description={i18n('reportBundle.empty')}><Button type="primary" icon={<Plus size={16} />} onClick={openCreate}>{i18n('reportBundle.action.create')}</Button></Empty> : <Table rowKey="id" loading={loading} columns={columns} dataSource={items} pagination={{ current: pageNo, pageSize, total, hideOnSinglePage: true, showSizeChanger: true }} onChange={(pagination) => load(pagination.current || 1, pagination.pageSize || pageSize)} />}</div></Space>
    <Modal open={modalOpen} title={editing ? i18n('reportBundle.modal.editTitle') : i18n('reportBundle.modal.createTitle')} onCancel={() => setModalOpen(false)} onOk={submit} destroyOnClose><Form form={form} layout="vertical"><Form.Item name="name" label={i18n('reportBundle.field.name')} rules={[{ required: true, whitespace: true, message: i18n('reportBundle.validation.nameRequired') }]}><Input autoFocus placeholder={i18n('reportBundle.placeholder.name')} /></Form.Item><Form.Item name="description" label={i18n('reportBundle.field.description')}><Input.TextArea rows={3} placeholder={i18n('reportBundle.placeholder.description')} /></Form.Item><Form.Item name="queryViewId" label={i18n('reportBundle.field.queryViewId')}><Input type="number" placeholder={i18n('reportBundle.placeholder.queryViewId')} /></Form.Item></Form></Modal>
  </main>;
}
