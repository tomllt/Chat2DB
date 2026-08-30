import { devtools } from 'zustand/middleware';
import { createWithEqualityFn } from 'zustand/traditional';
import { shallow } from 'zustand/shallow';
import { QueryDatasetState, initialState } from './initialState';
import { CommonAction, createCommonAction } from './slices/action';
import { StateCreator } from 'zustand';

export type QueryDatasetAction = CommonAction;
export type QueryDatasetStore = QueryDatasetState & QueryDatasetAction;

const createStore: StateCreator<QueryDatasetStore, [['zustand/devtools', never]]> = (...parameters) => ({
  ...initialState,
  ...createCommonAction(...parameters),
});

export const useQueryDatasetStore = createWithEqualityFn<QueryDatasetStore>()(
  devtools(createStore, { name: 'Chat2DB_QueryDataset_Store' }),
  shallow,
);

export const clearQueryDatasetStore = () => {
  useQueryDatasetStore.setState(initialState);
};
