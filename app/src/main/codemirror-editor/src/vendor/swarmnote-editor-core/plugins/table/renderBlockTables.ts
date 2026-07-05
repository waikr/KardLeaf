/**
 * GFM Markdown 表格实时预览渲染扩展 — Obsidian 风格
 *
 * **功能：**
 * 将 GFM Markdown 表格渲染为可交互的可视化表格，支持两种显示模式：
 * - **Widget 模式（默认）**：使用 `EditableTableWidget`，单元格可编辑
 * - **源码模式**：显示原始 Markdown 文本，顶部有“Table”切换 widget
 * 
 * **UI 设计：**
 * - 默认纯网格布局
 * - 悬停/聚焦时显示两个控制手柄：
 *   1. 每行左侧的 `:::` 手柄（点击切换行高亮）
 *   2. 表头上方的每列 `:::` 手柄（点击切换列高亮）
 * 
 * **上下文菜单：**
 * 所有结构性编辑（添加/删除行列、对齐方式、源码切换、复制、删除）
 * 都通过单元格右键菜单实现。右键点击任意单元格会触发
 * `EditorTableContextMenu` 事件，携带单元格坐标和操作集合。
 * 宿主 React 层（`NoteEditor`）在点击位置渲染 shadcn `DropdownMenu`；
 * 此模块本身不渲染菜单 DOM。
 * 
 * **单元格编辑：**
 * 使用 on-blur / on-Enter 提交（而非 onInput），以保持 IME 输入不间断
 * 并每次编辑产生单个 Yjs delta。
 * 
 * **主题颜色：**
 * 来自 `createTheme.ts` 中定义的 CSS 变量 `--cm-table-*`；
 * 此模块从不硬编码 rgba 颜色。
 */
import { syntaxTree } from '@codemirror/language';
import {
  type EditorState,
  type Range,
  StateEffect,
  StateField,
} from '@codemirror/state';
import { Decoration, type DecorationSet, EditorView, WidgetType } from '@codemirror/view';
import { editorEventCallback, EditorEventType, type TableContextMenuActions } from '../../events';
import { renderInlineMarkdown } from '../../utils/renderInlineMarkdown';

// KaTeX 在表格单元格首次包含内联数学公式时懒加载；
// CSS 已由 `renderBlockMath.ts` 全局导入。
let katexModule: typeof import('katex') | null = null;

/**
 * 异步加载 KaTeX 模块
 * 
 * @returns KaTeX 模块实例
 */
function loadKaTeX(): Promise<typeof import('katex').default> {
  // 如果已缓存，直接返回
  if (katexModule) {
    const mod = katexModule as { default?: typeof import('katex').default };
    return Promise.resolve(mod.default ?? (katexModule as unknown as typeof import('katex').default));
  }
  // 首次调用时动态导入
  return import('katex').then((m) => {
    katexModule = m;
    const mod = m as { default?: typeof import('katex').default };
    return mod.default ?? (m as unknown as typeof import('katex').default);
  });
}

/**
 * 水合数学公式 span 元素
 * 
 * **功能：**
 * 将 `renderInlineMarkdown` 生成的占位符
 * `<span class="cm-table-math" data-tex="...">$x$</span>`
 * 替换为 KaTeX 渲染的内联公式。失败时回退到字面源文本。
 * 
 * @param root - 根元素（表格容器）
 */
function hydrateMathSpans(root: HTMLElement) {
  const spans = root.querySelectorAll<HTMLElement>('.cm-table-math[data-tex]');
  if (spans.length === 0) return;
  void loadKaTeX().then((katex) => {
    spans.forEach((span) => {
      if (!span.isConnected) return;
      const tex = span.dataset.tex ?? '';
      try {
        katex.render(tex, span, { displayMode: false, throwOnError: false });
        span.removeAttribute('data-tex');
      } catch {
        span.textContent = `$${tex}$`;
      }
    });
  });
}

// ─── 类型定义 ──────────────────────────────────────────────────────

/** 单元格对齐方式 */
type Alignment = 'left' | 'center' | 'right' | null;

/** 表格数据接口 */
interface TableData {
  /** 表头单元格数组 */
  headers: string[];
  /** 每列的对齐方式 */
  alignments: Alignment[];
  /** 数据行数组（每行是一个单元格数组） */
  rows: string[][];
}

/** 表格源码范围 */
interface TableSourceRange {
  /** 起始位置 */
  from: number;
  /** 结束位置 */
  to: number;
}

// ─── Markdown 解析 ───────────────────────────────────────────────

/**
 * 解析表格行
 * 
 * **工作原理：**
 * 1. 将转义的管道符 `\|` 替换为占位符
 * 2. 按 `|` 分割单元格
 * 3. 移除首尾空单元格
 * 4. 恢复转义的管道符并修剪空白
 * 
 * @param line - 原始行文本
 * @returns 单元格数组
 */
