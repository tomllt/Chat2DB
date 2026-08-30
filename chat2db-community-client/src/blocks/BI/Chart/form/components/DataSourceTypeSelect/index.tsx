import { memo } from 'react';
import { Radio } from 'antd';
import i18n from '@/i18n';

export type ChartDataSourceType = 'LEGACY_SQL' | 'SAVED_QUERY_VIEW';

interface IProps {
  value?: ChartDataSourceType;
  onChange?: (value: ChartDataSourceType) => void;
}

const DataSourceTypeSelect = (props: IProps) => {
  const { value, onChange } = props;
  return (
    <Radio.Group
      value={value || 'LEGACY_SQL'}
      onChange={(e) => {
        onChange?.(e.target.value);
      }}
    >
      <Radio value="LEGACY_SQL">{i18n('dashboard.chart.dataSourceType.legacySql')}</Radio>
      <Radio value="SAVED_QUERY_VIEW">{i18n('dashboard.chart.dataSourceType.savedQueryView')}</Radio>
    </Radio.Group>
  );
};

export default memo(DataSourceTypeSelect);