import { Facet, type Extension } from '@codemirror/state';
import type { EditorView } from '@codemirror/view';
import type { EditorEvent, EditorEventType } from './events';
import type {
  Disposable,
  EditorCommandContext,
  EditorCommandSpec,
  EditorEventListener,
  EditorHostCapabilities,
  EditorPlugin,
  EditorPluginContext,
  EditorPluginInstance,
  EditorProps,
  MarkdownRenderRule,
  SelectionToolbarAction,
  SlashItemProvider,
  WikilinkItemProvider,
} from './types';
import { createSelectionRange } from './utils';

/**
 * Internal facet: 把 PluginHost.slashProviders 引用暴露给 ViewPlugin。
 *
 * 数组本身在 createPluginHost 时建立，后续 `ctx.registerSlashItems` mutate 这个 array
 * （push/splice 同一 reference）。Facet combine 取首个非空 array 引用并缓存——array
 * 引用不变，所以 `view.state.facet(slashItemProvidersFacet)` 总是返回 live array。
 *
 * NON-PUBLIC：第三方 plugin 不得直接使用此 facet；通过 `ctx.registerSlashItems()` 接入。
 */
export const slashItemProvidersFacet = Facet.define<
  readonly import('./types').SlashItemProvider[],
  readonly import('./types').SlashItemProvider[]
>({
  combine: (values) => values.find((v) => v !== undefined) ?? [],
});

/** Internal facet for wikilink providers (mirror of slashItemProvidersFacet). */
export const wikilinkItemProvidersFacet = Facet.define<
  readonly import('./types').WikilinkItemProvider[],
  readonly import('./types').WikilinkItemProvider[]
>({
  combine: (values) => values.find((v) => v !== undefined) ?? [],
});

/** Internal facet for plugin-registered selection toolbar actions (live array). */
export const selectionToolbarActionsFacet = Facet.define<
  readonly import('./types').SelectionToolbarAction[],
  readonly import('./types').SelectionToolbarAction[]
>({
  combine: (values) => values.find((v) => v !== undefined) ?? [],
});

/**
 * Internal facet: 把 `EditorControl.execCommand` 暴露给 plugin runtime。
 *
 * createEditor 创建 EditorControl 后注入；引用通过 mutable `ref.fn` 字段保持，
 * 这样 facet value (the ref object) 不变 → plugin 通过 `view.state.facet(...)`
 * 总是拿到最新的 fn。
 *
 * NON-PUBLIC：第三方 plugin 不得直接使用；通过 `ctx.host` / `registerCommands` 接入。
 */
export interface ExecCommandRef {
  fn: ((id: string, ...args: unknown[]) => unknown) | null;
}

export const execCommandFacet = Facet.define<ExecCommandRef, ExecCommandRef>({
  combine: (values) => values.find((v) => v !== undefined) ?? { fn: null },
});

interface PluginHostState {
  commands: Map<string, EditorCommandSpec>;
  extensions: Extension[];
  renderers: Map<string, MarkdownRenderRule>;
  disposables: Disposable[];
  instances: EditorPluginInstance[];
  slashProviders: SlashItemProvider[];
  wikilinkProviders: WikilinkItemProvider[];
  selectionToolbarActions: SelectionToolbarAction[];
  eventListeners: Map<EditorEventType, Set<EditorEventListener>>;
}

export interface PluginHost {
  /** Plugin 注册的 CM 扩展，在 createEditor 中合并进 EditorState */
  readonly extensions: readonly Extension[];
  /** Plugin 注册的 slash provider 列表（供 slashCommandPlugin runtime 读取） */
  readonly slashProviders: readonly SlashItemProvider[];
  /** Plugin 注册的 wikilink provider 列表 */
  readonly wikilinkProviders: readonly WikilinkItemProvider[];
  /** Plugin 注册的 selection toolbar actions 列表 */
  readonly selectionToolbarActions: readonly SelectionToolbarAction[];
  /**
   * 尝试执行 plugin 注册的命令。
   * - 返回 `true`：命中（且已执行 / 被 `when` 否决）
   * - 返回 `false`：未命中，应 fallback 到内置命令
   *
   * `args` 透传给命令 `run(ctx, ...args)`。
   */
  execPluginCommand(view: EditorView, id: string, ...args: unknown[]): boolean;
  /**
   * 把内核 emit 的事件分发给所有 `ctx.on` 订阅者。
   * 在内核 emit 路径上调用一次即可。
   */
  dispatchEvent(event: EditorEvent): void;
  /** Editor 销毁时调用：反向 dispose 全部 disposable 与 plugin 实例 */
  destroy(): void;
}

