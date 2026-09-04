import assert from 'node:assert/strict';
import { test } from 'node:test';

import type { QueryDatasetField } from '@/typings/queryDataset';

import {
  bindingsAreEqual,
  columnLetter,
  describeKanbanMove,
  itemsToBindings,
  kanbanItemId,
  moveKanbanItem,
  partitionFields,
  parseKanbanItemId,
  unbindItem,
  updateBoundItemBinding,
} from './kanbanLogic';

const fields: QueryDatasetField[] = [
  { fieldId: 'region_id', sourceColumn: 'r_id', displayName: 'Region' },
  { fieldId: 'amount', sourceColumn: 'total', displayName: 'Net Amount', dataType: 'NUMBER' },
  { fieldId: 'created_at', sourceColumn: 'created_at', displayName: 'Created at' },
];

test('kanbanItemId and parseKanbanItemId round-trip', () => {
  const id = kanbanItemId('amount', 'bound');
  assert.equal(id, 'bound:amount');
  assert.deepEqual(parseKanbanItemId(id), { scope: 'bound', fieldId: 'amount' });
});

test('parseKanbanItemId rejects malformed inputs', () => {
  assert.equal(parseKanbanItemId('not-scope:foo'), null);
  assert.equal(parseKanbanItemId(':foo'), null);
  assert.equal(parseKanbanItemId('available:'), null);
  assert.equal(parseKanbanItemId(''), null);
});

test('partitionFields moves already-bound fields into the bound list', () => {
  const bindings = [{ queryFieldId: 'amount', targetColumn: 'B', displayName: 'Net Amount' }];
  const { available, bound } = partitionFields(fields, bindings);
  assert.equal(available.length, 2);
  assert.equal(available.map((item) => item.fieldId).join(','), 'region_id,created_at');
  assert.equal(bound.length, 1);
  assert.equal(bound[0].fieldId, 'amount');
  assert.equal(bound[0].binding.targetColumn, 'B');
  assert.equal(bound[0].binding.queryFieldId, 'amount');
});

test('partitionFields synthesizes a binding payload when none exists', () => {
  const { bound } = partitionFields(fields, []);
  assert.equal(bound.length, 0);
  const { available } = partitionFields(fields, []);
  assert.deepEqual(available[0].binding, {
    queryFieldId: 'region_id',
    targetColumn: 'r_id',
    displayName: 'Region',
    exportEnabled: true,
  });
});

test('partitionFields skips fields without a stable fieldId', () => {
  const result = partitionFields([{ displayName: 'No id' } as QueryDatasetField], []);
  assert.equal(result.available.length, 0);
  assert.equal(result.bound.length, 0);
});

test('moveKanbanItem binds an available field into the empty bound list', () => {
  const partitioned = partitionFields(fields, []);
  const move = moveKanbanItem(partitioned, kanbanItemId('amount', 'available'), null);
  assert.ok(move);
  assert.equal(move?.nextAvailable.length, 2);
  assert.equal(move?.nextBound.length, 1);
  assert.equal(move?.nextBound[0].fieldId, 'amount');
});

test('moveKanbanItem inserts the new bound item before an existing bound target', () => {
  const initial = partitionFields(fields, [
    { queryFieldId: 'created_at', targetColumn: 'C' },
  ]);
  const move = moveKanbanItem(initial, kanbanItemId('region_id', 'available'), kanbanItemId('created_at', 'bound'));
  assert.ok(move);
  assert.deepEqual(move?.nextBound.map((item) => item.fieldId), ['region_id', 'created_at']);
  assert.equal(move?.nextAvailable.length, 1);
  assert.equal(move?.nextAvailable[0].fieldId, 'amount');
});

test('moveKanbanItem reorders within the bound collection without changing field ids', () => {
  const initial = partitionFields(fields, [
    { queryFieldId: 'region_id', targetColumn: 'A' },
    { queryFieldId: 'amount', targetColumn: 'B' },
  ]);
  const move = moveKanbanItem(initial, kanbanItemId('amount', 'bound'), kanbanItemId('region_id', 'bound'));
  assert.ok(move);
  assert.deepEqual(move?.nextBound.map((item) => item.fieldId), ['amount', 'region_id']);
  assert.equal(move?.nextAvailable.length, 1);
});

test('moveKanbanItem dropping an already-bound available field is a no-op for duplicates', () => {
  const initial = partitionFields(fields, [
    { queryFieldId: 'region_id', targetColumn: 'A' },
  ]);
  const duplicate = moveKanbanItem(initial, kanbanItemId('region_id', 'available'), kanbanItemId('amount', 'available'));
  assert.equal(duplicate, null);
});

test('moveKanbanItem returns null when the active id is unknown', () => {
  const initial = partitionFields(fields, []);
  const result = moveKanbanItem(initial, kanbanItemId('ghost', 'available'), null);
  assert.equal(result, null);
});

test('moveKanbanItem sends a bound item back into the available list when dropped over an available target', () => {
  const initial = partitionFields(fields, [
    { queryFieldId: 'region_id', targetColumn: 'A' },
  ]);
  const move = moveKanbanItem(initial, kanbanItemId('region_id', 'bound'), kanbanItemId('amount', 'available'));
  assert.ok(move);
  assert.equal(move?.nextBound.length, 0);
  assert.deepEqual(move?.nextAvailable.map((item) => item.fieldId), ['region_id', 'amount', 'created_at']);
});