function parseRow(line: string): string[] {
  // 使用占位符临时替换转义的管道符
  const placeholder = '\x00PIPE\x00';
  const escaped = line.replace(/\\\|/g, placeholder);
  // 按管道符分割
  const cells = escaped.split('|');

  // 移除首尾空单元格
  if (cells.length > 0 && cells[0].trim() === '') cells.shift();
  if (cells.length > 0 && cells[cells.length - 1].trim() === '') cells.pop();

  // 恢复转义的管道符并修剪空白
  return cells.map((cell) => cell.replace(new RegExp(placeholder, 'g'), '|').trim());
}

/**
 * 解析对齐方式
 * 
 * **规则：**
 * - `:---:` → center
 * - `---:` → right
 * - `:---` → left
 * - `---` → null（默认）
 * 
 * @param cell - 分隔符单元格文本
 * @returns 对齐方式
 */
function parseAlignment(cell: string): Alignment {
  const trimmed = cell.trim();
  const left = trimmed.startsWith(':');
  const right = trimmed.endsWith(':');
  if (left && right) return 'center';
  if (right) return 'right';
  if (left) return 'left';
  return null;
}

/**
 * 检查是否为分隔符行
 * 
 * @param cells - 单元格数组
 * @returns 是否为有效的分隔符行
 */
function isSeparatorRow(cells: string[]): boolean {
  return cells.length > 0 && cells.every((c) => /^:?-+:?$/.test(c.trim()));
}

/**
 * 解析 Markdown 表格
 * 
 * **工作流程：**
 * 1. 分割行并过滤空行
 * 2. 解析表头行
 * 3. 验证分隔符行
 * 4. 提取对齐方式
 * 5. 解析数据行（补齐缺失单元格）
 * 
 * @param source - 表格 Markdown 源码
 * @returns 表格数据或 null（解析失败）
 */
function parseMarkdownTable(source: string): TableData | null {
  const lines = source.split('\n').filter((l) => l.trim() !== '');
  if (lines.length < 2) return null;

  const headerCells = parseRow(lines[0]);
  if (headerCells.length === 0) return null;

  const separatorCells = parseRow(lines[1]);
  if (!isSeparatorRow(separatorCells)) return null;
  if (headerCells.length !== separatorCells.length) return null;

  const alignments = separatorCells.map(parseAlignment);

  const rows: string[][] = [];
  for (let i = 2; i < lines.length; i++) {
    const rowCells = parseRow(lines[i]);
    rows.push(headerCells.map((_, idx) => rowCells[idx] ?? ''));
  }

  return { headers: headerCells, alignments, rows };
}

// ─── Markdown 生成 ────────────────────────────────────────

/**
 * 生成分隔符单元格
 * 
 * @param alignment - 对齐方式
 * @returns 分隔符字符串（如 `:---`、`---:`、`:---:`）
 */
function generateSeparator(alignment: Alignment): string {
  switch (alignment) {
    case 'left':
      return ':---';
    case 'right':
      return '---:';
    case 'center':
      return ':---:';
    default:
      return '---';
  }
}

/**
 * 序列化表格数据为 Markdown 字符串
 * 
 * @param data - 表格数据
 * @returns Markdown 格式的表格字符串
 */
function serializeMarkdownTable(data: TableData): string {
  const headerLine = `| ${data.headers.join(' | ')} |`;
  const sepLine = `| ${data.alignments.map(generateSeparator).join(' | ')} |`;
  const dataLines = data.rows.map((row) => `| ${row.join(' | ')} |`);
  return [headerLine, sepLine, ...dataLines].join('\n');
}

/**
 * 深拷贝表格数据
 * 
 * @param data - 原始表格数据
 * @returns 克隆的表格数据
 */
function cloneTableData(data: TableData): TableData {
  return {
    headers: [...data.headers],
    alignments: [...data.alignments],
    rows: data.rows.map((row) => [...row]),
  };
}

// ─── 源码模式跟踪 ───────────────────────────────────────

/**
 * 设置表格源码模式的 StateEffect
 * 
 * **用途：**
 * 用于在 widget 模式和源码模式之间切换。
 */
export const setTableSourceMode = StateEffect.define<{
  from: number;
  to: number;
  showSource: boolean;
}>();

/**
 * 检查两个范围是否重叠
 * 
 * @param a - 范围 A
 * @param b - 范围 B
 * @returns 是否重叠
 */
function rangesOverlap(a: TableSourceRange, b: TableSourceRange): boolean {
  return a.from <= b.to && a.to >= b.from;
}

/**
 * 表格源码模式状态字段
 * 
 * **功能：**
 * 持久化存储所有处于源码模式的表格范围。
 * 
 * **更新逻辑：**
 * 1. 映射现有范围的位置（适应文档变化）
 * 2. 处理 setTableSourceMode 效果：
 *    - showSource=true：添加新范围（如果不存在）
 *    - showSource=false：移除匹配的范围
 */
