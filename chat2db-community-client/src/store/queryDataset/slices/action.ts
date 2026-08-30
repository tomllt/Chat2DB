import type { StateCreator } from 'zustand';
import { QueryDatasetStore } from '../store';
import * as queryDatasetService from '@/service/queryDataset';
import { IPageParams } from '@/typings/common';

export interface CommonAction {
  queryDatasetList: (params: IPageParams & { searchKey?: string }) => Promise<void>;
  queryDatasetDetail: (id: number) => Promise<void>;
  createQueryDataset: (data: any) => Promise<number>;
  updateQueryDataset: (data: any) => Promise<void>;
  deleteQueryDataset: (id: number) => Promise<void>;
  publish: (id: number) => Promise<void>;
  disable: (id: number) => Promise<void>;
  copy: (id: number, name?: string) => Promise<number>;
  getPreview: (id: number, pageNo?: number, pageSize?: number) => Promise<void>;
  setLoading: (loading: boolean) => void;
}

export const createCommonAction: StateCreator<QueryDatasetStore, [['zustand/devtools', never]], [], CommonAction> = (
  set,
  get,
) => ({
  queryDatasetList: async (params) => {
    set({ loading: true }, false, 'queryDatasetList/loading');
    try {
      const res = await queryDatasetService.queryDatasetList(params);
      set(
        { list: res.data, total: res.total, pageNo: res.pageNo, pageSize: res.pageSize, loading: false },
        false,
        'queryDatasetList/done',
      );
    } catch (e) {
      set({ loading: false }, false, 'queryDatasetList/error');
      throw e;
    }
  },
  queryDatasetDetail: async (id) => {
    set({ loading: true }, false, 'queryDatasetDetail/loading');
    try {
      const res = await queryDatasetService.queryDatasetDetail({ id });
      set({ current: res, loading: false }, false, 'queryDatasetDetail/done');
    } catch (e) {
      set({ loading: false }, false, 'queryDatasetDetail/error');
      throw e;
    }
  },
  createQueryDataset: async (data) => {
    const id = await queryDatasetService.createQueryDataset(data);
    return id;
  },
  updateQueryDataset: async (data) => {
    await queryDatasetService.updateQueryDataset(data);
  },
  deleteQueryDataset: async (id) => {
    await queryDatasetService.deleteQueryDataset({ id });
    // Refresh list after delete
    const { pageNo, pageSize } = get();
    await get().queryDatasetList({ pageNo, pageSize });
  },
  publish: async (id) => {
    await queryDatasetService.publishQueryDataset({ id });
    await get().queryDatasetDetail(id);
  },
  disable: async (id) => {
    await queryDatasetService.disableQueryDataset({ id });
    await get().queryDatasetDetail(id);
  },
  copy: async (id, name) => {
    return await queryDatasetService.copyQueryDataset({ id, name });
  },
  getPreview: async (id, pageNo, pageSize) => {
    set({ loading: true }, false, 'preview/loading');
    try {
      const res = await queryDatasetService.previewQueryDataset({ id, pageNo, pageSize });
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
