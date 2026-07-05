/**
 * 链接点击扩展（Obsidian / Notion 风格单击跳转）。
 *
 * - 单击 markdown 链接 `[text](url)`，且 cursor 不在该链接范围内 → 跳转
 * - cursor 已在链接范围内（reveal 编辑模式）→ 让 CM6 默认处理（设置 cursor）
 * - 移动端长按 500ms 触发跳转
 *
 * `.cm-ext-link` 已经在 `addFormattingClasses` 内设 `cursor: pointer`，
 * 视觉提示用户可点击；这里只负责 click 事件路由。
 */
import type { Extension } from '@codemirror/state';
import { EditorView } from '@codemirror/view';
import { findLinkAtPosition } from './linkUtils';

/** 链接打开回调函数类型 */
export type OnLinkOpen = (url: string) => void;

export function createCtrlClickLinksExtension(onLinkOpen: OnLinkOpen): Extension {
  return [
    // 桌面：mousedown 早于 click fire，preventDefault 更可靠
    EditorView.domEventHandlers({
      mousedown(event, view) {
        if (event.button !== 0) return false;
        if (event.shiftKey || event.altKey) return false;

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

    // 移动：500ms 长按
    EditorView.domEventHandlers({
      touchstart(event, view) {
        if (event.touches.length !== 1) return false;
        const touch = event.touches[0];
        const startX = touch.clientX;
        const startY = touch.clientY;

        const controller = new AbortController();
        const { signal } = controller;
        const timer = setTimeout(() => {
          controller.abort();
          const pos = view.posAtCoords({ x: startX, y: startY });
          if (pos === null) return;
          const link = findLinkAtPosition(pos, view.state);
          if (link) {
            event.preventDefault();
            onLinkOpen(link.url);
          }
        }, 500);

        view.dom.addEventListener(
          'touchend',
          () => {
            clearTimeout(timer);
            controller.abort();
          },
          { once: true, signal },
        );
        view.dom.addEventListener(
          'touchmove',
          () => {
            clearTimeout(timer);
            controller.abort();
          },
          { once: true, signal },
        );

        return false;
      },
    }),
  ];
}