const tableSourceModeField = StateField.define<TableSourceRange[]>({
  /** 初始化时返回空数组 */
  create: () => [],
  /**
   * 更新方法
   * 
   * @param ranges - 当前范围数组
   * @param tr - 事务对象
   * @returns 更新后的范围数组
   */
  update(ranges, tr) {
    // 映射现有范围的位置（适应文档变化）
    let next = ranges.map((range) => ({
      from: tr.changes.mapPos(range.from, 1),
      to: tr.changes.mapPos(range.to, -1),
    }));

    // 处理 setTableSourceMode 效果
    for (const effect of tr.effects) {
      if (!effect.is(setTableSourceMode)) continue;
      const { from, to, showSource } = effect.value;
      const mapped: TableSourceRange = {
        from: tr.changes.mapPos(from, 1),
        to: tr.changes.mapPos(to, -1),
      };
      if (showSource) {
        // 添加新范围（如果不存在）
        if (!next.some((r) => rangesOverlap(r, mapped))) {
          next = [...next, mapped];
        }
      } else {
        // 移除匹配的范围
        next = next.filter((r) => !rangesOverlap(r, mapped));
      }
    }

    return next;
  },
});

/**
 * 检查表格是否处于源码模式
 * 
 * @param ranges - 源码模式范围数组
 * @param from - 表格起始位置
 * @param to - 表格结束位置
 * @returns 是否处于源码模式
 */
function isTableInSourceMode(ranges: TableSourceRange[], from: number, to: number): boolean {
  return ranges.some((r) => r.from <= to && r.to >= from);
}

// ─── Lucide 图标（内联 SVG）─────────────────────────────────────

/** 图标键名类型 */
type IconKey = 'grip-horizontal' | 'grip-vertical' | 'table';

/** SVG 开头标记 */
const SVG_PROLOGUE =
  '<svg xmlns="http://www.w3.org/2000/svg" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">';

/**
 * Lucide 图标 SVG 映射表
 * 
 * **包含的图标：**
 * - grip-horizontal：水平手柄（用于列控制）
 * - grip-vertical：垂直手柄（用于行控制）
 * - table：表格图标（用于源码模式切换按钮）
 */
const LUCIDE_ICONS: Record<IconKey, string> = {
  'grip-horizontal':
    `${SVG_PROLOGUE}<circle cx="12" cy="9" r="1"/><circle cx="19" cy="9" r="1"/><circle cx="5" cy="9" r="1"/><circle cx="12" cy="15" r="1"/><circle cx="19" cy="15" r="1"/><circle cx="5" cy="15" r="1"/></svg>`,
  'grip-vertical':
    `${SVG_PROLOGUE}<circle cx="9" cy="12" r="1"/><circle cx="9" cy="5" r="1"/><circle cx="9" cy="19" r="1"/><circle cx="15" cy="12" r="1"/><circle cx="15" cy="5" r="1"/><circle cx="15" cy="19" r="1"/></svg>`,
  table: `${SVG_PROLOGUE}<path d="M12 3v18"/><rect width="18" height="18" x="3" y="3" rx="2"/><path d="M3 9h18"/><path d="M3 15h18"/></svg>`,
};

/**
 * 创建图标按钮
 * 
 * @param iconKey - 图标键名
 * @param title - 提示文本
 * @param onClick - 点击回调
 * @param className - CSS 类名
 * @returns button 元素
 */
function iconButton(
  iconKey: IconKey,
  title: string,
  onClick: () => void,
  className: string,
): HTMLButtonElement {
  // 创建按钮元素
  const btn = document.createElement('button');
  btn.type = 'button';
  btn.className = className;
  btn.title = title;
  btn.setAttribute('aria-label', title);
  btn.innerHTML = LUCIDE_ICONS[iconKey];
  // 监听鼠标按下事件（阻止默认行为和传播）
  btn.addEventListener('mousedown', (e) => {
    e.preventDefault();
    e.stopPropagation();
    onClick();
  });
  return btn;
}

// ─── 跨 widget 重建的待处理焦点 ───────────────────────────────

/**
 * 待处理的表格焦点映射表
 * 
 * **用途：**
 * 在 widget 重建期间保留焦点位置，确保编辑体验流畅。
 * 
 * **键：** 表格起始位置
 * **值：** { row: 行索引, col: 列索引 }
 */
const pendingTableFocus = new Map<number, { row: number; col: number }>();

// ─── 可编辑表格 Widget ──────────────────────────────────────

/**
 * 可编辑表格 Widget 类
 * 
 * **功能：**
 * 渲染可交互的 Markdown 表格，支持单元格编辑、行列选择、上下文菜单等。
 * 
 * @param data - 表格数据
 * @param tableFrom - 表格起始位置
 * @param tableTo - 表格结束位置
 */
class EditableTableWidget extends WidgetType {
  constructor(
    /** 表格数据 */
    private readonly data: TableData,
    /** 表格起始位置 */
    private readonly tableFrom: number,
    /** 表格结束位置 */
    private readonly tableTo: number,
  ) {
    super();
  }

