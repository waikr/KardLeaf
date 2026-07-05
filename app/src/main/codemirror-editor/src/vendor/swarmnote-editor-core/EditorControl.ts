import { copyLineDown, deleteLine, indentLess, indentMore, redo, selectAll, undo } from '@codemirror/commands';
import { findNext, findPrevious } from '@codemirror/search';
import { type Compartment, EditorSelection } from '@codemirror/state';
import { EditorView } from '@codemirror/view';
import {
  clearSearch,
  getEditorSettings,
  getEditorSettingsEffects,
  getSearchState,
  setSearchState,
  type EditorSettingsExtensionRuntime,
} from './extensions';
import {
  computeSelectionFormatting,
  cycleHeading,
  insertCodeBlock,
  insertHorizontalRule,
  insertImage,
  insertLink,
  insertTable,
  toggleBlockquote,
  toggleBold,
  toggleCode,
  toggleHeading,
  toggleHighlight,
  toggleItalic,
  toggleList,
  toggleStrike,
} from './editorCommands';
import { insertLineAfter } from './editorCommands/insertLineAfter';
import { sortSelectedLines } from './editorCommands/sortSelectedLines';
import { jumpToHash } from './editorCommands/jumpToHash';
import type { PluginHost } from './pluginHost';
import type {
  EditorCommandType,
  EditorControl,
  EditorSettings,
  EditorSettingsUpdate,
  SearchState,
} from './types';
import { createSelectionRange } from './utils';

/**
 * 编辑器控制器实现类
 * 
 * 这是编辑器的核心控制接口，负责封装所有对 CodeMirror 6 编辑器的操作。
 * 外部宿主应用（如 SwarmNote 桌面端/移动端）通过这个接口与编辑器交互，
 * 而不需要直接操作 CodeMirror 的底层 API。
 * 
 * 主要职责：
 * 1. 执行编辑命令（格式化、插入内容等）
 * 2. 管理文本内容和选区
 * 3. 控制编辑器设置和搜索状态
 * 4. 管理焦点和滚动行为
 */
export class EditorControlImpl implements EditorControl {
  /** 记录上一次设置的底部滚动边距，用于避免重复配置 */
  private lastScrollBottomMargin = 0;

  constructor(
    /** CodeMirror 6 编辑器视图实例 */
    public readonly view: EditorView,
    /** 构造函数选项 */
    private readonly options: {
      /** 编辑器设置的运行时扩展，用于动态更新设置 */
      settingsRuntime: EditorSettingsExtensionRuntime;
      /** 滚动边距的动态配置容器（Compartment） */
      scrollMarginsCompartment: Compartment;
      /** 内容内边距的动态配置容器（Compartment） */
      contentPaddingCompartment: Compartment;
      /** Plugin host：管理 plugin 注册的命令、disposable 与实例生命周期 */
      pluginHost: PluginHost;
      /** 销毁时的回调函数 */
      onDestroy?: () => void;
    },
  ) {}

  /**
   * 检查是否支持指定的编辑命令
   * 
   * @param name - 命令名称
   * @returns 如果支持该命令返回 true
   */
  supportsCommand(name: EditorCommandType | string): boolean {
    switch (name) {
      case 'undo':
      case 'redo':
      case 'toggleBold':
      case 'toggleItalic':
      case 'toggleCode':
      case 'toggleStrike':
      case 'toggleHighlight':
      case 'toggleBlockquote':
      case 'toggleHeading':
      case 'cycleHeading':
      case 'toggleOrderedList':
      case 'toggleUnorderedList':
      case 'toggleCheckList':
      case 'insertCodeBlock':
      case 'insertHorizontalRule':
      case 'insertTable':
      case 'insertLink':
      case 'insertImage':
      case 'selectAll':
      case 'duplicateLine':
      case 'deleteLine':
      case 'indentMore':
      case 'indentLess':
      case 'insertLineAfter':
      case 'sortSelectedLines':
      case 'jumpToHash':
      case 'findNext':
      case 'findPrevious':
      case 'focus':
      case 'blur':
      case 'scrollSelectionIntoView':
        return true;
      default:
        return false;
    }
  }

