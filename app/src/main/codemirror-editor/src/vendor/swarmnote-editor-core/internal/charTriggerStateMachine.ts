/**
 * Internal helper: char-trigger 状态机抽象。
 *
 * **NON-PUBLIC API** — 不通过主入口 re-export，第三方 plugin 不得依赖。
 * Shape 在 v0.x 内随实施细节调整。
 *
 * 当前使用者：
 * - `slashCommandPlugin`（v0.3 phase A）— trigger char `/`
 * - `wikilinkPlugin`（v0.3 phase B，规划）— trigger sequence `[[`
 *
 * 实现要点：
 * - ViewPlugin 监听 docChanged + selectionSet + focusChanged
 * - 检测条件：cursor 前的 N 个字符等于 `triggerChar`，且前一字符是
 *   whitespace / line start / doc start
 * - IME composition 期间不激活（防中文输入误判）
 * - `validateContext` 由 caller 提供，过滤 code block 等位置
 * - debounce 150ms 调 `computeItems`，每次 query 切换 abort 旧 signal
 * - query token 校验：async 结果晚到时如 token mismatch 直接丢弃
 */
import type { Extension, EditorState } from '@codemirror/state';
import { EditorView, ViewPlugin, type ViewUpdate } from '@codemirror/view';

const DEBOUNCE_MS = 150;

export interface CharTriggerState<TItem> {
  active: boolean;
  query: string;
  /** `range.from` 指向 trigger 序列起点（含 trigger char） */
  range: { from: number; to: number };
  items: TItem[];
  activeIndex: number;
  screenRect?: { x: number; y: number; width: number; height: number };
}

export interface CharTriggerConfig<TItem> {
  /** 触发序列（slash: '/', wikilink: '[[']） */
  triggerChar: string;
  /**
   * 判断在 `pos` 位置是否允许激活 trigger。
   * 典型实现：排除 code block / inline code / math / frontmatter 节点。
   */
  validateContext: (state: EditorState, pos: number) => boolean;
  /**
   * 计算候选项。`signal` 在 trigger 失活 / query 切换时 abort。
   * 实现 SHOULD 监听 signal.aborted 并尽早返回。
   */
  computeItems: (query: string, signal: AbortSignal) => Promise<TItem[]>;
  /**
   * State 变化通知。每次 state mutate 后调用一次。
   * caller（slash/wikilink plugin）在此 emit 自己的 `*TriggerChange` 事件。
   */
  onStateChange: (state: CharTriggerState<TItem>) => void;
}

export interface CharTriggerHandle<TItem> {
  /** 注入 EditorView 的 CM6 扩展 */
  extension: Extension;
  /** 当前 state 快照（read-only） */
  getState(): CharTriggerState<TItem>;
  /** 命令调用：activeIndex+1 */
  next(): void;
  /** 命令调用：activeIndex-1 */
  prev(): void;
  /** 命令调用：跳到指定 index（鼠标点击 UI 走此路径） */
  setActiveIndex(index: number): void;
  /**
   * 命令调用：dismiss trigger。`deleteTriggerText` 为 true 时删除 `[from,to]`
   * 区间的文本（slash 默认行为；wikilink 默认 false 保留 `[[query`）。
   */
  dismiss(view: EditorView, deleteTriggerText: boolean): void;
  /**
   * 命令调用：confirm。返回当前 activeIndex 对应的 item（caller 自行执行 commit）。
   * 调用后 trigger 自动 dismiss（不删除 trigger text，由 commit 函数决定如何处理 range）。
   */
  confirm(view: EditorView): TItem | undefined;
}

function inactiveState<TItem>(): CharTriggerState<TItem> {
  return {
    active: false,
    query: '',
    range: { from: 0, to: 0 },
    items: [],
    activeIndex: 0,
    screenRect: undefined,
  };
}

/**
 * 创建一个 char-trigger 状态机。
 *
 * 返回 `{ extension, getState, next, prev, dismiss, confirm }` 句柄。
 * 把 `extension` 通过 `ctx.registerCmExtensions([handle.extension])` 注入 editor；
 * 把 `next/prev/dismiss/confirm` 接到对应的 `EditorCommandSpec.run` 中。
 */
