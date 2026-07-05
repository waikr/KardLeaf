/**
 * Wikilink plugin (v0.3 phase B).
 *
 * 在 `[[` 处激活 wikilink trigger，dispatch `WikilinkTriggerChange` 事件。
 * 候选项来自两个源：
 * - Plugin via `ctx.registerWikilinkItems(provider)`
 * - Host via `host.getWikilinkItems(query, signal)`
 *
 * 注册 5 个内置命令：
 * - `wikilink.next` / `wikilink.prev` — 移动 activeIndex
 * - `wikilink.confirm` — 替换 `[[query` 为 `[[<item.title>]]`，cursor 移到 `]]` 之后
 * - `wikilink.confirmAt(index)` — 同 confirm 但先跳到 index（鼠标点击用）
 * - `wikilink.dismiss` — 关闭 trigger，**保留** `[[query` 文本（unlike slash）
 *
 * 复用 `createCharTriggerStateMachine` helper（slash 与 wikilink 共用）。
 */
import type { EditorState } from '@codemirror/state';
import { syntaxTree } from '@codemirror/language';
import { EditorView } from '@codemirror/view';
import { editorEventCallback, EditorEventType } from '../../../events';
import { createCharTriggerStateMachine } from '../../../internal/charTriggerStateMachine';
import { wikilinkItemProvidersFacet } from '../../../pluginHost';
import type { EditorPlugin, WikilinkItem, WikilinkItemProvider } from '../../../types';
import { createWikilinkDecorationExtension } from './wikilinkDecorationExtension';

const HOST_PROVIDER_PRIORITY = 200;
const DEFAULT_PROVIDER_PRIORITY = 100;

export interface WikilinkPluginOptions {
  /**
   * 选中 item 完成 commit 后被调用，传 item.id（典型 = note path）。
   * Host 可用此 hook 记录 MRU / 触发跳转（item.commit === 'jumpToNote' 时）。
   */
  onItemConfirmed?: (itemId: string, commit: WikilinkItem['commit']) => void;
}

function validateContext(state: EditorState, pos: number): boolean {
  const tree = syntaxTree(state);
  let node: ReturnType<typeof tree.resolveInner> | null = tree.resolveInner(pos, -1);
  while (node) {
    const name = node.type.name;
    if (
      name === 'FencedCode' ||
      name === 'CodeBlock' ||
      name === 'InlineCode' ||
      name === 'MathBlock' ||
      name === 'InlineMath' ||
      name === 'FrontMatter'
    ) {
      return false;
    }
    node = node.parent;
  }
  return true;
}

function scoreItem(item: WikilinkItem, query: string): number {
  if (!query) return 1;
  const q = query.toLowerCase();
  if (item.title.toLowerCase().includes(q)) return 1;
  return 0;
}

function sortAndDedupe(
  collected: { item: WikilinkItem; priority: number; score: number }[],
): WikilinkItem[] {
  collected.sort((a, b) => {
    if (a.priority !== b.priority) return b.priority - a.priority;
    if (a.score !== b.score) return b.score - a.score;
    return a.item.id.localeCompare(b.item.id);
  });
  const seen = new Set<string>();
  const out: WikilinkItem[] = [];
  for (const e of collected) {
    if (seen.has(e.item.id)) continue;
    seen.add(e.item.id);
    out.push(e.item);
  }
  return out;
}

