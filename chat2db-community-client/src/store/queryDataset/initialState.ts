import { QueryDataset, PreviewResult } from '@/typings/queryDataset';

export interface QueryDatasetState {
  list: QueryDataset[];
  current: QueryDataset | null;
  loading: boolean;
  preview: PreviewResult | null;
  total: number;
  pageNo: number;
  pageSize: number;
}

export const initialState: QueryDatasetState = {
  list: [],
  current: null,
  loading: false,
  preview: null,
  total: 0,
  pageNo: 1,
  pageSize: 20,
};
