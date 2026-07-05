/**
 * Selection toolbar plugin (v0.3 phase C).
 *
 * 在非空选区上方浮起 toolbar，dispatch `SelectionToolbarChange` 事件让 host
 * 渲染按钮。actions 来源三源混合：
 * - Plugin built-in defaults (bold / italic / strike / inline-code / link)
 * - Other plugin via `ctx.registerSelectionToolbarActions(actions)`
 * - Host via `host.getSelectionToolbarActions?(selection)` 同步调用
 *
 * Active 转换：
 * - false → true：selection 非空 + focus + 不在 code/math block 内 +
 *   非 IME composition。立即激活，无 debounce。
 * - true → false：selection collapse / focus blur / 移入 code block。
 *   100ms debounce 防 drag 释放时短暂闪烁；blur 立即 dismiss（无 debounce）。
 *
 * 注册命令：
 * - `selectionToolbar.dismiss` — 手动 dismiss（如 Esc 键）
 * 其它 action 直接通过 `action.commandId` 调 `EditorControl.execCommand`，
 * 不需要中转命令。
 */
import type { EditorState } from '@codemirror/state';
import { syntaxTree } from '@codemirror/language';
import { EditorView, ViewPlugin, type ViewUpdate } from '@codemirror/view';
import {
  editorEventCallback,
  EditorEventType,
  type SelectionToolbarMatch,
} from '../../../events';
import { selectionToolbarActionsFacet } from '../../../pluginHost';
import type { EditorPlugin, SelectionToolbarAction } from '../../../types';

const DEBOUNCE_DISMISS_MS = 100;

const DEFAULT_ACTIONS: SelectionToolbarAction[] = [
  { id: 'bold', title: 'Bold', icon: 'bold', commandId: 'toggleBold', priority: 100 },
  { id: 'italic', title: 'Italic', icon: 'italic', commandId: 'toggleItalic', priority: 100 },
  {
    id: 'strike',
    title: 'Strikethrough',
    icon: 'strikethrough',
    commandId: 'toggleStrike',
    priority: 100,
  },
  { id: 'code', title: 'Inline code', icon: 'code', commandId: 'toggleCode', priority: 100 },
  { id: 'link', title: 'Link', icon: 'link', commandId: 'insertLink', priority: 100 },
];

export interface SelectionToolbarPluginOptions {
  /** Override 内置 default actions（替代而非合并；plugin/host 仍可追加） */
  defaultActions?: SelectionToolbarAction[];
}

function isInExcludedBlock(state: EditorState, from: number, to: number): boolean {
  const tree = syntaxTree(state);
  const node = tree.resolveInner(Math.floor((from + to) / 2), -1);
  let cur: typeof node | null = node;
  while (cur) {
    const name = cur.type.name;
    if (
      name === 'FencedCode' ||
      name === 'CodeBlock' ||
      name === 'InlineCode' ||
      name === 'MathBlock' ||
      name === 'InlineMath' ||
      name === 'FrontMatter'
    ) {
      return true;
    }
    cur = cur.parent;
  }
  return false;
}

function emptyMatch(): SelectionToolbarMatch {
  return {
    active: false,
    selection: { from: 0, to: 0 },
    actions: [],
    screenRect: undefined,
  };
}

function dedupeActions(actions: SelectionToolbarAction[]): SelectionToolbarAction[] {
  const map = new Map<string, SelectionToolbarAction>();
  // Later writers win (host > plugin-registered > built-in)
  for (const a of actions) map.set(a.id, a);
  return Array.from(map.values());
}

