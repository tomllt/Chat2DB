import { SavedQueryView } from '@/typings/savedQueryView';
import { PreviewResult } from '@/typings/queryDataset';

export interface SavedQueryViewState {
  list: SavedQueryView[];
  current: SavedQueryView | null;
  loading: boolean;
  preview: PreviewResult | null;
  total: number;
  pageNo: number;
  pageSize: number;
}

export const initialState: SavedQueryViewState = {
  list: [],
  current: null,
  loading: false,
  preview: null,
  total: 0,
  pageNo: 1,
  pageSize: 20,
};