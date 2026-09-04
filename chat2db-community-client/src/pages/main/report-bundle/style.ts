import { createStyles } from 'antd-style';

export const useStyles = createStyles(({ css, token }) => ({
  container: css`
    min-height: 100%;
    min-width: 0;
    padding: 24px;
    background: ${token.colorBgLayout};
  `,
  header: css`
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 16px;
    margin-bottom: 24px;
  `,
  title: css`
    margin: 0 !important;
  `,
  tableCard: css`
    overflow-x: auto;
    background: ${token.colorBgContainer};
    border: 1px solid ${token.colorBorderSecondary};
    border-radius: ${token.borderRadiusLG}px;
  `,
  empty: css`
    padding: 64px 24px;
  `,
  actions: css`
    display: flex;
    gap: 8px;
    flex-wrap: wrap;
  `,
}));
