import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { Layers, LayoutDashboard, MessageSquarePlus } from 'lucide-react';

import { CORE_MAIN_NAV_KEYS, createCoreMainNavItems } from './navigationItems';

const items = createCoreMainNavItems({
  stream: { component: 'stream-component', name: 'Stream' },
  workspace: { component: 'workspace-component', name: 'Workspace' },
  dashboard: { component: 'dashboard-component', name: 'Dashboard' },
});

assert.deepEqual(
  items.map((item) => item.key),
  CORE_MAIN_NAV_KEYS,
  'shared main navigation should keep the Stream, Workspace, Dashboard order',
);
assert.strictEqual(items[0].icon, MessageSquarePlus, 'Stream should use the semantic Lucide icon');
assert.strictEqual(items[1].icon, Layers, 'Workspace should use the semantic Lucide icon');
assert.strictEqual(
  items[2].icon,
  LayoutDashboard,
  'Dashboard should use the semantic Lucide icon',
);
assert.ok(
  items.every((item) => item.isLoad === false),
  'shared navigation items should remain lazy by default',
);

for (const page of ['src/pages/main/CommunityMainPage.tsx', 'src/pages/main/index.tsx']) {
  assert.match(
    readFileSync(page, 'utf8'),
    /createCoreMainNavItems/,
    `${page} should consume the shared core navigation definition`,
  );
}

const communityMainPage = readFileSync('src/pages/main/CommunityMainPage.tsx', 'utf8');
for (const coreKey of CORE_MAIN_NAV_KEYS) {
  assert.match(
    communityMainPage,
    new RegExp(`key: '${coreKey}'|${coreKey}: \\{`),
    `Community should retain its ${coreKey} navigation entry`,
  );
}
for (const feature of [
  ['query-dataset', 'QueryDatasetPage'],
  ['saved-query-view', 'SavedQueryViewPage'],
  ['excel-report-template', 'ExcelReportTemplatePage'],
  ['report-bundle', 'ReportBundlePage'],
] as const) {
  assert.match(
    communityMainPage,
    new RegExp(`key: '${feature[0]}'[\\s\\S]*component: <${feature[1]} />`),
    `Community should register the ${feature[0]} page in its navigation`,
  );
}

const commercialMainPage = readFileSync('src/pages/main/index.tsx', 'utf8');
assert.match(commercialMainPage, /icon: 'icon-a-xunwen1'/, 'Team should keep its existing iconfont icon');
for (const retiredIconCode of ['icon-chat-alt-21', 'icon-gongxiang-', 'icon-chart-square-bar']) {
  assert.doesNotMatch(
    commercialMainPage,
    new RegExp(retiredIconCode),
    `commercial main navigation should not use ${retiredIconCode}`,
  );
}

const reportBundlePage = readFileSync('src/pages/main/report-bundle/index.tsx', 'utf8');
assert.match(
  reportBundlePage,
  /onClick=\{\(\) => open\(record, 'editor'\)\}/,
  'Configure should navigate to the registered editor route instead of opening an unregistered path',
);
assert.match(
  reportBundlePage,
  /onClick=\{\(\) => openEdit\(record\)\}/,
  'Edit should still surface the implemented in-place modal for the bundle row',
);

const umiConfig = readFileSync('.umirc.ts', 'utf8');
for (const route of [
  ['/report-bundle/editor', '@/pages/main/report-bundle/editor'],
  ['/report-bundle/data-view', '@/pages/main/report-bundle/data-view'],
] as const) {
  assert.match(
    umiConfig,
    new RegExp(`path: '${route[0]}'[\\s\\S]*component: '${route[1]}'`),
    `the ${route[0]} deep-link must remain registered to its implemented component`,
  );
}

console.log('Main navigation item tests passed.');
