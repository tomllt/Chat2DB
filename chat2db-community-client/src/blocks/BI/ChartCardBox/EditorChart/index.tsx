import React, { memo, useCallback } from 'react';
import { useStyles } from './style';
import Chart from '@/blocks/BI/Chart';
import { IChartItem } from '@/typings/dashboard';
import ChartTypeAndDataForm from '@/blocks/BI/Chart/form/ChartTypeAndDataForm';
import DataSourceTypeSelect from '@/blocks/BI/Chart/form/components/DataSourceTypeSelect';
import SavedQueryViewSelect from '@/blocks/BI/Chart/form/components/SavedQueryViewSelect';
import i18n from '@/i18n';
import { isEqualMemo } from '@/utils';

interface IProps {
  className?: string;
  customCommitButton?: React.ReactNode;
  chartDetail: IChartItem;
  onChangeChartSchema?: (values: IChartItem['chartSchema']) => void;
  onChangeChartDetail?: (patch: Partial<IChartItem>) => void;
}

export default memo<IProps>(
  (props) => {
    const { className, customCommitButton, chartDetail, onChangeChartSchema, onChangeChartDetail } = props;
    const { styles, cx } = useStyles();

    const isSavedQueryView = chartDetail?.dataSourceType === 'SAVED_QUERY_VIEW';

    const handleDataSourceTypeChange = useCallback(
      (value: 'LEGACY_SQL' | 'SAVED_QUERY_VIEW') => {
        const patch: Partial<IChartItem> = { dataSourceType: value };
        // Clear stale source-specific fields when toggling
        if (value === 'SAVED_QUERY_VIEW') {
          // Clear SQL-related fields
          patch.databaseInfo = undefined;
          patch.metaData = undefined;
        } else {
          // Clear saved-query-view-related fields
          patch.savedQueryViewId = undefined;
          patch.queryDatasetId = undefined;
        }
        onChangeChartDetail?.(patch);
      },
      [onChangeChartDetail],
    );

    const handleSavedQueryViewChange = useCallback(
      (value: number | undefined) => {
        onChangeChartDetail?.({ savedQueryViewId: value });
      },
      [onChangeChartDetail],
    );

    return (
      <div className={cx(styles.editChartCard, className)}>
        <div className={styles.left}>
          <Chart chartDetail={chartDetail} />
        </div>
        <div className={styles.right}>
          <div className={styles.formContent}>
            <div style={{ marginBottom: 16 }}>
              <div style={{ marginBottom: 4, fontSize: 13, color: 'rgba(0,0,0,0.65)' }}>
                {i18n('dashboard.chart.dataSourceType')}
              </div>
              <DataSourceTypeSelect
                value={chartDetail?.dataSourceType || 'LEGACY_SQL'}
                onChange={handleDataSourceTypeChange}
              />
            </div>
            {isSavedQueryView && (
              <div style={{ marginBottom: 16 }}>
                <div style={{ marginBottom: 4, fontSize: 13, color: 'rgba(0,0,0,0.65)' }}>
                  {i18n('savedQueryView.title')}
                </div>
                <SavedQueryViewSelect
                  value={chartDetail?.savedQueryViewId}
                  onChange={handleSavedQueryViewChange}
                />
              </div>
            )}
            <ChartTypeAndDataForm chartDetail={chartDetail} onChangeChartSchema={onChangeChartSchema} />
          </div>
          <div className={styles.buttonBox}>{customCommitButton}</div>
        </div>
      </div>
    );
  },
  (prev, next) => {
    return isEqualMemo([prev.chartDetail, next.chartDetail], [prev.className, next.className]);
  },
);
