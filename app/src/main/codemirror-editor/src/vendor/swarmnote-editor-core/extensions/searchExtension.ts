/**
 * 搜索扩展 - CodeMirror 搜索功能的封装和状态管理
 * 
 * **功能：**
 * 1. 集成 @codemirror/search 模块提供搜索能力
 * 2. 追踪搜索状态（查询字符串、匹配数、当前匹配等）
 * 3. 通过回调通知宿主应用搜索状态变化
 * 4. 支持外部控制搜索面板的打开/关闭
 */
import { StateEffect, type EditorState, type Extension } from '@codemirror/state';
import {
  closeSearchPanel,
  getSearchQuery,
  openSearchPanel,
  search,
  SearchQuery,
  searchPanelOpen,
  setSearchQuery,
} from '@codemirror/search';
import { EditorView, ViewPlugin, type ViewUpdate } from '@codemirror/view';
import type { SearchState } from '../types';
import { DEFAULT_SEARCH_STATE } from '../types';

/** 搜索扩展选项 */
export interface SearchExtensionOptions {
  /** 搜索状态变化回调 */
  onSearchStateChange?: (state: SearchState | null, source?: string) => void;
}

/** 搜索变更来源 Effect - 用于标记搜索状态变化的触发源 */
export const searchChangeSourceEffect = StateEffect.define<string>();

/**
 * 检查是否有搜索值
 * 
 * @param state - 编辑器状态
 * @returns 如果搜索面板打开或有搜索/替换文本则返回 true
 */
function hasSearchValue(state: EditorState): boolean {
  const query = getSearchQuery(state);
  return searchPanelOpen(state) || query.search.length > 0 || query.replace.length > 0;
}

/**
 * 统计搜索匹配数量并找到当前激活的匹配索引
 * 
 * @param state - 编辑器状态
 * @param query - 搜索查询对象
 * @returns 包含总匹配数和当前匹配索引的对象
 */
function countSearchMatches(state: EditorState, query: SearchQuery) {
  // 如果查询无效或搜索字符串为空，返回空结果
  if (!query.valid || query.search.length === 0) {
    return {
      activeMatchIndex: null,
      totalMatches: 0,
    };
  }

  const selection = state.selection.main;
  let totalMatches = 0;
  let activeMatchIndex: number | null = null;
  // 获取所有匹配的游标
  const cursor = query.getCursor(state);

  // 遍历所有匹配
  for (let match = cursor.next(); !match.done; match = cursor.next()) {
    // 跳过空匹配（from === to）
    if (match.value.from === match.value.to) {
      continue;
    }

    // 检查此匹配是否与当前选区重合（即当前激活的匹配）
    if (selection.from === match.value.from && selection.to === match.value.to) {
      activeMatchIndex = totalMatches;
    }

    totalMatches += 1;
  }

  return {
    activeMatchIndex,
    totalMatches,
  };
}

/**
 * 标准化搜索状态 - 使用默认值填充缺失字段
 * 
 * @param state - 原始搜索状态
 * @returns 标准化后的搜索状态
 */
function normalizeSearchState(state: SearchState | null): SearchState | null {
  if (!state) {
    return null;
  }

  // 合并默认值和提供的状态（提供的值覆盖默认值）
  return {
    ...DEFAULT_SEARCH_STATE,
    ...state,
  };
}

/**
 * 比较两个搜索状态是否相等
 * 
 * @param a - 第一个搜索状态
 * @param b - 第二个搜索状态
 * @returns 如果所有字段都相同则返回 true
 */
function isSearchStateEqual(a: SearchState | null, b: SearchState | null): boolean {
  // 引用相同，直接返回 true
  if (a === b) {
    return true;
  }

  // 其中一个为 null，不相等
  if (!a || !b) {
    return false;
  }

  // 逐个字段比较
  return (
    a.query === b.query &&
    a.replaceQuery === b.replaceQuery &&
    a.caseSensitive === b.caseSensitive &&
    a.wholeWord === b.wholeWord &&
    a.regexp === b.regexp &&
    a.isOpen === b.isOpen &&
    a.activeMatchIndex === b.activeMatchIndex &&
    a.totalMatches === b.totalMatches
  );
}

/**
 * 从视图更新中提取搜索变更来源
 * 
 * @param update - 视图更新对象
 * @returns 变更来源字符串，如果没有则返回 undefined
 */
function getSearchChangeSource(update: ViewUpdate): string | undefined {
  let source: string | undefined;

  // 遍历所有事务的 effects
  for (const transaction of update.transactions) {
    for (const effect of transaction.effects) {
      if (effect.is(searchChangeSourceEffect)) {
        source = effect.value;
      }
    }
  }

  return source;
}

/**
 * 判断是否应该触发搜索状态变化回调
 * 
 * **触发条件：**
 * 1. 文档内容或选区变化，且之前或之后有搜索值
 * 2. 搜索面板打开/关闭状态变化
 * 3. 设置了新的搜索查询
 * 4. 触发了搜索变更来源 Effect
 * 
 * @param update - 视图更新对象
 * @returns 是否应该触发回调
 */
function shouldEmitSearchStateChange(update: ViewUpdate): boolean {
  // 文档或选区变化时，检查是否有搜索值
  if (update.docChanged || update.selectionSet) {
    return hasSearchValue(update.startState) || hasSearchValue(update.state);
  }

  // 检查事务中是否有搜索相关的变化
  return update.transactions.some((transaction) => {
    // 搜索面板打开/关闭状态变化
    if (searchPanelOpen(transaction.startState) !== searchPanelOpen(transaction.state)) {
      return true;
    }

    // 设置了新的搜索查询或触发了变更来源 Effect
    return transaction.effects.some(
      (effect) =>
        effect.is(setSearchQuery) ||
        effect.is(searchChangeSourceEffect),
    );
  });
}