export function selectionToolbarPlugin(
  options?: SelectionToolbarPluginOptions,
): EditorPlugin {
  return {
    id: 'selectionToolbar',
    version: '0.3.0',
    setup(ctx) {
      let current: SelectionToolbarMatch = emptyMatch();
      let dismissTimer: ReturnType<typeof setTimeout> | null = null;
      let currentView: EditorView | null = null;
      // Notion / BlockNote 风格：drag 期间不显示 toolbar，松开鼠标后才弹。
      // selectionSet 在 drag 中频繁触发；用此标记跳过 activate。
      let isMouseDragging = false;

      const emit = (view: EditorView) => {
        const cb = view.state.facet(editorEventCallback);
        if (!cb) return;
        cb({
          kind: EditorEventType.SelectionToolbarChange,
          match: current,
        });
      };

      const cancelDismissTimer = () => {
        if (dismissTimer) {
          clearTimeout(dismissTimer);
          dismissTimer = null;
        }
      };

      const scheduleRectMeasure = (view: EditorView, from: number, to: number) => {
        view.requestMeasure({
          read(v): SelectionToolbarMatch['screenRect'] {
            const fromCoords = v.coordsAtPos(from);
            const toCoords = v.coordsAtPos(to);
            if (!fromCoords || !toCoords) return undefined;
            const left = Math.min(fromCoords.left, toCoords.left);
            const right = Math.max(fromCoords.right, toCoords.right);
            const top = Math.min(fromCoords.top, toCoords.top);
            const bottom = Math.max(fromCoords.bottom, toCoords.bottom);
            return { x: left, y: top, width: right - left, height: bottom - top };
          },
          write(rect, v) {
            if (!rect) return;
            if (!current.active) return;
            if (current.selection.from !== from || current.selection.to !== to) return;
            current = { ...current, screenRect: rect };
            emit(v);
          },
        });
      };

      const computeActions = (
        view: EditorView,
        sel: { from: number; to: number },
      ): SelectionToolbarAction[] => {
        const defaults = options?.defaultActions ?? DEFAULT_ACTIONS;
        const pluginActions = view.state.facet(selectionToolbarActionsFacet);
        const hostActions = ctx.host.getSelectionToolbarActions?.(sel) ?? [];
        return dedupeActions([...defaults, ...pluginActions, ...hostActions]);
      };

      const activate = (view: EditorView, sel: { from: number; to: number }) => {
        cancelDismissTimer();
        current = {
          active: true,
          selection: sel,
          actions: computeActions(view, sel),
          screenRect: undefined,
        };
        emit(view);
        scheduleRectMeasure(view, sel.from, sel.to);
      };

      const deactivateNow = (view: EditorView) => {
        cancelDismissTimer();
        if (!current.active) return;
        current = emptyMatch();
        emit(view);
      };

      const scheduleDeactivate = (view: EditorView) => {
        cancelDismissTimer();
        dismissTimer = setTimeout(() => {
          dismissTimer = null;
          if (!current.active) return;
          current = emptyMatch();
          emit(view);
        }, DEBOUNCE_DISMISS_MS);
      };

      const evaluate = (view: EditorView) => {
        if (view.composing) return;

        const sel = view.state.selection.main;
        const empty = sel.from === sel.to;
        const focused = view.hasFocus;

        if (empty || !focused) {
          if (current.active) scheduleDeactivate(view);
          return;
        }
        if (isInExcludedBlock(view.state, sel.from, sel.to)) {
          if (current.active) scheduleDeactivate(view);
          return;
        }

        // Drag 中：dismiss 现有 toolbar（如果之前已经弹出），但不 activate 新的
        // 等待 mouseup 后 re-evaluate
        if (isMouseDragging) {
          if (current.active) deactivateNow(view);
          return;
        }

        // 同 selection 已 active —— 不重 emit（防止 actions 重复计算抖动）
        if (
          current.active &&
          current.selection.from === sel.from &&
          current.selection.to === sel.to
        ) {
          return;
        }
        activate(view, { from: sel.from, to: sel.to });
      };

      const extension = ViewPlugin.fromClass(
        class {
          constructor(view: EditorView) {
            currentView = view;
          }
          update(u: ViewUpdate) {
            currentView = u.view;
            if (u.focusChanged && !u.view.hasFocus) {
              deactivateNow(u.view);
              return;
            }
            if (u.selectionSet || u.focusChanged || u.docChanged) {
              evaluate(u.view);
            }
          }
          destroy() {
            cancelDismissTimer();
            currentView = null;
          }
        },
      );

      // Notion-style：drag 期间不显示 toolbar，松开鼠标后 evaluate
      // mouseup 监听 window 而非 contentDOM —— 用户可能 drag 到编辑器外才松开
      const mouseTracker = EditorView.domEventHandlers({
        mousedown: () => {
          isMouseDragging = true;
        },
      });

      const windowMouseUp = () => {
        if (!isMouseDragging) return;
        isMouseDragging = false;
        if (currentView) {
          // 让 CM6 selectionSet 先 settle，下一帧再 evaluate
          requestAnimationFrame(() => {
            if (currentView) evaluate(currentView);
          });
        }
      };
      window.addEventListener('mouseup', windowMouseUp);

      ctx.registerCmExtensions([extension, mouseTracker]);

      ctx.registerCommands([
        {
          id: 'selectionToolbar.dismiss',
          title: 'Selection toolbar: dismiss',
          when: () => current.active,
          run({ view }) {
            deactivateNow(view);
          },
        },
      ]);

      return {
        dispose() {
          cancelDismissTimer();
          window.removeEventListener('mouseup', windowMouseUp);
          currentView = null;
        },
      };
    },
  };
}
