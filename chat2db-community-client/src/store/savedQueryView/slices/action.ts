import type { StateCreator } from 'zustand';
import { SavedQueryViewStore } from '../store';
import * as savedQueryViewService from '@/service/savedQueryView';
import { IPageParams } from '@/typings/common';

export interface CommonAction {
  savedQueryViewList: (params: IPageParams & { searchKey?: string }) => Promise<void>;
  savedQueryViewDetail: (id: number) => Promise<void>;
  createSavedQueryView: (data: any) => Promise<number>;
  updateSavedQueryView: (data: any) => Promise<void>;
  deleteSavedQueryView: (id: number) => Promise<void>;
  publish: (id: number) => Promise<void>;
  disable: (id: number) => Promise<void>;
  copy: (id: number, name?: string) => Promise<number>;
  getPreview: (id: number, pageNo?: number, pageSize?: number, filterOverrides?: string) => Promise<void>;
  setLoading: (loading: boolean) => void;
}

export const createCommonAction: StateCreator<SavedQueryViewStore, [['zustand/devtools', never]], [], CommonAction> = (
  set,
  get,
) => ({
  savedQueryViewList: async (params) => {
    set({ loading: true }, false, 'savedQueryViewList/loading');
    try {
      const res = await savedQueryViewService.savedQueryViewList(params);
      set(
        { list: res.data, total: res.total, pageNo: res.pageNo, pageSize: res.pageSize, loading: false },
        false,
        'savedQueryViewList/done',
      );
    } catch (e) {
      set({ loading: false }, false, 'savedQueryViewList/error');
      throw e;
    }
  },
  savedQueryViewDetail: async (id) => {
    set({ loading: true }, false, 'savedQueryViewDetail/loading');
    try {
      const res = await savedQueryViewService.savedQueryViewDetail({ id });
      set({ current: res, loading: false }, false, 'savedQueryViewDetail/done');
    } catch (e) {
      set({ loading: false }, false, 'savedQueryViewDetail/error');
      throw e;
    }
  },
  createSavedQueryView: async (data) => {
    const id = await savedQueryViewService.createSavedQueryView(data);
    return id;
  },
  updateSavedQueryView: async (data) => {
    await savedQueryViewService.updateSavedQueryView(data);
  },
  deleteSavedQueryView: async (id) => {
    await savedQueryViewService.deleteSavedQueryView({ id });
    // Refresh list after delete
    const { pageNo, pageSize } = get();
    await get().savedQueryViewList({ pageNo, pageSize });
  },
  publish: async (id) => {
    await savedQueryViewService.publishSavedQueryView({ id });
    await get().savedQueryViewDetail(id);
  },
  disable: async (id) => {
    await savedQueryViewService.disableSavedQueryView({ id });
    await get().savedQueryViewDetail(id);
  },
  copy: async (id, name) => {
    return await savedQueryViewService.copySavedQueryView({ id, name });
  },
  getPreview: async (id, pageNo, pageSize, filterOverrides) => {
    set({ loading: true }, false, 'preview/loading');
    try {
      const res = await savedQueryViewService.previewSavedQueryView({ id, pageNo, pageSize, filterOverrides });
      set({ preview: res, loading: false }, false, 'preview/done');
    } catch (e) {
      set({ loading: false }, false, 'preview/error');
      throw e;
    }
  },
  setLoading: (loading) => {
    set({ loading }, false, 'setLoading');
  },
});