export function createCharTriggerStateMachine<TItem>(
  config: CharTriggerConfig<TItem>,
): CharTriggerHandle<TItem> {
  let current: CharTriggerState<TItem> = inactiveState<TItem>();
  let queryToken = 0;
  let abortController: AbortController | null = null;
  let debounceTimer: ReturnType<typeof setTimeout> | null = null;
  let currentView: EditorView | null = null;

  const emit = () => {
    config.onStateChange(current);
  };

  const setState = (next: CharTriggerState<TItem>) => {
    current = next;
    emit();
  };

  /**
   * Compute screen rect in the CM6 **measure phase** (not in update).
   * Calling `view.coordsAtPos` directly inside an update phase throws
   * "Reading the editor layout isn't allowed during an update".
   *
   * Schedules `view.requestMeasure`; once layout is read, mutates the
   * current state's screenRect and re-emits if the trigger is still
   * active at the same range.
   */
  const scheduleMeasureRect = (view: EditorView, from: number, to: number) => {
    view.requestMeasure({
      read(v) {
        const fromCoords = v.coordsAtPos(from);
        const toCoords = v.coordsAtPos(to);
        if (!fromCoords || !toCoords) return null;
        const left = Math.min(fromCoords.left, toCoords.left);
        const right = Math.max(fromCoords.right, toCoords.right);
        const top = Math.min(fromCoords.top, toCoords.top);
        const bottom = Math.max(fromCoords.bottom, toCoords.bottom);
        return { x: left, y: top, width: right - left, height: bottom - top };
      },
      write(rect) {
        if (!rect) return;
        if (!current.active) return;
        if (current.range.from !== from || current.range.to !== to) return;
        setState({ ...current, screenRect: rect });
      },
    });
  };

  const cancelInFlight = () => {
    if (abortController) {
      abortController.abort();
      abortController = null;
    }
    if (debounceTimer) {
      clearTimeout(debounceTimer);
      debounceTimer = null;
    }
  };

  const scheduleCompute = (view: EditorView) => {
    cancelInFlight();
    const myToken = ++queryToken;
    const ac = new AbortController();
    abortController = ac;
    const { query } = current;
    debounceTimer = setTimeout(() => {
      debounceTimer = null;
      config
        .computeItems(query, ac.signal)
        .then((items) => {
          if (ac.signal.aborted || myToken !== queryToken) return;
          if (!current.active) return;
          // computeItems 完成时 setState 保留现有 screenRect（measure phase 已通过
          // scheduleMeasureRect 异步更新过）
          setState({
            ...current,
            items,
            activeIndex: items.length > 0 ? 0 : 0,
          });
        })
        .catch((err: unknown) => {
          if (ac.signal.aborted) return;
          if (myToken !== queryToken) return;
          console.error('[editor-core] charTrigger computeItems rejected', err);
        });
    }, DEBOUNCE_MS);
  };

  const tryActivate = (view: EditorView): boolean => {
    const { state } = view;
    const sel = state.selection.main;
    if (sel.from !== sel.to) return false;
    const cursor = sel.from;
    const trig = config.triggerChar;
    const trigLen = trig.length;
    if (cursor < trigLen) return false;
    const justTyped = state.doc.sliceString(cursor - trigLen, cursor);
    if (justTyped !== trig) return false;

    // 前一字符必须 whitespace / line start / doc start
    // 这一规则对所有 char-trigger 统一：避免 `foo[[` / `[/` / `x/` 等中段误触发
    if (cursor - trigLen > 0) {
      const prev = state.doc.sliceString(cursor - trigLen - 1, cursor - trigLen);
      if (!/\s/.test(prev)) return false;
    }

    if (!config.validateContext(state, cursor)) return false;

    const range = { from: cursor - trigLen, to: cursor };
    setState({
      active: true,
      query: '',
      range,
      items: [],
      activeIndex: 0,
      screenRect: undefined,
    });
    scheduleMeasureRect(view, range.from, range.to);
    scheduleCompute(view);
    return true;
  };

  const tryUpdateQuery = (view: EditorView) => {
    if (!current.active) return;
    const { state } = view;
    const sel = state.selection.main;
    if (sel.from !== sel.to) {
      deactivate();
      return;
    }
    const cursor = sel.from;
    const triggerFrom = current.range.from;
    const triggerCharLen = config.triggerChar.length;
    if (cursor < triggerFrom + triggerCharLen) {
      deactivate();
      return;
    }
    // 验证 trigger char 仍然存在
    const stillTrigger =
      state.doc.sliceString(triggerFrom, triggerFrom + triggerCharLen) === config.triggerChar;
    if (!stillTrigger) {
      deactivate();
      return;
    }
    const query = state.doc.sliceString(triggerFrom + triggerCharLen, cursor);
    // 空白终止 trigger（用户键入空格后通常表示不再选 slash item）
    if (/\s/.test(query)) {
      deactivate();
      return;
    }
    if (query === current.query && cursor === current.range.to) return;
    setState({
      ...current,
      query,
      range: { from: triggerFrom, to: cursor },
      screenRect: undefined,
    });
    scheduleMeasureRect(view, triggerFrom, cursor);
    scheduleCompute(view);
  };

  const deactivate = () => {
    cancelInFlight();
    if (!current.active) return;
    setState(inactiveState<TItem>());
  };

  const extension = ViewPlugin.fromClass(
    class {
      constructor(view: EditorView) {
        currentView = view;
      }

      update(u: ViewUpdate) {
        currentView = u.view;

        // IME composition 期间不处理
        if (u.view.composing) return;

        if (u.focusChanged && !u.view.hasFocus) {
          deactivate();
          return;
        }

        if (u.docChanged) {
          if (current.active) {
            tryUpdateQuery(u.view);
          } else {
            tryActivate(u.view);
          }
          return;
        }

        if (u.selectionSet && current.active) {
          tryUpdateQuery(u.view);
        }
      }

      destroy() {
        cancelInFlight();
        currentView = null;
      }
    },
  );

  return {
    extension,
    getState() {
      return current;
    },
    next() {
      if (!current.active || current.items.length === 0) return;
      setState({
        ...current,
        activeIndex: (current.activeIndex + 1) % current.items.length,
      });
    },
    prev() {
      if (!current.active || current.items.length === 0) return;
      setState({
        ...current,
        activeIndex:
          (current.activeIndex - 1 + current.items.length) % current.items.length,
      });
    },
    setActiveIndex(index) {
      if (!current.active || current.items.length === 0) return;
      const clamped = Math.max(0, Math.min(index, current.items.length - 1));
      if (clamped === current.activeIndex) return;
      setState({ ...current, activeIndex: clamped });
    },
    dismiss(view, deleteTriggerText) {
      if (!current.active) return;
      const range = current.range;
      deactivate();
      if (deleteTriggerText && view) {
        view.dispatch({ changes: { from: range.from, to: range.to, insert: '' } });
      }
    },
    confirm(view) {
      if (!current.active || current.items.length === 0) {
        deactivate();
        return undefined;
      }
      const item = current.items[current.activeIndex];
      // commit 函数自己处理 range（典型：替换 trigger range 为 item 决定的内容）
      // 这里 dismiss 但 NOT delete text，由 commit 实现决定
      deactivate();
      void view;
      return item;
    },
  };
}