  /**
   * 相等性判断
   * 
   * **比较内容：**
   * 1. 表格范围（tableFrom, tableTo）
   * 2. 表头数量和名称
   * 3. 行数和每行的单元格数量
   * 4. 对齐方式
   * 5. 所有单元格的内容
   * 
   * @param other - 另一个 widget 实例
   * @returns 是否相等
   */
  eq(other: EditableTableWidget): boolean {
    if (this.tableFrom !== other.tableFrom || this.tableTo !== other.tableTo) return false;
    if (this.data.headers.length !== other.data.headers.length) return false;
    if (this.data.rows.length !== other.data.rows.length) return false;
    for (let i = 0; i < this.data.headers.length; i++) {
      if (this.data.headers[i] !== other.data.headers[i]) return false;
      if (this.data.alignments[i] !== other.data.alignments[i]) return false;
    }
    for (let i = 0; i < this.data.rows.length; i++) {
      for (let j = 0; j < this.data.headers.length; j++) {
        if (this.data.rows[i][j] !== other.data.rows[i][j]) return false;
      }
    }
    return true;
  }

  toDOM(view: EditorView): HTMLElement {
    // 创建容器元素
    const container = document.createElement('div');
    container.className = 'cm-table-widget';
    container.dataset.tableFrom = String(this.tableFrom);

    // 构建并附加表格
    container.appendChild(this.buildTable(view));

    // 检查是否有待处理的焦点（widget 重建期间保留）
    const pending = pendingTableFocus.get(this.tableFrom);
    if (pending) {
      pendingTableFocus.delete(this.tableFrom);
      requestAnimationFrame(() => {
        // 找到目标行（-1 表示表头）
        const targetRow =
          pending.row === -1
            ? container.querySelector<HTMLTableRowElement>('thead tr')
            : container.querySelectorAll<HTMLTableRowElement>('tbody tr')[pending.row];
        if (!targetRow) return;
        // 找到目标单元格
        const cells = targetRow.querySelectorAll<HTMLElement>(
          'th[contenteditable], td[contenteditable]',
        );
        const target = cells[pending.col];
        if (target) focusCellEnd(target);  // 聚焦到单元格末尾
      });
    }

    return container;
  }

  /**
   * 事件处理策略
   * 
   * **返回值：**
   * true — Widget 单元格是 contentEditable 并管理自己的 DOM 事件
   * （焦点 / 光标 / 输入）。返回 true 告诉 CodeMirror 不要移动
   * 其自身的选区来响应源自 widget 内部的事件 —— 否则点击单元格
   * 会将编辑器光标推到 widget 边界而不是聚焦单元格。
   * 
   * @returns true
   */
  ignoreEvent(): boolean {
    return true;
  }

  // ── 表格主体 ──

  /**
   * 构建表格 DOM 结构
   * 
   * **DOM 层级：**
   * ```
   * table
   *   thead
   *     tr
   *       th (表头单元格 + 列手柄)
   *   tbody
   *     tr (每行)
   *       td (数据单元格 + 行手柄（仅第一列）)
   * ```
   * 
   * @param view - 编辑器视图
   * @returns table 元素
   */
  private buildTable(view: EditorView): HTMLElement {
    // 创建 table 元素
    const table = document.createElement('table');

    // 构建表头
    const thead = document.createElement('thead');
    const headerRow = document.createElement('tr');
    this.data.headers.forEach((header, colIdx) => {
      // 创建表头单元格并附加列手柄
      const th = this.createCell(view, 'th', header, colIdx, (next) =>
        this.commitHeader(view, colIdx, next),
      );
      th.appendChild(this.buildColumnHandle(colIdx));
      headerRow.appendChild(th);
    });
    thead.appendChild(headerRow);
    table.appendChild(thead);

    // 构建表体
    const tbody = document.createElement('tbody');
    this.data.rows.forEach((row, rowIdx) => {
      const tr = document.createElement('tr');
      tr.dataset.rowIdx = String(rowIdx);
      row.forEach((cell, colIdx) => {
        // 创建数据单元格
        const td = this.createCell(view, 'td', cell, colIdx, (next) =>
          this.commitCell(view, rowIdx, colIdx, next),
        );
        // 在第一列附加行手柄
        if (colIdx === 0) {
          td.appendChild(this.buildRowHandle(rowIdx));
        }
        tr.appendChild(td);
      });
      tbody.appendChild(tr);
    });
    table.appendChild(tbody);

    return table;
  }

  /**
   * 构建行手柄按钮
   * 
   * @param rowIdx - 行索引
   * @returns 图标按钮元素
   */
  private buildRowHandle(rowIdx: number): HTMLElement {
    // 创建垂直手柄按钮，点击切换行选择
    return iconButton(
      'grip-vertical',
      'Click to select row',
      () => this.toggleRowSelection(rowIdx),
      'cm-table-row-handle',
    );
  }

  /**
   * 构建列手柄按钮
   * 
   * @param colIdx - 列索引
   * @returns 图标按钮元素
   */
  private buildColumnHandle(colIdx: number): HTMLElement {
    // 创建水平手柄按钮，点击切换列选择
    return iconButton(
      'grip-horizontal',
      'Click to select column',
      () => this.toggleColumnSelection(colIdx),
      'cm-table-col-handle',
    );
  }

  // ── 选择辅助函数 ──