  /**
   * 执行指定的编辑命令
   * 
   * 这是一个统一的命令分发器，将字符串形式的命令名映射到具体的操作函数。
   * 这种设计让宿主应用可以通过统一的接口调用各种编辑功能，
   * 而不需要了解底层的 CodeMirror API。
   * 
   * @param name - 命令名称
   * @param args - 命令参数（可变参数）
   * @returns 命令执行结果
   */
  execCommand(name: EditorCommandType | string, ...args: unknown[]): unknown {
    // Plugin 命令优先：若 plugin host 命中（包含 `when` 否决的情况），直接返回。
    // 未命中再 fallback 到内置命令；内置命令最终的 `default` 返回 `undefined`，
    // 因此「禁用 plugin 时无对应内置命令」表现为优雅 no-op。
    if (this.options.pluginHost.execPluginCommand(this.view, name, ...args)) {
      return undefined;
    }
    switch (name) {
      /** 撤销操作 */
      case 'undo':
        return undo(this.view);
      /** 重做操作 */
      case 'redo':
        return redo(this.view);
      /** 切换加粗格式 */
      case 'toggleBold':
        return toggleBold(this.view);
      /** 切换斜体格式 */
      case 'toggleItalic':
        return toggleItalic(this.view);
      /** 切换行内代码格式 */
      case 'toggleCode':
        return toggleCode(this.view);
      /** 切换删除线格式 */
      case 'toggleStrike':
        return toggleStrike(this.view);
      /** 切换高亮格式 */
      case 'toggleHighlight':
        return toggleHighlight(this.view);
      /** 切换引用块格式 */
      case 'toggleBlockquote':
        return toggleBlockquote(this.view);
      /** 切换标题格式，默认为 H2 */
      case 'toggleHeading':
        return toggleHeading(this.view, typeof args[0] === 'number' ? args[0] : 2);
      /** 循环切换标题级别（H1 -> H2 -> H3 -> ... -> 普通文本 -> H1） */
      case 'cycleHeading':
        return cycleHeading(this.view);
      /** 切换有序列表 */
      case 'toggleOrderedList':
        return toggleList(this.view, 'ordered');
      /** 切换无序列表 */
      case 'toggleUnorderedList':
        return toggleList(this.view, 'unordered');
      /** 切换任务列表（复选框） */
      case 'toggleCheckList':
        return toggleList(this.view, 'check');
      /** 插入代码块 */
      case 'insertCodeBlock':
        return insertCodeBlock(this.view);
      /** 插入水平分割线 */
      case 'insertHorizontalRule':
        return insertHorizontalRule(this.view);
      /** 插入表格 */
      case 'insertTable':
        return insertTable(this.view);
      /** 插入链接，可传入 URL 和文本 */
      case 'insertLink':
        return insertLink(
          this.view,
          typeof args[0] === 'string' ? args[0] : undefined,
          typeof args[1] === 'string' ? args[1] : undefined,
        );
      /** 插入图片，需要传入 URL 和可选的 alt 文本 */
      case 'insertImage': {
        const url = typeof args[0] === 'string' ? args[0] : '';
        const alt = typeof args[1] === 'string' ? args[1] : '';
        return url ? insertImage(this.view, url, alt) : undefined;
      }
      /** 全选 */
      case 'selectAll':
        return selectAll(this.view);
      /** 复制当前行到下一行 */
      case 'duplicateLine':
        return copyLineDown(this.view);
      /** 删除当前行 */
      case 'deleteLine':
        return deleteLine(this.view);
      /** 增加缩进 */
      case 'indentMore':
        return indentMore(this.view);
      /** 减少缩进 */
      case 'indentLess':
        return indentLess(this.view);
      /** 在当前行后插入新行 */
      case 'insertLineAfter':
        return insertLineAfter(this.view);
      /** 对选中的行进行排序 */
      case 'sortSelectedLines':
        return sortSelectedLines(this.view);
      /** 跳转到指定的哈希锚点 */
      case 'jumpToHash':
        return jumpToHash(this.view, typeof args[0] === 'string' ? args[0] : '');
      /** 查找下一个匹配项 */
      case 'findNext':
        return findNext(this.view);
      /** 查找上一个匹配项 */
      case 'findPrevious':
        return findPrevious(this.view);
      /** 获取焦点 */
      case 'focus':
        return this.focus();
      /** 失去焦点 */
      case 'blur':
        return this.blur();
      /** 滚动使选区可见 */
      case 'scrollSelectionIntoView':
        return this.view.dispatch({ scrollIntoView: true });
      default:
        return undefined;
    }
  }

  /**
   * 获取编辑器中的全部文本内容
   * 
   * @returns 完整的文档字符串
   */
  getText(): string {
    return this.view.state.doc.toString();
  }

  /**
   * 替换整个文档内容
   * 
   * @param text - 新的文档内容
   */
  setText(text: string): void {
    this.view.dispatch({
      changes: { from: 0, to: this.view.state.doc.length, insert: text },
    });
  }

  /**
   * 在当前位置插入文本（替换选区或插入光标处）
   * 
   * @param text - 要插入的文本
   */
  insertText(text: string): void {
    this.replaceSelection(text);
  }

