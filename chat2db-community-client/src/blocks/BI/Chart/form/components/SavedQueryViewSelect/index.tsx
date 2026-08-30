import { memo, useEffect, useState, useCallback } from 'react';
import { Select, Spin } from 'antd';
import { savedQueryViewList } from '@/service/savedQueryView';
import type { SavedQueryView } from '@/typings/savedQueryView';
import i18n from '@/i18n';

interface IProps {
  value?: number;
  onChange?: (value: number | undefined) => void;
}

const SavedQueryViewSelect = (props: IProps) => {
  const { value, onChange } = props;
  const [views, setViews] = useState<SavedQueryView[]>([]);
  const [loading, setLoading] = useState(false);

  const loadViews = useCallback(async () => {
    setLoading(true);
    try {
      const res = await savedQueryViewList({ pageNo: 1, pageSize: 200 });
      // Filter to show only published views
      setViews((res.data || []).filter((v) => v.status === 'PUBLISHED'));
    } catch {
      setViews([]);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    loadViews();
  }, [loadViews]);

  return (
    <Select
      allowClear
      showSearch
      loading={loading}
      notFoundContent={loading ? <Spin size="small" /> : null}
      placeholder={i18n('dashboard.chart.savedQueryViewSelect.placeholder')}
      style={{ width: '100%' }}
      value={value}
      onChange={(val) => onChange?.(val ?? undefined)}
      filterOption={(input, option) =>
        (option?.label as string ?? '').toLowerCase().includes(input.toLowerCase())
      }
      options={views.map((v) => ({
        label: v.name || `View #${v.id}`,
        value: v.id!,
      }))}
    />
  );
};

export default memo(SavedQueryViewSelect);