/**
 * 合并 deprecated 顶层字段 (`imageResolver` / `uploadFile`) 到 host 对象。
 *
 * - 仅 deprecated 字段存在 → 桥接到 host.*
 * - 仅 host.* 存在 → 直接使用
 * - 双方都存在 → host.* 优先，触发一次 console.warn（按字段聚合一次）
 */
export function mergeHostCapabilities(
  host: EditorHostCapabilities | undefined,
  imageResolver: EditorProps['imageResolver'],
  uploadFile: EditorProps['uploadFile'],
): EditorHostCapabilities {
  const merged: EditorHostCapabilities = { ...(host ?? {}) };
  const conflicts: string[] = [];
  const bridge = <K extends keyof EditorHostCapabilities>(
    field: K,
    legacy: EditorHostCapabilities[K] | undefined,
  ) => {
    if (!legacy) return;
    if (host?.[field]) conflicts.push(String(field));
    else merged[field] = legacy;
  };
  bridge('resolveImage', imageResolver);
  bridge('uploadFile', uploadFile);
  if (conflicts.length) {
    console.warn(
      `[editor-core] EditorProps deprecated field(s) [${conflicts.join(', ')}] conflict with host.* — host.* takes precedence.`,
    );
  }
  return merged;
}

/**
 * 创建 plugin host：遍历 `plugins` 依次调用 `setup(ctx)`，收集每个 plugin
 * 注册的命令 / CM 扩展 / Markdown 渲染规则与 disposable。
 *
 * 调用方应在 createEditor 内：
 * 1. 拿到 `host.extensions` 合并进 `EditorState.extensions`
 * 2. 把 `host` 透给 `EditorControl`，让 `execCommand` 优先查 plugin 命令
 * 3. 在 `EditorControl.destroy()` 内调 `host.destroy()` 反向 dispose
 */
export function createPluginHost(
  host: EditorHostCapabilities,
  plugins: readonly EditorPlugin[] | undefined,
): PluginHost {
  const state: PluginHostState = {
    commands: new Map(),
    extensions: [],
    renderers: new Map(),
    disposables: [],
    instances: [],
    slashProviders: [],
    wikilinkProviders: [],
    selectionToolbarActions: [],
    eventListeners: new Map(),
  };

  // 把 *Providers live 引用注入 facet，对应 plugin 的 ViewPlugin 可读
  state.extensions.push(slashItemProvidersFacet.of(state.slashProviders));
  state.extensions.push(wikilinkItemProvidersFacet.of(state.wikilinkProviders));
  state.extensions.push(selectionToolbarActionsFacet.of(state.selectionToolbarActions));

  for (const plugin of plugins ?? []) {
    const ctx = createCtx(plugin, host, state);
    const instance = plugin.setup(ctx);
    if (instance) state.instances.push(instance);
  }

  return {
    get extensions() {
      return state.extensions;
    },
    get slashProviders() {
      return state.slashProviders;
    },
    get wikilinkProviders() {
      return state.wikilinkProviders;
    },
    get selectionToolbarActions() {
      return state.selectionToolbarActions;
    },
    dispatchEvent(event) {
      const listeners = state.eventListeners.get(event.kind);
      if (!listeners || listeners.size === 0) return;
      for (const l of listeners) {
        try {
          l(event);
        } catch (err) {
          console.error('[editor-core] plugin event listener threw', err);
        }
      }
    },
    execPluginCommand(view, id, ...args) {
      const spec = state.commands.get(id);
      if (!spec) return false;
      const sel = view.state.selection.main;
      const cmdCtx: EditorCommandContext = {
        view,
        selection: createSelectionRange(sel.anchor, sel.head),
      };
      if (spec.when && !spec.when(cmdCtx)) return true;
      const result = spec.run(cmdCtx, ...args);
      if (result && typeof (result as Promise<unknown>).then === 'function') {
        (result as Promise<unknown>).catch((err) => {
          console.error(`[editor-core] plugin command "${id}" rejected`, err);
        });
      }
      return true;
    },
    destroy() {
      for (let i = state.disposables.length - 1; i >= 0; i--) {
        try {
          state.disposables[i].dispose();
        } catch (err) {
          console.error('[editor-core] disposable dispose error', err);
        }
      }
      for (let i = state.instances.length - 1; i >= 0; i--) {
        try {
          state.instances[i].dispose?.();
        } catch (err) {
          console.error('[editor-core] plugin dispose error', err);
        }
      }
    },
  };
}

