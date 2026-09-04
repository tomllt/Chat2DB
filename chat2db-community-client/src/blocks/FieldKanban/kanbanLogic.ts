import i18n from '@/i18n';
import type { ExcelColumnBinding } from '@/service/excelReport';
import type { QueryDatasetField } from '@/typings/queryDataset';

/**
 * Synthetic kanban item that pairs a backend QueryDatasetField with an
 * optional binding payload. The same shape is reused for both the available
 * (un-bound) and bound collections so dnd-kit can route items by a single id.
 *
 * `fieldId` is the canonical identity key; everything else is decoration for
 * the editor UI.
 */
export interface IKanbanFieldItem {
  readonly fieldId: string;
  readonly displayName: string;
  readonly sourceColumn?: string;
  readonly dataType?: string;
  readonly binding: ExcelColumnBinding;
  readonly queryField: QueryDatasetField | null;
}

/**
 * Result returned when a drag finishes. The component layer applies this via
 * setState; the helper is pure so the same transition can be replayed in
 * tests and driven from keyboard listeners.
 */
export interface IKanbanMoveResult {
  readonly nextAvailable: readonly IKanbanFieldItem[];
  readonly nextBound: readonly IKanbanFieldItem[];
}

/**
 * Stable identity used by both the available-list view and the bound-list
 * view. dnd-kit moves the same id across collections, so we must guarantee
 * it is unique per field and that two separate identities (one for
 * available, one for bound) never collide for the same backend field.
 */
export const kanbanItemId = (fieldId: string, scope: 'available' | 'bound'): string =>
  `${scope}:${fieldId}`;

export const parseKanbanItemId = (id: string): { scope: 'available' | 'bound'; fieldId: string } | null => {
  const separator = id.indexOf(':');
  if (separator <= 0) return null;
  const scope = id.slice(0, separator);
  const fieldId = id.slice(separator + 1);
  if ((scope !== 'available' && scope !== 'bound') || !fieldId) return null;
  return { scope, fieldId };
};

const bindingFromField = (field: QueryDatasetField): ExcelColumnBinding => {
  const binding: ExcelColumnBinding = {
    queryFieldId: field.fieldId,
    targetColumn: field.sourceColumn,
    displayName: field.displayName,
    exportEnabled: true,
  };
  if (field.numberFormat) binding.numberFormat = field.numberFormat;
  if (field.nullDisplay) binding.nullDisplay = field.nullDisplay;
  return binding;
};

/**
 * Build the available/bound collections from the source query-view fields
 * and the current draft bindings. The same fieldId MUST appear in only one
 * collection: any field already referenced by a binding becomes part of the
 * bound collection (with its existing binding preserved); the rest are
 * available.
 */
export const partitionFields = (
  fields: readonly QueryDatasetField[],
  bindings: readonly ExcelColumnBinding[],
): { available: readonly IKanbanFieldItem[]; bound: readonly IKanbanFieldItem[] } => {
  const boundByField = new Map<string, ExcelColumnBinding>();
  bindings.forEach((binding) => {
    if (binding.queryFieldId) boundByField.set(binding.queryFieldId, binding);
  });
  const available: IKanbanFieldItem[] = [];
  const bound: IKanbanFieldItem[] = [];
  fields.forEach((field) => {
    if (!field.fieldId) return;
    const existing = boundByField.get(field.fieldId);
    const item: IKanbanFieldItem = {
      fieldId: field.fieldId,
      displayName: field.displayName || field.sourceColumn || field.fieldId,
      sourceColumn: field.sourceColumn,
      dataType: field.dataType,
      binding: existing || bindingFromField(field),
      queryField: field,
    };
    if (existing) bound.push(item);
    else available.push(item);
  });
  return { available, bound };
};

/**
 * Find the kanban item that owns an id without scanning both arrays
 * repeatedly. Returns null when the id is malformed.
 */
const findItem = (
  collections: { available: readonly IKanbanFieldItem[]; bound: readonly IKanbanFieldItem[] },
  id: string,
): { collection: 'available' | 'bound'; item: IKanbanFieldItem } | null => {
  const parsed = parseKanbanItemId(id);
  if (!parsed) return null;
  const pool = parsed.scope === 'available' ? collections.available : collections.bound;
  const item = pool.find((entry) => entry.fieldId === parsed.fieldId);
  if (!item) return null;
  return { collection: parsed.scope, item };
};

/**
 * Move an item identified by `activeId` into the bound collection, either
 * before `targetId` (over the existing target row) or at the end of the
 * bound list (over a target that lives in the available list). When the
 * active item is already in the bound collection this is treated as a
 * reorder rather than a bind, so the same helper handles both transitions.
 *
 * Duplicate fieldIds are rejected explicitly: an item bound once must not
 * appear again in the bound collection, which means dropping a bound item
 * onto itself is a no-op rather than a duplicate insert.
 */
