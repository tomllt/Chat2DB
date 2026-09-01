import { memo, useCallback, useEffect, useState } from 'react';
import { Modal, Select, Typography } from 'antd';
import {
  excelTemplateList,
  exportExcel,
  downloadExcel,
  ExcelReportTemplate,
} from '@/service/excelReport';
import { ViewFilter } from '@/typings/savedQueryView';
import feedback from '@/utils/feedback';
import ModalTitle from '@/components/Modal/ModalTitle';
import i18n from '@/i18n';

interface ExcelExportModalProps {
  open: boolean;
  queryViewId: number;
  /** Pre-select a specific template (e.g. when on the template detail page) */
  templateId?: number;
  /** Current filter state to serialize as filterOverrides */
  currentFilters?: ViewFilter[];
  onClose: () => void;
}

export default memo<ExcelExportModalProps>(({
  open,
  queryViewId,
  templateId,
  currentFilters,
  onClose,
}) => {
  const [templates, setTemplates] = useState<ExcelReportTemplate[]>([]);
  const [selectedTemplateId, setSelectedTemplateId] = useState<number | null>(null);
  const [exporting, setExporting] = useState(false);

  // Reset state and load templates when modal opens
  useEffect(() => {
    if (open) {
      setSelectedTemplateId(templateId ?? null);
      loadTemplates();
    }
  }, [open, templateId]);

  const loadTemplates = useCallback(async () => {
    try {
      const res = (await excelTemplateList({
        pageNo: 1,
        pageSize: 100,
      })) as unknown as { data: ExcelReportTemplate[] };
      const valid = (res.data || []).filter((t) => t.status === 'VALID');
      setTemplates(valid);
    } catch (e: any) {
      feedback.error(e?.message || i18n('excelExport.message.loadFailed'));
    }
  }, []);

  const selectedTemplate = templates.find((t) => t.id === selectedTemplateId);

  const handleExport = useCallback(async () => {
    if (!selectedTemplateId) {
      feedback.warning(i18n('excelExport.message.pleaseSelectTemplate'));
      return;
    }
    setExporting(true);
    try {
      const filterOverrides =
        currentFilters && currentFilters.length > 0
          ? JSON.stringify(currentFilters)
          : undefined;
      const res = await exportExcel({
        queryViewId,
        templateId: selectedTemplateId,
        filterOverrides,
      });
      if (res?.downloadToken && res?.exportId != null) {
        window.open(downloadExcel(res.exportId, res.downloadToken), '_blank');
        feedback.success(i18n('excelExport.message.exportStarted'));
      } else {
        feedback.warning(i18n('excelExport.message.exportNoToken'));
      }
      onClose();
    } catch (e: any) {
      const code = e?.errorCode;
      if (code && code.startsWith('EX_')) {
        feedback.error(e?.errorMessage || i18n('excelExport.message.exportFailed'));
      } else {
        feedback.error(e?.message || i18n('excelExport.message.exportFailed'));
      }
    } finally {
      setExporting(false);
    }
  }, [selectedTemplateId, currentFilters, queryViewId, onClose]);

  return (
    <Modal
      title={<ModalTitle iconCode="icon-export" title={i18n('excelExport.title')} />}
      open={open}
      onOk={handleExport}
      onCancel={onClose}
      okText={i18n('excelExport.export')}
      okButtonProps={{ loading: exporting }}
      confirmLoading={exporting}
      destroyOnClose
      width={480}
    >
      <div style={{ marginBottom: 16 }}>
        <Typography.Text strong>{i18n('excelExport.selectTemplate')}</Typography.Text>
        <Select
          style={{ width: '100%', marginTop: 4 }}
          placeholder={i18n('excelExport.placeholder')}
          value={selectedTemplateId}
          onChange={setSelectedTemplateId}
          disabled={templateId != null}
          options={templates.map((t) => ({
            label: t.name || `Template #${t.id}`,
            value: t.id!,
          }))}
        />
      </div>
      {selectedTemplate && (
<Typography.Text type="secondary">
           {i18n('excelExport.linkedView')}: {selectedTemplate.queryViewId
             ? `#${selectedTemplate.queryViewId}`
             : '-'}
         </Typography.Text>
      )}
    </Modal>
  );
});