export function wikilinkPlugin(options?: WikilinkPluginOptions): EditorPlugin {
  return {
    id: 'wikilink',
    version: '0.3.0',
    setup(ctx) {
      let currentView: EditorView | null = null;

      const handle = createCharTriggerStateMachine<WikilinkItem>({
        triggerChar: '[[',
        validateContext,
        async computeItems(query, signal) {
          if (!currentView) return [];
          const providers = currentView.state.facet(wikilinkItemProvidersFacet);
          const collected: { item: WikilinkItem; priority: number; score: number }[] = [];

          const fromProvider = async (p: WikilinkItemProvider) => {
            try {
              const items = await p.provide(query, signal);
              if (signal.aborted) return;
              const providerPriority = p.priority ?? DEFAULT_PROVIDER_PRIORITY;
              for (const it of items) {
                collected.push({
                  item: it,
                  priority: it.priority ?? providerPriority,
                  score: scoreItem(it, query),
                });
              }
            } catch (err) {
              if (!signal.aborted) {
                console.error(`[editor-core] wikilink provider "${p.id}" rejected`, err);
              }
            }
          };

          const fromHost = async () => {
            if (!ctx.host.getWikilinkItems) return;
            try {
              const items = await ctx.host.getWikilinkItems(query, signal);
              if (signal.aborted) return;
              for (const it of items) {
                collected.push({
                  item: it,
                  priority: it.priority ?? HOST_PROVIDER_PRIORITY,
                  score: scoreItem(it, query),
                });
              }
            } catch (err) {
              if (!signal.aborted) {
                console.error('[editor-core] host.getWikilinkItems rejected', err);
              }
            }
          };

          await Promise.all([...providers.map(fromProvider), fromHost()]);
          if (signal.aborted) return [];
          return sortAndDedupe(collected);
        },
        onStateChange(state) {
          if (!currentView) return;
          const emit = currentView.state.facet(editorEventCallback);
          if (!emit) return;
          emit({
            kind: EditorEventType.WikilinkTriggerChange,
            match: state,
          });
        },
      });

      const viewRefCapture = EditorView.updateListener.of((u) => {
        currentView = u.view;
      });

      ctx.registerCmExtensions([
        handle.extension,
        viewRefCapture,
        createWikilinkDecorationExtension(),
      ]);

      const performConfirm = (view: EditorView) => {
        const range = handle.getState().range;
        const item = handle.confirm(view);
        if (!item) return;

        try {
          options?.onItemConfirmed?.(item.id, item.commit);
        } catch (err) {
          console.error(`[editor-core] wikilink onItemConfirmed threw for "${item.id}"`, err);
        }

        // CM6 closeBrackets 扩展在用户键入 `[` 时会自动插入对应 `]`。当 trigger
        // 是 `[[`，closeBrackets 会留下 `]]` 在 cursor 之后。我们把这些已存在的
        // 尾部 `]` 字符纳入替换范围，避免出现 `[[<title>]]]]`。
        let endPos = range.to;
        const after = view.state.doc.sliceString(range.to, range.to + 2);
        if (after === ']]') {
          endPos = range.to + 2;
        } else if (after.startsWith(']')) {
          endPos = range.to + 1;
        }

        // Replace `[[query` (+ stray closing `]]` if present) with `[[<title>]]`
        const replacement = `[[${item.title}]]`;
        view.dispatch({
          changes: { from: range.from, to: endPos, insert: replacement },
          selection: { anchor: range.from + replacement.length },
        });
      };

      ctx.registerCommands([
        {
          id: 'wikilink.next',
          title: 'Wikilink: next item',
          when: () => handle.getState().active,
          run({ view }) {
            currentView = view;
            handle.next();
          },
        },
        {
          id: 'wikilink.prev',
          title: 'Wikilink: previous item',
          when: () => handle.getState().active,
          run({ view }) {
            currentView = view;
            handle.prev();
          },
        },
        {
          id: 'wikilink.confirm',
          title: 'Wikilink: confirm selection',
          when: () => handle.getState().active,
          run({ view }) {
            currentView = view;
            performConfirm(view);
          },
        },
        {
          id: 'wikilink.confirmAt',
          title: 'Wikilink: confirm at index',
          when: () => handle.getState().active,
          run({ view }, ...args) {
            currentView = view;
            const index = typeof args[0] === 'number' ? args[0] : 0;
            handle.setActiveIndex(index);
            performConfirm(view);
          },
        },
        {
          id: 'wikilink.dismiss',
          title: 'Wikilink: dismiss',
          when: () => handle.getState().active,
          run({ view }) {
            currentView = view;
            // 与 slash 不同：dismiss 不删除 `[[query` 文本（用户可能继续输入）
            handle.dismiss(view, /* deleteTriggerText */ false);
          },
        },
      ]);

      return {
        dispose() {
          currentView = null;
        },
      };
    },
  };
}
