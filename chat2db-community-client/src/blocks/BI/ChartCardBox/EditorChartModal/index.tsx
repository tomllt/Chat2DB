import { useState, useImperativeHandle, ForwardedRef, forwardRef, useMemo, useRef, useCallback } from 'react';
import { useStyles } from './style';
import EditorChart from '../EditorChart';
import EditorChartSql, { EditorChartSqlRef } from '../EditorChartSql';
import { IChartItem } from '@/typings';
import i18n from '@/i18n';
import { EditText, Modal } from '@chat2db/ui';
import { Button } from 'antd';

export interface EditChartModalRef {
  controlEditChartModal: (status: 'editChart' | 'editChartSql' | false, chartDetail?: IChartItem) => void;
}

export interface IProps {
  submitEditorChartCallback?: (data: IChartItem) => void;
  editableChart?: boolean;
}

const initChartDetail: IChartItem = {
  chartSchema: {
    title: i18n('dashboard.chart.defaultName'),
  },
};

export default forwardRef((props: IProps, ref: ForwardedRef<EditChartModalRef>) => {
  const { submitEditorChartCallback, editableChart = true } = props;
  const { styles, cx } = useStyles();
  const [segmentedValue, setSegmentedValue] = useState<'editChart' | 'editChartSql' | false>(false);
  // Chart
  const [chartDetail, setChartDetail] = useState<IChartItem>(initChartDetail);
  const editorChartSqlRef = useRef<EditorChartSqlRef>(null);

  useImperativeHandle(ref, () => ({
    controlEditChartModal: (_segmentedValue, _chartDetail) => {
      if (_chartDetail) {
        setChartDetail(_chartDetail);
      }
      setSegmentedValue(_segmentedValue);
    },
  }));

  const isSavedQueryView = chartDetail?.dataSourceType === 'SAVED_QUERY_VIEW';

  const handleChangeChartDetail = useCallback((patch: Partial<IChartItem>) => {
    setChartDetail((prev) => ({
      ...prev,
      ...patch,
    }));
  }, []);

  const updateChartTitle = (value: string) => {
    setChartDetail({
      ...chartDetail,
      chartSchema: {
        ...chartDetail?.chartSchema,
        title: value,
      },
    });
  };

  const customCommitButton = useMemo(() => {
    return (
      <Button
        type="primary"
        onClick={() => {
          setSegmentedValue(false);
          submitEditorChartCallback?.(chartDetail!);
        }}
      >
        {i18n('dashboard.editor.save')}
      </Button>
    );
  }, [chartDetail]);

  const handleChangeChartSchema = (chartSchema: IChartItem['chartSchema']) => {
    setChartDetail({
      ...chartDetail,
      chartSchema,
    });
  };

  const handleBackToChart = () => {
    setSegmentedValue('editChart');
  };

  const handleGoToSql = () => {
    // Obtain user-configured data when switching to SQL
    const databaseInfoAndMetaData = editorChartSqlRef.current?.getDatabaseInfoAndMetaData();
    setChartDetail({
      ...chartDetail,
      ...databaseInfoAndMetaData,
    });
    setSegmentedValue('editChartSql');
  };

  return (
    <>
      <Modal
        className={styles.editDashboardModal}
        open={!!segmentedValue}
        afterClose={() => {
          setChartDetail?.(initChartDetail);
        }}
        maskClosable={false}
        title={
          <div className={styles.editDashboardModalTitle}>
            <EditText className={styles.editText} hoverShowBorder onBlur={updateChartTitle}>
              {chartDetail?.chartSchema?.title || chartDetail?.chartSchema?.summary || ''}
            </EditText>
            {editableChart && !isSavedQueryView && (
              <div className={styles.dataConfiguration}>
                <div
                  className={styles.back}
                  onClick={() => {
                    if (segmentedValue === 'editChart') {
                      handleGoToSql();
                    } else {
                      handleBackToChart();
                    }
                  }}
                >
                  <span>
                    {segmentedValue === 'editChart'
                      ? i18n('dashboard.chart.dataConfiguration')
                      : i18n('dashboard.chart.backToChart')}
                  </span>
                </div>
              </div>
            )}
          </div>
        }
        footer={null}
        width="84vw"
        destroyOnClose
        onCancel={() => {
          setSegmentedValue(false);
        }}
        centered
        styles={{
          body: {
            paddingBlock: 0,
          },
        }}
      >
        <div className={styles.editDashboardWrapper}>
          <div className={styles.editDashboardBody}>
            <EditorChart
              chartDetail={chartDetail}
              onChangeChartSchema={handleChangeChartSchema}
              onChangeChartDetail={handleChangeChartDetail}
              className={cx({ [styles.editorChart]: segmentedValue !== 'editChart' })}
              customCommitButton={customCommitButton}
            />
            {editableChart && !isSavedQueryView && (
              <EditorChartSql
                chartDetail={chartDetail}
                ref={editorChartSqlRef}
                className={cx({ [styles.editorChartSql]: segmentedValue !== 'editChartSql' })}
              />
            )}
          </div>
        </div>
      </Modal>
    </>
  );
});