/**
 * 创建外部搜索面板（占位符）
 * 
 * **说明：**
 * 此函数返回一个空的 DOM 元素作为搜索面板的占位符。
 * 实际的搜索 UI 由宿主应用（如 React 组件）提供，
 * CodeMirror 只负责管理搜索状态和高亮匹配。
 * 
 * @returns 搜索面板对象
 */
function createExternalSearchPanel() {
  const dom = document.createElement('div');
  return {
    dom,
    mount() {},  // 挂载时无操作
    destroy() {},  // 销毁时无操作
  };
}

/**
 * 将搜索状态转换为 SearchQuery 对象
 * 
 * @param state - 搜索状态
 * @returns CodeMirror SearchQuery 对象
 */
function toSearchQuery(state: SearchState | null): SearchQuery {
  const normalizedState = normalizeSearchState(state);

  return new SearchQuery({
    search: normalizedState?.query ?? '',
    replace: normalizedState?.replaceQuery ?? '',
    caseSensitive: normalizedState?.caseSensitive ?? DEFAULT_SEARCH_STATE.caseSensitive,
    wholeWord: normalizedState?.wholeWord ?? DEFAULT_SEARCH_STATE.wholeWord,
    regexp: normalizedState?.regexp ?? DEFAULT_SEARCH_STATE.regexp,
  });
}

/**
 * 创建搜索扩展
 * 
 * **功能：**
 * 1. 集成 CodeMirror 搜索模块
 * 2. 监听搜索状态变化并通过回调通知宿主应用
 * 
 * @param options - 配置选项
 * @returns CodeMirror 扩展数组
 */
export function createSearchExtension(
  options: SearchExtensionOptions = {},
): Extension {
  const { onSearchStateChange } = options;

  return [
    // 集成搜索模块，使用外部面板（占位符）
    search({
      createPanel: createExternalSearchPanel,
    }),
    // 视图插件：监听搜索状态变化并触发回调
    ViewPlugin.fromClass(
      class {
        update(update: ViewUpdate) {
          // 如果没有回调或不需要触发，直接返回
          if (!onSearchStateChange || !shouldEmitSearchStateChange(update)) {
            return;
          }

          // 获取前后状态的搜索信息
          const previousState = getSearchState(update.startState);
          const nextState = getSearchState(update.state);
          const source = getSearchChangeSource(update);

          // 如果状态发生变化或有明确的变更来源，触发回调
          if (!isSearchStateEqual(previousState, nextState) || source) {
            onSearchStateChange(nextState, source);
          }
        }
      },
    ),
  ];
}

/**
 * 获取当前搜索状态
 * 
 * @param state - 编辑器状态
 * @returns 搜索状态对象，如果没有搜索活动则返回 null
 */
export function getSearchState(state: EditorState): SearchState | null {
  const query = getSearchQuery(state);
  const isOpen = searchPanelOpen(state);

  // 如果面板关闭且没有搜索/替换文本，返回 null
  if (!isOpen && query.search.length === 0 && query.replace.length === 0) {
    return null;
  }

  // 统计匹配数量和当前激活的匹配索引
  const { activeMatchIndex, totalMatches } = countSearchMatches(state, query);

  return {
    ...DEFAULT_SEARCH_STATE,
    query: query.search,
    replaceQuery: query.replace,
    caseSensitive: query.caseSensitive,
    wholeWord: query.wholeWord,
    regexp: query.regexp,
    isOpen,
    activeMatchIndex,
    totalMatches,
  };
}

/**
 * 设置搜索状态
 * 
 * **工作流程：**
 * 1. 标准化搜索状态
 * 2. 比较新旧查询，如果有变化则分发 setSearchQuery effect
 * 3. 如果提供了 source，同时分发 searchChangeSourceEffect
 * 4. 根据 isOpen 字段控制搜索面板的打开/关闭
 * 
 * @param view - 编辑器视图
 * @param state - 要设置的搜索状态
 * @param source - 变更来源（可选）
 */
export function setSearchState(
  view: EditorView,
  state: SearchState | null,
  source?: string,
): void {
  const normalizedState = normalizeSearchState(state);
  const currentQuery = getSearchQuery(view.state);
  const nextQuery = toSearchQuery(normalizedState);

  // 如果查询有变化，分发 effect
  if (!currentQuery.eq(nextQuery)) {
    const effects: StateEffect<unknown>[] = [setSearchQuery.of(nextQuery)];
    if (source) {
      effects.unshift(searchChangeSourceEffect.of(source));
    }

    view.dispatch({ effects });
  }

  // 检查是否需要打开/关闭搜索面板
  const shouldOpen = normalizedState?.isOpen ?? false;
  const isOpen = searchPanelOpen(view.state);
  if (shouldOpen === isOpen) {
    return;  // 状态一致，无需操作
  }

  if (shouldOpen) {
    openSearchPanel(view);  // 打开面板
    return;
  }

  closeSearchPanel(view);  // 关闭面板
}

/**
 * 清除搜索状态
 * 
 * @param view - 编辑器视图
 * @param source - 变更来源（可选）
 */
export function clearSearch(view: EditorView, source?: string): void {
  setSearchState(view, null, source);
}
