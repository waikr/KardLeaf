/**
 * 链接点击扩展（Obsidian / Notion 风格单击跳转）。
 *
 * - 单击 markdown 链接 `[text](url)`，且 cursor 不在该链接范围内 → 跳转
 * - cursor 已在链接范围内（reveal 编辑模式）→ 让 CM6 默认处理（设置 cursor）
 * - 移动端仅短按跳转；长按与拖动保留浏览器原生选择
 *
 * `.cm-ext-link` 已经在 `addFormattingClasses` 内设 `cursor: pointer`，
 * 视觉提示用户可点击；这里只负责 click 事件路由。
 */
import type { EditorState, Extension } from '@codemirror/state';
import { EditorView } from '@codemirror/view';
import { findLinkAtPosition } from './linkUtils';

/** 链接打开回调函数类型 */
export type OnLinkOpen = (url: string) => void;

// Both link types must distinguish a tap from a long press, a cancelled scroll,
// and selection-handle movement. Do not install a navigation timer on long press.
export function createTouchLinkExtension(
  findLink: (pos: number, state: EditorState) => { from: number; to: number; target: string } | null,
  onLinkOpen: OnLinkOpen,
): Extension {
  let tap: { x: number; y: number; time: number; doc: EditorState['doc']; target: string } | null = null;
  return EditorView.domEventHandlers({
    touchstart(event, view) {
      tap = null;
      if (event.touches.length !== 1 || !view.state.selection.main.empty ||
        window.getSelection()?.isCollapsed === false) return false;
      const touch = event.touches[0];
      const pos = view.posAtCoords({ x: touch.clientX, y: touch.clientY });
      const link = pos === null ? null : findLink(pos, view.state);
      if (link && pos! > link.from && pos! < link.to) {
        tap = { x: touch.clientX, y: touch.clientY, time: performance.now(), doc: view.state.doc, target: link.target };
      }
      return false;
    },
    touchmove(event) {
      const touch = event.touches[0];
      if (tap && (!touch || event.touches.length !== 1 ||
        Math.hypot(touch.clientX - tap.x, touch.clientY - tap.y) > 8)) tap = null;
      return false;
    },
    touchend(event, view) {
      const start = tap;
      tap = null;
      const touch = event.changedTouches[0];
      if (!start || !touch || performance.now() - start.time > 280 || view.state.doc !== start.doc ||
        Math.hypot(touch.clientX - start.x, touch.clientY - start.y) > 8 ||
        !view.state.selection.main.empty || window.getSelection()?.isCollapsed === false) return false;
      onLinkOpen(start.target);
      return true;
    },
    touchcancel() {
      tap = null;
      return false;
    },
  });
}

export function createCtrlClickLinksExtension(onLinkOpen: OnLinkOpen): Extension {
  return [
    // 桌面：mousedown 早于 click fire，preventDefault 更可靠
    EditorView.domEventHandlers({
      mousedown(event, view) {
        if (event.button !== 0) return false;
        if (event.shiftKey || event.altKey) return false;
        if ((event as MouseEvent & { sourceCapabilities?: { firesTouchEvents: boolean } }).sourceCapabilities?.firesTouchEvents) return false;
        if (!event.ctrlKey && !event.metaKey && window.getSelection()?.isCollapsed === false) return false;

        // 点击在行右侧空白（行 padding / line end 之后）：target 是 .cm-line
        // 本身而非任何字符 span。CM6 posAtCoords 会"找最近字符 pos"返回
        // link 内的位置，造成误触发跳转。先用 DOM target 过滤掉这种 click。
        const target = event.target;
        if (target instanceof HTMLElement && target.classList.contains('cm-line')) {
          return false;
        }

        const pos = view.posAtCoords({ x: event.clientX, y: event.clientY });
        if (pos === null) return false;

        const link = findLinkAtPosition(pos, view.state);
        if (!link) return false;

        // mouse pos 必须严格在 link 字符上（不含 boundary）才考虑跳转。
        // boundary 外侧（如紧邻 `]` 右边的空隙）让 CM6 默认设 cursor → 触发 reveal
        if (pos <= link.from || pos >= link.to) return false;

        // Cmd/Ctrl+click 总是跳转（不管 cursor 处于 reveal 与否）
        if (event.ctrlKey || event.metaKey) {
          event.preventDefault();
          onLinkOpen(link.url);
          return true;
        }

        // 普通单击：cursor 已与 link 接触（reveal 状态，显示源码）→ 编辑模式
        // 不跳转，让 CM6 默认处理 click 设置 cursor 到点击位置
        const sel = view.state.selection.main;
        const isRevealed = sel.from <= link.to && sel.to >= link.from;
        if (isRevealed) return false;

        // 普通单击 + 装饰状态（cursor 远离 link，link 显示为装饰）→ 跳转
        event.preventDefault();
        onLinkOpen(link.url);
        return true;
      },
    }),

    createTouchLinkExtension((pos, state) => {
      const link = findLinkAtPosition(pos, state);
      return link ? { ...link, target: link.url } : null;
    }, onLinkOpen),
  ];
}
