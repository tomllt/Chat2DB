import React from 'react';
import { Table } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { PreviewResult } from '@/typings/queryDataset';
import i18n from '@/i18n';

interface QueryPreviewProps {
  preview: PreviewResult | null;
  loading?: boolean;
  onPageChange?: (page: number, pageSize: number) => void;
}

const QueryPreview: React.FC<QueryPreviewProps> = ({ preview, loading, onPageChange }) => {
  if (!preview) return null;

  // When there are no columns, the antd Table has nothing to map cells against
  // and would render an empty grid. Guard so a null/empty result is explicit
  // instead of a broken table.
  if (!preview.columns || preview.columns.length === 0) {
    return <div style={{ padding: 16, color: '#999' }}>{i18n('queryPreview.noData')}</div>;
  }

  // Verify row keys line up with the column list (used by Table via dataIndex).
  if (preview.rows && preview.rows.length > 0) {
    const firstRowKeys = Object.keys(preview.rows[0]).filter((k) => k !== '_key').join(', ');
    // eslint-disable-next-line no-console
    console.log('[QueryPreview] columns:', preview.columns.join(', '), '| first row keys:', firstRowKeys);
  }

  const columns: ColumnsType<Record<string, unknown>> = preview.columns.map((col) => ({
    title: col,
    dataIndex: col,
    key: col,
    ellipsis: true,
  }));

  return (
    <Table<Record<string, unknown>>
      columns={columns}
      dataSource={preview.rows.map((row, idx) => ({ ...row, _key: idx }))}
      rowKey="_key"
      pagination={{
        current: preview.pageNo,
        pageSize: preview.pageSize,
        total: preview.total,
        showSizeChanger: false,
        showTotal: (total) => i18n('queryPreview.total', total),
        onChange: onPageChange,
      }}
      loading={loading}
      scroll={{ x: 'max-content' }}
      size="small"
    />
  );
};

export default QueryPreview;