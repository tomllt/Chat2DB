import assert from 'node:assert/strict';
import { test } from 'node:test';
import {
  createViewFilter,
  hasStableRowKeys,
  resolveFilterFieldId,
} from './dataViewLogic';

const bindings = [
  { queryFieldId: 'region_id', targetColumn: 'Region', displayName: 'Sales Region' },
  { queryFieldId: 'amount', targetColumn: 'Total', displayName: 'Net Amount' },
];

test('resolveFilterFieldId maps displayed targetColumn back to queryFieldId', () => {
  assert.equal(resolveFilterFieldId('Region', bindings), 'region_id');
  assert.equal(resolveFilterFieldId('Total', bindings), 'amount');
});

test('resolveFilterFieldId matches displayName alias when targetColumn does not match', () => {
  const aliasedBindings = [
    { queryFieldId: 'region_id', targetColumn: 'r_id', displayName: 'Region' },
  ];
  assert.equal(resolveFilterFieldId('Region', aliasedBindings), 'region_id');
});

test('resolveFilterFieldId returns undefined for unmatched columns when bindings exist', () => {
  assert.equal(resolveFilterFieldId('Not Mapped', bindings), undefined);
});

test('resolveFilterFieldId falls back to displayed column when no bindings exist', () => {
  assert.equal(resolveFilterFieldId('Free Column', []), 'Free Column');
});

test('createViewFilter resolves aliased column title to source queryFieldId', () => {
  const filter = createViewFilter('Region', bindings, 'EQ', 'EU');
  assert.deepEqual(filter, { fieldId: 'region_id', operator: 'EQ', value: 'EU' });
});

test('createViewFilter returns undefined for aliased columns lacking a queryFieldId mapping', () => {
  assert.equal(createViewFilter('Not Mapped', bindings, 'EQ', 'x'), undefined);
});

test('createViewFilter trims values and rejects empty input', () => {
  assert.deepEqual(createViewFilter('Region', bindings, 'CONTAINS', '  EU  '), {
    fieldId: 'region_id',
    operator: 'CONTAINS',
    value: 'EU',
  });
  assert.equal(createViewFilter('Region', bindings, 'EQ', '   '), undefined);
});

test('hasStableRowKeys requires every row to have a non-empty backend key', () => {
  assert.equal(hasStableRowKeys([{ a: 1 }, { a: 2 }], ['k1', 'k2']), true);
  assert.equal(hasStableRowKeys([{ a: 1 }, { a: 2 }], ['k1']), false);
  assert.equal(hasStableRowKeys([{ a: 1 }, { a: 2 }], ['k1', '']), false);
  assert.equal(hasStableRowKeys([], []), false);
});
