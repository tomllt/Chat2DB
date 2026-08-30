import { devtools } from 'zustand/middleware';
import { createWithEqualityFn } from 'zustand/traditional';
import { shallow } from 'zustand/shallow';
import { SavedQueryViewState, initialState } from './initialState';
import { CommonAction, createCommonAction } from './slices/action';
import { StateCreator } from 'zustand';

export type SavedQueryViewAction = CommonAction;
export type SavedQueryViewStore = SavedQueryViewState & SavedQueryViewAction;

const createStore: StateCreator<SavedQueryViewStore, [['zustand/devtools', never]]> = (...parameters) => ({
  ...initialState,
  ...createCommonAction(...parameters),
});

export const useSavedQueryViewStore = createWithEqualityFn<SavedQueryViewStore>()(
  devtools(createStore, { name: 'Chat2DB_SavedQueryView_Store' }),
  shallow,
);

export const clearSavedQueryViewStore = () => {
  useSavedQueryViewStore.setState(initialState);
};