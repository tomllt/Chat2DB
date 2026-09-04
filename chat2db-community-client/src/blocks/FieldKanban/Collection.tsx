import { useMemo } from 'react';
import { Button, Space, Tag, Typography } from 'antd';
import { GripVertical, X } from 'lucide-react';
import { useDroppable } from '@dnd-kit/core';
import { SortableContext, useSortable, verticalListSortingStrategy } from '@dnd-kit/sortable';
import { CSS } from '@dnd-kit/utilities';

import i18n from '@/i18n';
import { kanbanItemId, type IKanbanFieldItem } from './kanbanLogic';
import { useStyles } from './style';

export interface ICollectionDropProps {
  readonly id: string;
  readonly label: string;
  readonly items: readonly IKanbanFieldItem[];
  readonly scope: 'available' | 'bound';
  readonly count: number;
  readonly activeId: string | null;
  readonly disabled: boolean;
  readonly onUnbind: (item: IKanbanFieldItem) => void;
  readonly onEdit?: (item: IKanbanFieldItem) => void;
}

interface IKanbanCardProps {
  readonly item: IKanbanFieldItem;
  readonly scope: 'available' | 'bound';
  readonly activeId: string | null;
  readonly disabled: boolean;
  readonly onUnbind: (item: IKanbanFieldItem) => void;
  readonly onEdit?: (item: IKanbanFieldItem) => void;
}

const KanbanCard = ({ item, scope, activeId, disabled, onUnbind, onEdit }: IKanbanCardProps) => {
  const { styles } = useStyles();
  const itemId = kanbanItemId(item.fieldId, scope);
  const { attributes, listeners, setNodeRef, setActivatorNodeRef, transform, transition, isDragging } = useSortable({
    id: itemId,
    disabled,
  });
  const style = {
    transform: CSS.Transform.toString(transform ? { ...transform, scaleX: 1, scaleY: 1 } : null),
    transition,
  };
  const ariaLabel = scope === 'bound'
    ? i18n('reportBundle.editor.fieldCard.bound', item.displayName, item.binding.targetColumn || '')
    : i18n('reportBundle.editor.fieldCard.available', item.displayName);
  return (
    <div
      ref={setNodeRef}
      style={style}
      className={`${styles.card}${isDragging || activeId === itemId ? ` ${styles.cardDragging}` : ''}`}
      role="listitem"
      aria-label={ariaLabel}
      data-dragging={activeId === itemId}
      data-kanban-item={itemId}
      {...attributes}
    >
    <div className={styles.cardRow}>
      <Button
        ref={setActivatorNodeRef}
        type="text"
        size="small"
        icon={<GripVertical size={14} />}
        aria-label={i18n('reportBundle.editor.dragHandle', item.displayName)}
        {...listeners}
      />
      <Typography.Text className={styles.cardTitle}>{item.displayName}</Typography.Text>
      <div className={styles.cardActions}>
        {onEdit && scope === 'bound' ? <Button type="link" size="small" onClick={() => onEdit(item)} aria-label={i18n('reportBundle.editor.editField', item.displayName)}>{i18n('reportBundle.editor.editFieldShort')}</Button> : null}
        {scope === 'bound' ? <Button type="text" size="small" danger icon={<X size={14} />} aria-label={i18n('reportBundle.editor.unbind', item.displayName)} onClick={() => onUnbind(item)} /> : null}
      </div>
    </div>
    <div className={styles.cardMeta}>
      {item.sourceColumn ? <Tag color="default">{item.sourceColumn}</Tag> : null}
      {item.dataType ? <Tag color="blue">{item.dataType}</Tag> : null}
      {scope === 'bound' && item.binding.targetColumn ? <Tag color="green">{item.binding.targetColumn}</Tag> : null}
    </div>
    </div>
  );
};

export const CollectionDrop = (
  { id, label, items, scope, count, activeId, disabled, onUnbind, onEdit }: ICollectionDropProps,
) => {
  const { styles } = useStyles();
  const { setNodeRef, isOver } = useDroppable({ id });
  const ids = useMemo(() => items.map((item) => kanbanItemId(item.fieldId, scope)), [items, scope]);
  return <section className={styles.column} aria-label={label}>
    <header className={styles.columnHeader}>
      <Space>
        <Typography.Text strong>{label}</Typography.Text>
        <Typography.Text className={styles.columnCount}>{count}</Typography.Text>
      </Space>
      {scope === 'bound' ? <Tag color="processing">{i18n('reportBundle.editor.boundHint')}</Tag> : null}
    </header>
    <SortableContext id={id} items={ids} strategy={verticalListSortingStrategy}>
      <div
        ref={setNodeRef}
        className={`${styles.list}${isOver ? ` ${styles.listOver}` : ''}`}
        data-kanban-list={scope}
        role="list"
        aria-roledescription={i18n('reportBundle.editor.sortableList')}
        aria-describedby={`${scope}-list-hint`}
      >
        {items.length === 0 ? <div className={styles.emptyHint}>{i18n('reportBundle.editor.empty', label)}</div> : null}
        {items.map((item) => (
          <KanbanCard
            key={kanbanItemId(item.fieldId, scope)}
            item={item}
            scope={scope}
            activeId={activeId}
            disabled={disabled}
            onUnbind={onUnbind}
            onEdit={onEdit}
          />
        ))}
      </div>
    </SortableContext>
    <Typography.Paragraph
      id={`${scope}-list-hint`}
      type="secondary"
      style={{ padding: '0 16px 12px', fontSize: 12, margin: 0 }}
    >
      {scope === 'bound' ? i18n('reportBundle.editor.hint.bound') : i18n('reportBundle.editor.hint.available')}
    </Typography.Paragraph>
  </section>;
};