  /**
   * 切换行选择状态
   * 
   * **工作流程：**
   * 1. 查找表格 widget 元素
   * 2. 找到目标行
   * 3. 如果已选中则取消，否则清除其他选择并选中该行
   * 
   * @param rowIdx - 行索引
   */
  private toggleRowSelection(rowIdx: number) {
    const widgetEl = document.querySelector<HTMLElement>(
      `.cm-table-widget[data-table-from="${this.tableFrom}"]`,
    );
    if (!widgetEl) return;
    const rows = widgetEl.querySelectorAll<HTMLTableRowElement>('tbody tr');
    const target = rows[rowIdx];
    if (!target) return;
    const wasSelected = widgetEl.dataset.selectedRow === String(rowIdx);
    clearTableSelection(widgetEl);
    if (!wasSelected) {
      target.classList.add('cm-table-row-selected');
      widgetEl.dataset.selectedRow = String(rowIdx);
    }
  }

  private toggleColumnSelection(colIdx: number) {
    const widgetEl = document.querySelector<HTMLElement>(
      `.cm-table-widget[data-table-from="${this.tableFrom}"]`,
    );
    if (!widgetEl) return;
    const wasSelected = widgetEl.dataset.selectedCol === String(colIdx);
    clearTableSelection(widgetEl);
    if (!wasSelected) {
      const headerTh = widgetEl.querySelectorAll<HTMLElement>('thead th')[colIdx];
      headerTh?.classList.add('cm-table-col-selected');
      widgetEl.querySelectorAll<HTMLTableRowElement>('tbody tr').forEach((tr) => {
        const td = tr.children[colIdx] as HTMLElement | undefined;
        td?.classList.add('cm-table-col-selected');
      });
      widgetEl.dataset.selectedCol = String(colIdx);
    }
  }

  // ── Cell ──

  private createCell(
    view: EditorView,
    tag: 'th' | 'td',
    value: string,
    colIdx: number,
    onCommit: (nextValue: string) => void,
  ): HTMLTableCellElement {
    const cell = document.createElement(tag);
    cell.className = 'cm-table-cell';
    cell.contentEditable = 'true';
    cell.spellcheck = false;
    cell.dataset.raw = value;
    cell.dataset.colIdx = String(colIdx);
    cell.innerHTML = renderInlineMarkdown(value);
    hydrateMathSpans(cell);

    const align = this.data.alignments[colIdx];
    if (align) cell.style.textAlign = align;

    // Stop mousedown from bubbling so CodeMirror doesn't try to place its
    // caret at the widget boundary; let the contentEditable element handle
    // focus + native caret placement on its own.
    cell.addEventListener('mousedown', (e) => {
      e.stopPropagation();
    });

    let editing = false;

    const commitIfChanged = () => {
      const nextValue = cell.textContent ?? '';
      const raw = cell.dataset.raw ?? '';
      if (nextValue === raw) return;
      cell.dataset.raw = nextValue;
      onCommit(nextValue);
    };

    cell.addEventListener('focus', () => {
      if (!editing) {
        editing = true;
        cell.textContent = cell.dataset.raw ?? '';
      }
    });

    cell.addEventListener('blur', (e) => {
      e.stopPropagation();
      commitIfChanged();
      editing = false;
      cell.innerHTML = renderInlineMarkdown(cell.dataset.raw ?? '');
      hydrateMathSpans(cell);
    });

    cell.addEventListener('keydown', (e) => {
      if (e.key === 'Enter') {
        e.preventDefault();
        commitIfChanged();
        cell.blur();
      } else if (e.key === 'Tab') {
        e.preventDefault();
        e.stopPropagation();
        this.navigateCell(view, cell, e.shiftKey ? -1 : 1);
      } else if (e.key === 'Escape') {
        e.preventDefault();
        e.stopPropagation();
        cell.blur();
      }
    });

    cell.addEventListener('contextmenu', (e) => {
      e.preventDefault();
      e.stopPropagation();
      const rowIdx = tag === 'th' ? -1 : Number(cell.parentElement?.dataset.rowIdx ?? -1);
      const callback = view.state.facet(editorEventCallback);
      callback?.({
        kind: EditorEventType.TableContextMenu,
        clientX: e.clientX,
        clientY: e.clientY,
        rowIdx,
        colIdx,
        alignment: this.data.alignments[colIdx],
        rowCount: this.data.rows.length,
        colCount: this.data.headers.length,
        actions: this.buildContextMenuActions(view),
      });
    });

    return cell;
  }

  private navigateCell(view: EditorView, currentCell: HTMLElement, direction: 1 | -1) {
    const container = currentCell.closest('.cm-table-widget');
    if (!container) return;

    const cells = Array.from(
      container.querySelectorAll<HTMLElement>('th[contenteditable], td[contenteditable]'),
    );
    const idx = cells.indexOf(currentCell);
    if (idx === -1) return;

    if (direction === 1 && idx === cells.length - 1) {
      pendingTableFocus.set(this.tableFrom, {
        row: this.data.rows.length,
        col: 0,
      });
      this.addRowAt(view, this.data.rows.length, 'below');
      return;
    }

    if (direction === -1 && idx === 0) {
      view.focus();
      view.dispatch({ selection: { anchor: this.tableFrom } });
      return;
    }

    const nextIdx = idx + direction;
    if (nextIdx < 0 || nextIdx >= cells.length) return;
    focusCellEnd(cells[nextIdx]);
  }

