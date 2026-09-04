import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { DndContext, KeyboardSensor, PointerSensor, TouchSensor, useSensor, useSensors, type DragEndEvent, type DragStartEvent } from '@dnd-kit/core';
import { restrictToWindowEdges } from '@dnd-kit/modifiers';
import { sortableKeyboardCoordinates } from '@dnd-kit/sortable';

import i18n from '@/i18n';
import { describeKanbanMove, kanbanItemId, moveKanbanItem, type IKanbanFieldItem } from './kanbanLogic';
import { CollectionDrop } from './Collection';
import { useStyles } from './style';

const AVAILABLE_DROPPABLE_ID = '__available__';
const BOUND_DROPPABLE_ID = '__bound__';

export interface IFieldKanbanProps {
  readonly available: readonly IKanbanFieldItem[];
  readonly bound: readonly IKanbanFieldItem[];
  readonly onChange: (next: { available: readonly IKanbanFieldItem[]; bound: readonly IKanbanFieldItem[] }) => void;
  readonly onBind?: (item: IKanbanFieldItem, position: number) => void;
  readonly onUnbind?: (item: IKanbanFieldItem) => void;
  readonly onEdit?: (item: IKanbanFieldItem) => void;
  readonly disabled?: boolean;
}

export const FieldKanban = ({ available, bound, onChange, onBind, onUnbind, onEdit, disabled }: IFieldKanbanProps) => {
  const { styles } = useStyles();
  const [activeId, setActiveId] = useState<string | null>(null);
  const [announcement, setAnnouncement] = useState('');
  const lastMoveRef = useRef<{ fieldId: string; destination: 'available' | 'bound' } | null>(null);

  const sensors = useSensors(
    useSensor(PointerSensor, { activationConstraint: { distance: 6 } }),
    useSensor(TouchSensor, { activationConstraint: { delay: 250, tolerance: 8 } }),
    useSensor(KeyboardSensor, { coordinateGetter: sortableKeyboardCoordinates }),
  );

  useEffect(() => {
    if (activeId) return;
    const last = lastMoveRef.current;
    if (!last) return;
    const movedBound = bound.find((item) => item.fieldId === last.fieldId);
    const movedAvailable = available.find((item) => item.fieldId === last.fieldId);
    const destinationCollection = last.destination === 'bound' ? bound : available;
    const movedItem = last.destination === 'bound' ? movedBound : movedAvailable;
    if (!movedItem || movedItem.fieldId !== last.fieldId) return;
    const index = destinationCollection.findIndex((item) => item.fieldId === last.fieldId);
    if (index === -1) return;
    setAnnouncement(describeKanbanMove(movedItem, last.destination, index));
    lastMoveRef.current = null;
  }, [activeId, available, bound]);

  const handleDragStart = useCallback((event: DragStartEvent) => {
    if (disabled) return;
    setActiveId(String(event.active.id));
  }, [disabled]);

  const handleDragEnd = useCallback((event: DragEndEvent) => {
    const activeIdValue = String(event.active.id);
    const overId = event.over ? String(event.over.id) : null;
    setActiveId(null);
    if (disabled || !overId) {
      setAnnouncement(i18n('reportBundle.editor.announce.dropRejected'));
      return;
    }
    const moved = moveKanbanItem({ available, bound }, activeIdValue, overId);
    if (!moved) {
      const parsed = activeIdValue.startsWith('available:') ? 'available' : 'bound';
      if (parsed === 'bound' && overId === AVAILABLE_DROPPABLE_ID) {
        const item = bound.find((entry) => kanbanItemId(entry.fieldId, 'bound') === activeIdValue);
        if (item) {
          onChange({ available: [...available, item], bound: bound.filter((entry) => entry.fieldId !== item.fieldId) });
          lastMoveRef.current = { fieldId: item.fieldId, destination: 'available' };
          onUnbind?.(item);
          return;
        }
      }
      if (parsed === 'available' && overId === BOUND_DROPPABLE_ID) {
        const item = available.find((entry) => kanbanItemId(entry.fieldId, 'available') === activeIdValue);
        if (item && !bound.some((entry) => entry.fieldId === item.fieldId)) {
          onChange({ available: available.filter((entry) => entry.fieldId !== item.fieldId), bound: [...bound, item] });
          lastMoveRef.current = { fieldId: item.fieldId, destination: 'bound' };
          onBind?.(item, bound.length);
          return;
        }
      }
      setAnnouncement(i18n('reportBundle.editor.announce.dropRejected'));
      return;
    }
    onChange({ available: moved.nextAvailable, bound: moved.nextBound });
    const parsed = activeIdValue.startsWith('available:') ? 'available' : 'bound';
    const destination = parsed === 'available' || overId.startsWith('bound:') ? 'bound' : 'available';
    const fieldId = activeIdValue.split(':')[1] || '';
    lastMoveRef.current = { fieldId, destination };
    const targetIndex = (destination === 'bound' ? moved.nextBound : moved.nextAvailable)
      .findIndex((entry) => entry.fieldId === fieldId);
    if (destination === 'bound') {
      const item = moved.nextBound.find((entry) => entry.fieldId === fieldId);
      if (item) onBind?.(item, targetIndex === -1 ? moved.nextBound.length - 1 : targetIndex);
    } else {
      const item = moved.nextAvailable.find((entry) => entry.fieldId === fieldId);
      if (item) onUnbind?.(item);
    }
  }, [available, bound, disabled, onBind, onChange, onUnbind]);

  const handleUnbind = useCallback((item: IKanbanFieldItem) => {
    if (disabled) return;
    onChange({
      available: [...available, item],
      bound: bound.filter((entry) => entry.fieldId !== item.fieldId),
    });
    lastMoveRef.current = { fieldId: item.fieldId, destination: 'available' };
    setAnnouncement(describeKanbanMove(item, 'available', available.length));
    onUnbind?.(item);
  }, [available, bound, disabled, onChange, onUnbind]);

  const handleDragCancel = useCallback(() => {
    setActiveId(null);
    setAnnouncement(i18n('reportBundle.editor.announce.cancel'));
  }, []);

  const availableLabel = i18n('reportBundle.editor.availableTitle');
  const boundLabel = i18n('reportBundle.editor.boundTitle');
  const activeItem = useMemo(() => {
    if (!activeId) return null;
    const fieldId = activeId.split(':')[1];
    return bound.find((entry) => entry.fieldId === fieldId)
      || available.find((entry) => entry.fieldId === fieldId)
      || null;
  }, [activeId, available, bound]);

  return (
    <div className={styles.board}>
      <DndContext
        sensors={sensors}
        modifiers={[restrictToWindowEdges]}
        onDragStart={handleDragStart}
        onDragEnd={handleDragEnd}
        onDragCancel={handleDragCancel}
        accessibility={{
          announcements: {
            onDragStart({ active }) {
              const fieldId = String(active.id).split(':')[1] || '';
              const item = activeId === null
                ? (bound.find((e) => e.fieldId === fieldId) || available.find((e) => e.fieldId === fieldId))
                : null;
              return item
                ? describeKanbanMove(item, String(active.id).startsWith('bound:') ? 'bound' : 'available', 0)
                : '';
            },
            onDragOver({ active, over }) {
              if (!over) return '';
              const activeField = String(active.id).split(':')[1] || '';
              const overField = String(over.id).split(':')[1] || '';
              if (!activeField || !overField) return '';
              return i18n('reportBundle.editor.announce.over', activeField, overField);
            },
            onDragEnd({ active, over }) {
              const activeField = String(active.id).split(':')[1] || '';
              if (!over) return i18n('reportBundle.editor.announce.dragCancelled', activeField);
              const overField = String(over.id).split(':')[1] || '';
              return i18n('reportBundle.editor.announce.dropped', activeField, overField);
            },
            onDragCancel({ active }) {
              const fieldId = String(active.id).split(':')[1] || '';
              return i18n('reportBundle.editor.announce.dragCancelled', fieldId);
            },
          },
        }}
      >
        <CollectionDrop
          id={AVAILABLE_DROPPABLE_ID}
          label={availableLabel}
          items={available}
          scope="available"
          count={available.length}
          activeId={activeId}
          disabled={Boolean(disabled)}
          onUnbind={handleUnbind}
          onEdit={onEdit}
        />
        <CollectionDrop
          id={BOUND_DROPPABLE_ID}
          label={boundLabel}
          items={bound}
          scope="bound"
          count={bound.length}
          activeId={activeId}
          disabled={Boolean(disabled)}
          onUnbind={handleUnbind}
          onEdit={onEdit}
        />
      </DndContext>
      <div className={styles.liveRegion} role="status" aria-live="polite" aria-atomic="true">
        {announcement}
      </div>
      <input type="hidden" value="" aria-hidden readOnly />
      <span hidden>{activeItem ? activeItem.displayName : ''}</span>
    </div>
  );
};

export type { IKanbanFieldItem } from './kanbanLogic';
export { AVAILABLE_DROPPABLE_ID, BOUND_DROPPABLE_ID };