export const moveKanbanItem = (
  collections: { available: readonly IKanbanFieldItem[]; bound: readonly IKanbanFieldItem[] },
  activeId: string,
  overId: string | null,
): IKanbanMoveResult | null => {
  const active = findItem(collections, activeId);
  if (!active) return null;
  const target = overId ? findItem(collections, overId) : null;
  const targetInBound = target?.collection === 'bound';
  const targetInAvailable = target?.collection === 'available';
  const droppingIntoBound = targetInBound || (!target && active.collection === 'available');
  const droppingIntoAvailable = targetInAvailable || (!target && active.collection === 'bound');

  if (active.collection === 'bound' && target?.collection === 'bound' && target.item.fieldId === active.item.fieldId) {
    return { nextAvailable: collections.available, nextBound: collections.bound };
  }

  if (droppingIntoBound && active.collection === 'available'
    && collections.bound.some((entry) => entry.fieldId === active.item.fieldId)) {
    return null;
  }

  const removeFromAvailable = active.collection === 'available'
    ? collections.available.filter((entry) => entry.fieldId !== active.item.fieldId)
    : collections.available;
  const removeFromBound = active.collection === 'bound'
    ? collections.bound.filter((entry) => entry.fieldId !== active.item.fieldId)
    : collections.bound;

  if (droppingIntoBound) {
    const insertIndex = targetInBound && target
      ? removeFromBound.findIndex((entry) => entry.fieldId === target.item.fieldId)
      : removeFromBound.length;
    const safeIndex = insertIndex === -1 ? removeFromBound.length : insertIndex;
    const next = [...removeFromBound];
    next.splice(safeIndex, 0, active.item);
    return { nextAvailable: removeFromAvailable, nextBound: next };
  }

  if (droppingIntoAvailable) {
    const insertIndex = targetInAvailable && target
      ? removeFromAvailable.findIndex((entry) => entry.fieldId === target.item.fieldId)
      : removeFromAvailable.length;
    const safeIndex = insertIndex === -1 ? removeFromAvailable.length : insertIndex;
    const next = [...removeFromAvailable];
    next.splice(safeIndex, 0, active.item);
    return { nextAvailable: next, nextBound: removeFromBound };
  }

  return null;
};

/**
 * Convert the bound kanban items back into the immutable `ExcelColumnBinding[]`
 * shape used by the existing service contract. Bindings preserve the user's
 * edits (displayName, numberFormat, nullDisplay, alignment, exportEnabled)
 * via `binding`; targetColumn falls back to sourceColumn and finally to a
 * synthesized letter label so empty bindings never leak through.
 */
const canonicalizeBinding = (binding: ExcelColumnBinding): Record<string, unknown> => Object.keys(binding)
  .sort()
  .reduce<Record<string, unknown>>((canonical, key) => {
    canonical[key] = binding[key as keyof ExcelColumnBinding];
    return canonical;
  }, {});

export const bindingsAreEqual = (
  left: readonly ExcelColumnBinding[],
  right: readonly ExcelColumnBinding[],
): boolean => left.length === right.length
  && left.every((binding, index) => JSON.stringify(canonicalizeBinding(binding))
    === JSON.stringify(canonicalizeBinding(right[index])));

export const itemsToBindings = (items: readonly IKanbanFieldItem[]): ExcelColumnBinding[] => {
  return items.map((item, index) => {
    const binding: ExcelColumnBinding = {
      queryFieldId: item.fieldId,
      displayName: item.binding.displayName || item.displayName,
      exportEnabled: item.binding.exportEnabled !== false,
    };
    if (item.binding.targetColumn) binding.targetColumn = item.binding.targetColumn;
    else if (item.sourceColumn) binding.targetColumn = item.sourceColumn;
    else binding.targetColumn = columnLetter(index);
    if (item.binding.numberFormat) binding.numberFormat = item.binding.numberFormat;
    if (item.binding.nullDisplay) binding.nullDisplay = item.binding.nullDisplay;
    if (item.binding.alignment) binding.alignment = item.binding.alignment;
    return binding;
  });
};

/**
 * Convert spreadsheet column index 0..N to a stable A-Z letter sequence
 * (`A`, `B`, ..., `Z`, `AA`, `AB`, ...). Used only when a binding row has
 * no sourceColumn to fall back on; the persisted value is the same one
 * users see in the editor.
 */
export const columnLetter = (index: number): string => {
  let current = index;
  let result = '';
  while (current >= 0) {
    result = String.fromCharCode(65 + (current % 26)) + result;
    current = Math.floor(current / 26) - 1;
  }
  return result;
};

/**
 * Update a single bound item's binding payload in-place. Returns a new
 * collection reference to keep React render paths stable. The identity check
 * is intentionally a simple fieldId match because the bound collection must
 * never contain duplicate fieldIds.
 */
export const updateBoundItemBinding = (
  items: readonly IKanbanFieldItem[],
  fieldId: string,
  partial: Partial<ExcelColumnBinding>,
): IKanbanFieldItem[] => items.map((item) => (
  item.fieldId === fieldId
    ? { ...item, binding: { ...item.binding, ...partial } }
    : item
));

/**
 * Drop a bound item back into the available collection. Equivalent to a
 * drag from bound to available; keyboard "Unbind" actions reuse the same
 * helper so the keyboard and pointer paths share one removal logic.
 */
export const unbindItem = (
  collections: { available: readonly IKanbanFieldItem[]; bound: readonly IKanbanFieldItem[] },
  fieldId: string,
): { available: readonly IKanbanFieldItem[]; bound: readonly IKanbanFieldItem[] } => {
  const item = collections.bound.find((entry) => entry.fieldId === fieldId);
  if (!item) return collections;
  return {
    available: [...collections.available, item],
    bound: collections.bound.filter((entry) => entry.fieldId !== fieldId),
  };
};

/**
 * Build the human-readable aria-live announcement for a drag operation. The
 * editor passes the same strings into the screen-reader live region so
 * keyboard and pointer users hear the same wording.
 */
export const describeKanbanMove = (
  item: IKanbanFieldItem,
  destination: 'available' | 'bound',
  index: number,
): string => i18n(
  'reportBundle.editor.announce.moved',
  item.displayName,
  destination === 'bound' ? i18n('reportBundle.editor.boundTitle') : i18n('reportBundle.editor.availableTitle'),
  index + 1,
);
