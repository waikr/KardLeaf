/**
 * Wikilink Ctrl/Cmd+点击扩展。
 *
 * 与 `ctrlClickLinksExtension` 互补：那个走 lezer markdown `Link` / `URL`
 * 语法节点，处理 `[text](url)` 形式。本扩展用 regex 在当前行内找
 * `[[<target>]]` 文本，处理 Obsidian 风格 wikilink。
 *
 * 触发方式同 `ctrlClickLinksExtension`：
 * - 桌面：Ctrl（Win/Linux）/ Cmd（macOS）+ 点击
 * - 移动：500ms 长按
 *
 * 找到 wikilink 后调 `onLinkOpen(target)` —— target 是 `[[]]` 中的原始文本
 * （不含 `[[` `]]` 标记）。Host 端根据 url 形态决定打开外部链接还是跳转笔记。
 */
import type { EditorState, Extension } from '@codemirror/state';
import { EditorView } from '@codemirror/view';
import type { OnLinkOpen } from './ctrlClickLinksExtension';

/**
 * 在 `pos` 位置查找 wikilink。返回 target + 文档范围，未找到返回 null。
 *
 * 仅在 pos 当前行内查找；wikilink 不允许跨行。
 */
export function findWikilinkAtPosition(
  pos: number,
  state: EditorState,
): { target: string; from: number; to: number } | null {
  const line = state.doc.lineAt(pos);
  const text = line.text;
  const offset = pos - line.from;
  const regex = /\[\[([^\[\]\n]+)\]\]/g;
  let m: RegExpExecArray | null;
  // biome-ignore lint/suspicious/noAssignInExpressions: classic regex iter
  while ((m = regex.exec(text)) !== null) {
    const start = m.index;
    const end = m.index + m[0].length;
    if (offset >= start && offset <= end) {
      return {
        target: m[1],
        from: line.from + start,
        to: line.from + end,
      };
    }
  }
  return null;
}

/**
 * Wikilink 单击跳转（Obsidian 风格）：
 * - cursor 当前不在该 wikilink 范围内 → 单击直接跳转
 * - cursor 已在该 wikilink 范围内（reveal 编辑模式）→ 让 CM6 默认处理 click（设置 cursor）
 *
 * 这与 markdown link 的 Ctrl/Cmd+Click 体验不同：wikilink 在"完整文本被装饰为
 * 蓝色链接"时视觉等同于网页链接，用户期望单击跳转；进入 reveal 模式后才允许编辑。
 */
export function createWikilinkClickExtension(onLinkOpen: OnLinkOpen): Extension {
  return [
    EditorView.domEventHandlers({
      mousedown(event, view) {
        if (event.button !== 0) return false;
        if (event.shiftKey || event.altKey) return false;

        // 行 padding 空白点击过滤：避免 posAtCoords "找最近字符"误触发
        const target = event.target;
        if (target instanceof HTMLElement && target.classList.contains('cm-line')) {
          return false;
        }

        const pos = view.posAtCoords({ x: event.clientX, y: event.clientY });
        if (pos === null) return false;
        const hit = findWikilinkAtPosition(pos, view.state);
        if (!hit) return false;

        // mouse pos 必须严格在 wikilink 字符上才跳转，boundary 让 CM6 默认设 cursor
        if (pos <= hit.from || pos >= hit.to) return false;

        // Cmd/Ctrl+click 总是跳转（不管 reveal 状态）
        if (event.ctrlKey || event.metaKey) {
          event.preventDefault();
          onLinkOpen(hit.target);
          return true;
        }

        // 普通单击：reveal 状态下不跳转，让 CM6 设 cursor 编辑
        const sel = view.state.selection.main;
        const isRevealed = sel.from <= hit.to && sel.to >= hit.from;
        if (isRevealed) return false;

        // 普通单击 + 装饰状态 → 跳转
        event.preventDefault();
        onLinkOpen(hit.target);
        return true;
      },
    }),

    // 移动：500ms 长按（仍保留 Cmd+Click fallback 给桌面）
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
          const hit = findWikilinkAtPosition(pos, view.state);
          if (hit) {
            event.preventDefault();
            onLinkOpen(hit.target);
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
