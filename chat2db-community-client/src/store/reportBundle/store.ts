import { devtools } from 'zustand/middleware';
import { createWithEqualityFn } from 'zustand/traditional';
import { shallow } from 'zustand/shallow';
import type { StateCreator } from 'zustand';
import type { IReportBundleVersion } from '@/typings/reportBundle';
import { initialState, IReportBundleState } from './initialState';

export interface IReportBundleKeyValidity {
  readonly validRowKeys: readonly string[];
  readonly isComplete: boolean;
}

export interface IReportBundleActions {
  readonly selectBundle: (id: number | null) => void;
  readonly selectVersion: (id: number | null) => void;
  readonly setSelectedRowKeys: (keys: readonly string[]) => void;
  readonly retainSelectedRowKeysForPage: (pageRowKeys: readonly string[]) => void;
  readonly reconcileSelectedRowKeys: (validity: IReportBundleKeyValidity) => void;
  readonly setCurrent: (bundle: IReportBundleState['current']) => void;
  readonly setVersions: (versions: readonly IReportBundleVersion[]) => void;
  readonly setCurrentVersion: (version: IReportBundleState['currentVersion']) => void;
  readonly setLoading: (loading: boolean) => void;
  readonly reset: () => void;
}

export type IReportBundleStore = IReportBundleState & IReportBundleActions;

type StoreCreator = StateCreator<IReportBundleStore, [['zustand/devtools', never]]>;

const createStore: StoreCreator = (set, get) => ({
  ...initialState,
  selectBundle: (selectedBundleId) => {
    set(
      {
        selectedBundleId,
        selectedVersionId: null,
        current: null,
        currentVersion: null,
        versions: [],
        selectedRowKeys: [],
      },
      false,
      'selectBundle',
    );
  },
  selectVersion: (selectedVersionId) => {
    set({ selectedVersionId, currentVersion: null, selectedRowKeys: [] }, false, 'selectVersion');
  },
  setSelectedRowKeys: (selectedRowKeys) => {
    set({ selectedRowKeys: [...selectedRowKeys] }, false, 'setSelectedRowKeys');
  },
  retainSelectedRowKeysForPage: () => {
    set({}, false, 'retainSelectedRowKeysForPage');
  },
  reconcileSelectedRowKeys: ({ validRowKeys, isComplete }) => {
    if (!isComplete) {
      return;
    }
    const validKeys = new Set(validRowKeys);
    set(
      { selectedRowKeys: get().selectedRowKeys.filter((key) => validKeys.has(key)) },
      false,
      'reconcileSelectedRowKeys',
    );
  },
  setCurrent: (current) => set({ current }, false, 'setCurrent'),
  setVersions: (versions) => set({ versions: [...versions] }, false, 'setVersions'),
  setCurrentVersion: (currentVersion) => set({ currentVersion }, false, 'setCurrentVersion'),
  setLoading: (loading) => set({ loading }, false, 'setLoading'),
  reset: () => set(initialState, false, 'reset'),
});

export const useReportBundleStore = createWithEqualityFn<IReportBundleStore>()(
  devtools(createStore, { name: 'Chat2DB_ReportBundle_Store' }),
  shallow,
);

export const clearReportBundleStore = () => {
  useReportBundleStore.setState(initialState);
};