  // ── Context menu actions exposed to the host React layer ──

  private buildContextMenuActions(view: EditorView): TableContextMenuActions {
    return {
      addRowAt: (rowIdx, position) => this.addRowAt(view, rowIdx, position),
      deleteRow: (rowIdx) => this.deleteRow(view, rowIdx),
      addColumnAt: (colIdx, position) => this.addColumnAt(view, colIdx, position),
      deleteColumn: (colIdx) => this.deleteColumn(view, colIdx),
      setAlignment: (colIdx, alignment) => this.setAlignment(view, colIdx, alignment),
      toggleSource: () => this.toggleSource(view),
      copyMarkdown: () => this.copyMarkdown(),
      deleteTable: () => this.deleteTable(view),
    };
  }

  // ── Commit handlers ──

  private commitHeader(view: EditorView, colIdx: number, nextValue: string) {
    const updated = cloneTableData(this.data);
    updated.headers[colIdx] = nextValue;
    this.dispatchTableChange(view, updated);
  }

  private commitCell(view: EditorView, rowIdx: number, colIdx: number, nextValue: string) {
    const updated = cloneTableData(this.data);
    updated.rows[rowIdx][colIdx] = nextValue;
    this.dispatchTableChange(view, updated);
  }

  private dispatchTableChange(view: EditorView, newData: TableData) {
    const markdown = serializeMarkdownTable(newData);
    view.dispatch({
      changes: { from: this.tableFrom, to: this.tableTo, insert: markdown },
    });
  }

  // ── Structural operations ──

  private addRowAt(view: EditorView, rowIdx: number, position: 'above' | 'below') {
    const updated = cloneTableData(this.data);
    const insertAt = position === 'above' ? rowIdx : rowIdx + 1;
    updated.rows.splice(insertAt, 0, this.data.headers.map(() => ''));
    this.dispatchTableChange(view, updated);
  }

  private deleteRow(view: EditorView, rowIdx: number) {
    if (rowIdx < 0 || rowIdx >= this.data.rows.length) return;
    const updated = cloneTableData(this.data);
    updated.rows.splice(rowIdx, 1);
    this.dispatchTableChange(view, updated);
  }

  private addColumnAt(view: EditorView, colIdx: number, position: 'left' | 'right') {
    const updated = cloneTableData(this.data);
    const insertAt = position === 'left' ? colIdx : colIdx + 1;
    updated.headers.splice(insertAt, 0, '');
    updated.alignments.splice(insertAt, 0, null);
    updated.rows.forEach((row) => row.splice(insertAt, 0, ''));
    this.dispatchTableChange(view, updated);
  }

  private deleteColumn(view: EditorView, colIdx: number) {
    if (this.data.headers.length <= 1) return;
    if (colIdx < 0 || colIdx >= this.data.headers.length) return;
    const updated = cloneTableData(this.data);
    updated.headers.splice(colIdx, 1);
    updated.alignments.splice(colIdx, 1);
    updated.rows.forEach((row) => row.splice(colIdx, 1));
    this.dispatchTableChange(view, updated);
  }

  private setAlignment(view: EditorView, colIdx: number, alignment: Alignment) {
    if (this.data.alignments[colIdx] === alignment) return;
    const updated = cloneTableData(this.data);
    updated.alignments[colIdx] = alignment;
    this.dispatchTableChange(view, updated);
  }

  private toggleSource(view: EditorView) {
    view.dispatch({
      effects: setTableSourceMode.of({
        from: this.tableFrom,
        to: this.tableTo,
        showSource: true,
      }),
    });
  }

  private deleteTable(view: EditorView) {
    view.dispatch({
      changes: { from: this.tableFrom, to: this.tableTo, insert: '' },
    });
  }

  private copyMarkdown() {
    const md = serializeMarkdownTable(this.data);
    const fallback = () => {
      const ta = document.createElement('textarea');
      ta.value = md;
      ta.style.position = 'fixed';
      ta.style.opacity = '0';
      document.body.appendChild(ta);
      ta.select();
      try {
        document.execCommand('copy');
      } catch {
        // best-effort
      }
      document.body.removeChild(ta);
    };

    if (navigator.clipboard?.writeText) {
      navigator.clipboard.writeText(md).catch(fallback);
    } else {
      fallback();
    }
  }
}

// ─── Helpers ────────────────────────────────────────────────────

function focusCellEnd(target: HTMLElement) {
  target.focus();
  const range = document.createRange();
  range.selectNodeContents(target);
  const sel = window.getSelection();
  sel?.removeAllRanges();
  sel?.addRange(range);
}

