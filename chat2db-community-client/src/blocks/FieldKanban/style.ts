import { createStyles } from 'antd-style';

export const useStyles = createStyles(({ css, token }) => ({
  board: css`
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 16px;
    align-items: stretch;
    min-width: 0;
    @media (max-width: 768px) {
      grid-template-columns: 1fr;
    }
  `,
  column: css`
    min-width: 0;
    display: flex;
    flex-direction: column;
    background: ${token.colorBgContainer};
    border: 1px solid ${token.colorBorderSecondary};
    border-radius: ${token.borderRadiusLG}px;
    min-height: 320px;
  `,
  columnHeader: css`
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 12px 16px;
    border-bottom: 1px solid ${token.colorBorderSecondary};
    font-weight: 600;
  `,
  columnCount: css`
    color: ${token.colorTextSecondary};
    font-weight: 400;
    font-size: 12px;
  `,
  list: css`
    display: flex;
    flex-direction: column;
    gap: 8px;
    padding: 12px 16px 16px;
    flex: 1;
    overflow: auto;
    min-height: 200px;
  `,
  listOver: css`
    background: ${token.colorFillTertiary};
    outline: 2px dashed ${token.colorPrimary};
    outline-offset: -2px;
  `,
  emptyHint: css`
    color: ${token.colorTextTertiary};
    text-align: center;
    padding: 24px 0;
    font-size: 12px;
  `,
  card: css`
    background: ${token.colorBgElevated};
    border: 1px solid ${token.colorBorderSecondary};
    border-radius: ${token.borderRadius}px;
    padding: 10px 12px;
    display: flex;
    flex-direction: column;
    gap: 4px;
    cursor: grab;
    user-select: none;
    touch-action: none;
  `,
  cardDragging: css`
    opacity: 0.5;
  `,
  cardRow: css`
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 8px;
  `,
  cardTitle: css`
    font-weight: 600;
    color: ${token.colorText};
    word-break: break-word;
  `,
  cardMeta: css`
    font-size: 12px;
    color: ${token.colorTextSecondary};
    display: flex;
    flex-wrap: wrap;
    gap: 8px;
  `,
  cardActions: css`
    display: flex;
    align-items: center;
    gap: 4px;
  `,
  liveRegion: css`
    position: absolute;
    width: 1px;
    height: 1px;
    padding: 0;
    margin: -1px;
    overflow: hidden;
    clip: rect(0, 0, 0, 0);
    white-space: nowrap;
    border: 0;
  `,
}));