test('unbindItem removes a bound item by fieldId and returns it to available', () => {
  const initial = partitionFields(fields, [
    { queryFieldId: 'region_id', targetColumn: 'A' },
  ]);
  const result = unbindItem(initial, 'region_id');
  assert.equal(result.bound.length, 0);
  assert.deepEqual(result.available.map((item) => item.fieldId), ['amount', 'created_at', 'region_id']);
});

test('unbindItem is a no-op when the fieldId is not currently bound', () => {
  const initial = partitionFields(fields, []);
  const result = unbindItem(initial, 'amount');
  assert.deepEqual(result, initial);
});

test('updateBoundItemBinding merges a partial binding patch', () => {
  const initial = partitionFields(fields, [
    { queryFieldId: 'region_id', targetColumn: 'A', displayName: 'Region' },
  ]);
  const next = updateBoundItemBinding(initial.bound, 'region_id', { displayName: 'Sales Region', alignment: 'CENTER' });
  assert.equal(next[0].binding.displayName, 'Sales Region');
  assert.equal(next[0].binding.alignment, 'CENTER');
  assert.equal(next[0].binding.targetColumn, 'A');
});

test('bindingsAreEqual ignores object key order but preserves values and array order', () => {
  const first = [
    { queryFieldId: 'region_id', targetColumn: 'A', displayName: 'Region' },
    { queryFieldId: 'amount', targetColumn: 'B', displayName: 'Amount' },
  ];
  const sameBindingsWithDifferentKeyOrder = [
    { displayName: 'Region', targetColumn: 'A', queryFieldId: 'region_id' },
    { displayName: 'Amount', queryFieldId: 'amount', targetColumn: 'B' },
  ];

  assert.equal(bindingsAreEqual(first, sameBindingsWithDifferentKeyOrder), true);
  assert.equal(bindingsAreEqual(first, [...sameBindingsWithDifferentKeyOrder].reverse()), false);
  assert.equal(bindingsAreEqual(first, [{ ...sameBindingsWithDifferentKeyOrder[0], targetColumn: 'C' }, sameBindingsWithDifferentKeyOrder[1]]), false);
});
test('itemsToBindings preserves displayName, targetColumn, format, nullDisplay, alignment, exportEnabled', () => {
  const partitioned = partitionFields(fields, [
    { queryFieldId: 'region_id', targetColumn: 'A', displayName: 'Region', exportEnabled: false },
  ]);
  const bindings = itemsToBindings(partitioned.bound);
  assert.equal(bindings.length, 1);
  assert.equal(bindings[0].queryFieldId, 'region_id');
  assert.equal(bindings[0].targetColumn, 'A');
  assert.equal(bindings[0].displayName, 'Region');
  assert.equal(bindings[0].exportEnabled, false);
});


test('itemsToBindings falls back to sourceColumn then to a synthesized letter when no targetColumn is set', () => {
  const noSourceField: QueryDatasetField = { fieldId: 'synth', displayName: 'Synthetic' };
  const partitioned = partitionFields([...fields, noSourceField], []);
  const mixed = [
    { ...partitioned.available[0], binding: { ...partitioned.available[0].binding, targetColumn: undefined } },
    { ...partitioned.available[1], binding: { ...partitioned.available[1].binding, targetColumn: undefined } },
    {
      ...partitioned.available[2],
      sourceColumn: undefined,
      binding: { ...partitioned.available[2].binding, targetColumn: undefined },
    },
  ];
  const bindings = itemsToBindings(mixed);
  assert.equal(bindings[0].targetColumn, 'r_id');
  assert.equal(bindings[1].targetColumn, 'total');
  assert.equal(bindings[2].targetColumn, 'C');
});

test('itemsToBindings never emits duplicate queryFieldIds', () => {
  const partitioned = partitionFields(fields, []);
  const bindings = itemsToBindings(partitioned.available);
  const ids = new Set<string>();
  bindings.forEach((binding) => {
    if (!binding.queryFieldId) return;
    assert.equal(ids.has(binding.queryFieldId), false, `duplicate ${binding.queryFieldId}`);
    ids.add(binding.queryFieldId);
  });
});

test('columnLetter emits A..Z then AA, AB', () => {
  assert.equal(columnLetter(0), 'A');
  assert.equal(columnLetter(25), 'Z');
  assert.equal(columnLetter(26), 'AA');
  assert.equal(columnLetter(27), 'AB');
});

test('describeKanbanMove speaks the destination and the position', () => {
  assert.equal(
    describeKanbanMove(
      { fieldId: 'amount', displayName: 'Net Amount', binding: { queryFieldId: 'amount' }, queryField: null },
      'bound',
      0,
    ),
    'Net Amount bound at position 1',
  );
  assert.equal(
    describeKanbanMove(
      { fieldId: 'amount', displayName: 'Net Amount', binding: { queryFieldId: 'amount' }, queryField: null },
      'available',
      3,
    ),
    'Net Amount Available fields at position 4',
  );
});