function clearTableSelection(widgetEl: HTMLElement) {
  widgetEl
    .querySelectorAll<HTMLElement>('.cm-table-row-selected, .cm-table-col-selected')
    .forEach((el) => {
      el.classList.remove('cm-table-row-selected');
      el.classList.remove('cm-table-col-selected');
    });
  delete widgetEl.dataset.selectedRow;
  delete widgetEl.dataset.selectedCol;
}

// ─── Source-mode "Table" toggle widget ──────────────────────────

// Source-mode toggle widget keeps default `ignoreEvent` (false) since it has
// no contentEditable children — the small icon button handles its own click.
class TableSourceToggleWidget extends WidgetType {
  constructor(
    private readonly tableFrom: number,
    private readonly tableTo: number,
  ) {
    super();
  }

  eq(other: TableSourceToggleWidget): boolean {
    return this.tableFrom === other.tableFrom && this.tableTo === other.tableTo;
  }

  toDOM(view: EditorView): HTMLElement {
    const container = document.createElement('div');
    container.className = 'cm-table-source-toggle';

    container.appendChild(
      iconButton(
        'table',
        'Switch to table view',
        () => {
          view.dispatch({
            effects: setTableSourceMode.of({
              from: this.tableFrom,
              to: this.tableTo,
              showSource: false,
            }),
          });
        },
        'cm-table-source-toggle-btn',
      ),
    );

    return container;
  }

  ignoreEvent(): boolean {
    return false;
  }
}

// ─── StateField ─────────────────────────────────────────────────

function buildTableDecorations(state: EditorState): DecorationSet {
  const decorations: Range<Decoration>[] = [];
  const sourceRanges = state.field(tableSourceModeField);

  syntaxTree(state).iterate({
    enter: (node) => {
      if (node.name !== 'Table') return;

      const tableSource = state.doc.sliceString(node.from, node.to);
      const tableData = parseMarkdownTable(tableSource);
      if (!tableData) return;

      const showSource = isTableInSourceMode(sourceRanges, node.from, node.to);

      if (!showSource) {
        decorations.push(
          Decoration.replace({
            widget: new EditableTableWidget(tableData, node.from, node.to),
            block: true,
          }).range(node.from, node.to),
        );
        return;
      }

      decorations.push(
        Decoration.widget({
          widget: new TableSourceToggleWidget(node.from, node.to),
          block: true,
          side: -1,
        }).range(node.from),
      );
      for (let pos = node.from; pos <= node.to; ) {
        const line = state.doc.lineAt(pos);
        decorations.push(Decoration.line({ class: 'cm-table-source-mode' }).range(line.from));
        pos = line.to + 1;
      }
    },
  });

  return Decoration.set(
    decorations.sort((a, b) => a.from - b.from),
    true,
  );
}

const tableField = StateField.define<DecorationSet>({
  create(state) {
    return buildTableDecorations(state);
  },
  update(deco, tr) {
    const hasModeToggle = tr.effects.some((e) => e.is(setTableSourceMode));
    if (tr.docChanged || tr.reconfigured || hasModeToggle) {
      return buildTableDecorations(tr.state);
    }
    return deco;
  },
  provide: (f) => EditorView.decorations.from(f),
});

// ─── Theme ──────────────────────────────────────────────────────