function createCtx(
  plugin: EditorPlugin,
  host: EditorHostCapabilities,
  state: PluginHostState,
): EditorPluginContext {
  const trackDisposable = (d: Disposable): Disposable => {
    state.disposables.push(d);
    return d;
  };

  return {
    host,
    registerCommands(specs) {
      const installed = new Map<string, EditorCommandSpec>();
      for (const spec of specs) {
        const prev = state.commands.get(spec.id);
        if (prev) {
          console.warn(
            `[editor-core] command "${spec.id}" registered by plugin "${plugin.id}" overrides existing registration`,
          );
        }
        state.commands.set(spec.id, spec);
        installed.set(spec.id, spec);
      }
      return trackDisposable({
        dispose() {
          for (const [id, spec] of installed) {
            if (state.commands.get(id) === spec) state.commands.delete(id);
          }
        },
      });
    },
    registerCmExtensions(extensions) {
      state.extensions.push(...extensions);
      // v0.1 不支持运行时摘除已挂载的 CM 扩展（无 Compartment）。
      // disposable 仍然返回以便未来扩充，dispose 当前为 no-op。
      return trackDisposable({ dispose() {} });
    },
    registerMarkdownRenderer(rule) {
      const prev = state.renderers.get(rule.nodeType);
      if (prev) {
        console.warn(
          `[editor-core] markdown renderer for node "${rule.nodeType}" registered by plugin "${plugin.id}" overrides existing registration`,
        );
      }
      state.renderers.set(rule.nodeType, rule);
      const exts = Array.isArray(rule.extension) ? rule.extension : [rule.extension];
      state.extensions.push(...exts);
      return trackDisposable({
        dispose() {
          if (state.renderers.get(rule.nodeType) === rule) {
            state.renderers.delete(rule.nodeType);
          }
        },
      });
    },
    registerSlashItems(provider) {
      state.slashProviders.push(provider);
      return trackDisposable({
        dispose() {
          const idx = state.slashProviders.indexOf(provider);
          if (idx >= 0) state.slashProviders.splice(idx, 1);
        },
      });
    },
    registerWikilinkItems(provider) {
      state.wikilinkProviders.push(provider);
      return trackDisposable({
        dispose() {
          const idx = state.wikilinkProviders.indexOf(provider);
          if (idx >= 0) state.wikilinkProviders.splice(idx, 1);
        },
      });
    },
    registerSelectionToolbarActions(actions) {
      state.selectionToolbarActions.push(...actions);
      return trackDisposable({
        dispose() {
          for (const a of actions) {
            const idx = state.selectionToolbarActions.indexOf(a);
            if (idx >= 0) state.selectionToolbarActions.splice(idx, 1);
          }
        },
      });
    },
    on(event, listener) {
      let listeners = state.eventListeners.get(event);
      if (!listeners) {
        listeners = new Set();
        state.eventListeners.set(event, listeners);
      }
      listeners.add(listener);
      return trackDisposable({
        dispose() {
          const set = state.eventListeners.get(event);
          if (set) {
            set.delete(listener);
            if (set.size === 0) state.eventListeners.delete(event);
          }
        },
      });
    },
  };
}