  /**
   * 替换当前选区的文本内容
   * 
   * 如果有选区，则替换选区内容；如果没有选区，则在光标位置插入。
   * 插入后将光标移动到插入文本的末尾。
   * 
   * @param text - 要插入的文本
   */
  replaceSelection(text: string): void {
    const selection = this.view.state.selection.main;
    this.view.dispatch({
      changes: { from: selection.from, to: selection.to, insert: text },
      selection: EditorSelection.cursor(selection.from + text.length),
    });
  }

  /**
   * 获取当前选区的位置信息
   * 
   * @returns 包含 anchor 和 head 的选区对象
   */
  getSelection() {
    const selection = this.view.state.selection.main;
    return createSelectionRange(selection.anchor, selection.head);
  }

  /**
   * 设置选区位置
   * 
   * @param anchor - 选区起始位置
   * @param head - 选区结束位置（可选，默认等于 anchor）
   */
  select(anchor: number, head?: number): void {
    const resolvedHead = head ?? anchor;
    this.view.dispatch({
      selection: EditorSelection.single(anchor, resolvedHead),
    });
    // 设置选区后自动获取焦点
    this.view.focus();
  }

  /**
   * 获取当前编辑器设置
   * 
   * @returns 完整的编辑器设置对象
   */
  getSettings(): EditorSettings {
    return getEditorSettings(this.view.state, this.options.settingsRuntime);
  }

  /**
   * 更新编辑器设置
   * 
   * 通过 CodeMirror 的 Effect 机制动态更新设置，无需重建编辑器。
   * 可以更新主题、功能开关等配置。
   * 
   * @param settings - 要更新的设置（部分更新）
   */
  updateSettings(settings: EditorSettingsUpdate): void {
    this.view.dispatch({
      effects: getEditorSettingsEffects(
        this.view.state,
        this.options.settingsRuntime,
        settings,
      ),
    });
  }

  /**
   * 获取当前搜索状态
   * 
   * @returns 搜索状态对象，如果没有激活搜索则返回 null
   */
  getSearchState(): SearchState | null {
    return getSearchState(this.view.state);
  }

  /**
   * 设置搜索状态
   * 
   * 可以激活搜索面板、设置搜索关键词等。
   * 
   * @param state - 搜索状态对象，传 null 关闭搜索
   * @param source - 触发来源标识（用于区分不同的触发源）
   */
  setSearchState(state: SearchState | null, source?: string): void {
    setSearchState(this.view, state, source);
  }

  /**
   * 清除搜索状态
   * 
   * @param source - 触发来源标识
   */
  clearSearch(source?: string): void {
    clearSearch(this.view, source);
  }

  /**
   * 获取当前选区的格式状态
   * 
   * 用于工具栏按钮的状态显示（例如：当前是否在加粗文本中）。
   * 
   * @returns 选区格式状态对象
   */
  getSelectionFormatting() {
    return computeSelectionFormatting(this.view.state);
  }

  /**
   * 设置编辑器底部的滚动边距
   * 
   * 这个方法同时调整两个地方：
   * 1. CodeMirror 的 scrollMargins - 控制滚动边界
   * 2. 内容的 padding-bottom - 控制视觉上的底部留白
   * 
   * 使用 Compartment.reconfigure 实现原子性更新，避免闪烁。
   * 
   * @param px - 底部边距的像素值
   */
  setScrollBottomMargin(px: number): void {
    // 如果值没有变化，跳过更新以提升性能
    if (px === this.lastScrollBottomMargin) return;
    this.lastScrollBottomMargin = px;
    this.view.dispatch({
      effects: [
        // 重新配置滚动边距
        this.options.scrollMarginsCompartment.reconfigure(
          EditorView.scrollMargins.of(() => ({ bottom: px })),
        ),
        // 重新配置内容内边距
        this.options.contentPaddingCompartment.reconfigure(
          EditorView.contentAttributes.of({ style: `padding-bottom: ${px}px` }),
        ),
      ],
    });
  }

  /**
   * 使编辑器获取焦点
   */
  focus(): void {
    this.view.focus();
  }

  /**
   * 使编辑器失去焦点
   */
  blur(): void {
    this.view.contentDOM.blur();
  }

  /**
   * 销毁编辑器实例
   * 
   * 清理资源，移除 DOM，触发 onDestroy 回调。
   * 在 React/Vue 等框架的组件卸载时调用。
   */
  destroy(): void {
    // 顺序：先派发 onDestroy（host 可能仍要读 view），再 dispose plugin 链，
    // 最后销毁 CM view（释放 DOM）。
    this.options.onDestroy?.();
    this.options.pluginHost.destroy();
    this.view.destroy();
  }
}