const blockTableTheme = EditorView.theme({
  '.cm-table-widget': {
    position: 'relative',
    // padding-top reserves room for the per-column `:::` handle that floats
    // above each `<th>` (top:-18px from the th, +4px breathing room).
    padding: '24px 8px 8px 24px',
    overflowX: 'auto',
    overflowY: 'visible',
  },
  '.cm-table-widget table': {
    borderCollapse: 'collapse',
    width: '100%',
    fontSize: '0.95em',
  },
  '.cm-table-widget th, .cm-table-widget td': {
    border: '1px solid var(--cm-table-border)',
    padding: '6px 12px',
    outline: 'none',
    minWidth: '40px',
    position: 'relative',
  },
  '.cm-table-widget th': {
    fontWeight: '600',
  },
  '.cm-table-widget tbody tr': {
    position: 'relative',
  },
  // Selection ring uses an absolutely-positioned `::before` pseudo-element
  // as a backdrop layer. This sidesteps both `border-collapse: collapse`
  // (which silently drops `border-radius` on cells) and the layout shift
  // that border-width changes would cause. `inset: -1px` extends the ring
  // 1px outside the cell so it visually replaces the underlying gray
  // 1px border on the four outer edges, while internal cell dividers
  // (rendered by neighbouring cells) remain intact.

  // ── Selected row ──
  '.cm-table-widget tbody tr.cm-table-row-selected td': {
    background: 'var(--cm-table-selection-bg)',
  },
  '.cm-table-widget tbody tr.cm-table-row-selected td::before': {
    content: '""',
    position: 'absolute',
    inset: '-2px',
    pointerEvents: 'none',
    borderTop: '2px solid var(--cm-table-selection-border)',
    borderBottom: '2px solid var(--cm-table-selection-border)',
  },
  '.cm-table-widget tbody tr.cm-table-row-selected td:first-child::before': {
    borderLeft: '2px solid var(--cm-table-selection-border)',
    borderTopLeftRadius: '6px',
    borderBottomLeftRadius: '6px',
  },
  '.cm-table-widget tbody tr.cm-table-row-selected td:last-child::before': {
    borderRight: '2px solid var(--cm-table-selection-border)',
    borderTopRightRadius: '6px',
    borderBottomRightRadius: '6px',
  },

  // ── Selected column ──
  '.cm-table-widget tbody tr td.cm-table-col-selected, .cm-table-widget thead tr th.cm-table-col-selected':
    {
      background: 'var(--cm-table-selection-bg)',
    },
  '.cm-table-widget tbody tr td.cm-table-col-selected::before, .cm-table-widget thead tr th.cm-table-col-selected::before':
    {
      content: '""',
      position: 'absolute',
      inset: '-2px',
      pointerEvents: 'none',
      borderLeft: '2px solid var(--cm-table-selection-border)',
      borderRight: '2px solid var(--cm-table-selection-border)',
    },
  '.cm-table-widget thead tr th.cm-table-col-selected::before': {
    borderTop: '2px solid var(--cm-table-selection-border)',
    borderTopLeftRadius: '6px',
    borderTopRightRadius: '6px',
  },
  '.cm-table-widget tbody tr:last-child td.cm-table-col-selected::before': {
    borderBottom: '2px solid var(--cm-table-selection-border)',
    borderBottomLeftRadius: '6px',
    borderBottomRightRadius: '6px',
  },
  '.cm-table-widget code': {
    background: 'var(--cm-table-header-bg)',
    borderRadius: '3px',
    padding: '0 4px',
    fontFamily: 'monospace',
    fontSize: '0.9em',
  },
  '.cm-table-widget a': {
    color: 'var(--cm-link, currentColor)',
    textDecoration: 'underline',
  },
  '.cm-table-widget img': {
    maxWidth: '100%',
    verticalAlign: 'middle',
    display: 'inline-block',
  },
  '.cm-table-math': {
    display: 'inline-block',
    verticalAlign: 'middle',
  },

  // ── Row handle (left gutter) ──
  '.cm-table-row-handle': {
    position: 'absolute',
    left: '-22px',
    top: '50%',
    transform: 'translateY(-50%)',
    display: 'inline-flex',
    alignItems: 'center',
    justifyContent: 'center',
    width: '18px',
    height: '20px',
    border: 'none',
    background: 'transparent',
    color: 'var(--cm-table-affordance-fg)',
    cursor: 'pointer',
    padding: '0',
    borderRadius: '4px',
    opacity: '0',
    transition: 'opacity 0.15s',
    lineHeight: '0',
    zIndex: '1',
  },
  // Row handle reveals strictly on pointer hover. Selection (and cell
  // focus-within) do NOT keep it visible — the colored border ring is the
  // selection cue; the handle staying around once the cursor leaves would
  // duplicate the cue and clutter the gutter.
  '.cm-table-widget tbody tr:hover .cm-table-row-handle': {
    opacity: '1',
  },
  '.cm-table-row-handle:hover': {
    background: 'var(--cm-table-affordance-bg-hover)',
  },

  // ── Column handle (top of each th) ──
  '.cm-table-col-handle': {
    position: 'absolute',
    top: '-18px',
    left: '50%',
    transform: 'translateX(-50%)',
    display: 'inline-flex',
    alignItems: 'center',
    justifyContent: 'center',
    width: '20px',
    height: '14px',
    border: 'none',
    background: 'transparent',
    color: 'var(--cm-table-affordance-fg)',
    cursor: 'pointer',
    padding: '0',
    borderRadius: '4px',
    opacity: '0',
    transition: 'opacity 0.15s',
    lineHeight: '0',
    zIndex: '1',
  },
  '.cm-table-widget th:hover .cm-table-col-handle': {
    opacity: '1',
  },
  '.cm-table-col-handle:hover': {
    background: 'var(--cm-table-affordance-bg-hover)',
  },

  // ── Source mode ──
  '.cm-table-source-mode': {
    fontFamily: 'monospace',
    fontSize: '0.95em',
  },
  '.cm-table-source-toggle': {
    padding: '4px 0',
    display: 'flex',
    justifyContent: 'flex-start',
  },
  '.cm-table-source-toggle-btn': {
    display: 'inline-flex',
    alignItems: 'center',
    justifyContent: 'center',
    width: '24px',
    height: '24px',
    border: '1px solid var(--cm-table-border)',
    background: 'var(--cm-table-header-bg)',
    color: 'var(--cm-table-affordance-fg)',
    cursor: 'pointer',
    padding: '0',
    borderRadius: '4px',
    lineHeight: '0',
  },
  '.cm-table-source-toggle-btn:hover': {
    background: 'var(--cm-table-affordance-bg-hover)',
  },
});

// ─── Export ─────────────────────────────────────────────────────

export function createBlockTableExtension() {
  return [tableSourceModeField, blockTableTheme, tableField];
}
