import { createStyles } from 'antd-style';

export const useStyles = createStyles(({ css }) => {
  return {
    container: css`
      display: flex;
      height: 100%;
      width: 100%;
    `,
    containerRight: css`
      height: 100%;
      width: 100%;
    `,
    fieldEditor: css`
      .ant-table-cell {
        padding: 4px 8px;
      }
    `,
    actionButtons: css`
      display: flex;
      gap: 8px;
      flex-wrap: wrap;
    `,
    bindingTable: css`
      .ant-table-cell {
        padding: 4px 8px;
      }
    `,
    validationErrors: css`
      max-height: 400px;
      overflow-y: auto;
    `,
  };
});
