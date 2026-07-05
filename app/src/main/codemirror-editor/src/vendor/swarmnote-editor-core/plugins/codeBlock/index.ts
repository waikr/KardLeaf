/**
 * Code block plugin (v0.1)
 *
 * 把 fenced 代码块按 `mode` 渲染为不同形态：
 * - `inline`（默认）：fence 标记折叠为头/尾 widget，代码保持在 CM 文档流中
 * - `auto`：光标不在块内时整块折叠为只读卡片；光标进入显示源码
 * - `toggle`：始终显示卡片，通过按钮手动切换源码
 *
 * v0.1 移除了 'off' 选项语义——若 plugin 未启用，等价于 'off'。
 */
import {
  type BlockCodeOptions,
  createBlockCodeExtension,
} from './renderBlockCode';
import type { EditorPlugin, SlashItem } from '../../types';

/** 代码块渲染模式（非 plugin-internal 形态，对应 BlockCodeOptions.mode） */
export type CodeBlockPluginMode = NonNullable<BlockCodeOptions['mode']>;

export interface CodeBlockPluginOptions {
  /** 渲染模式，默认 'inline' */
  mode?: CodeBlockPluginMode;
}

export function codeBlockPlugin(options?: CodeBlockPluginOptions): EditorPlugin {
  return {
    id: 'codeBlock',
    setup(ctx) {
      ctx.registerCmExtensions([
        createBlockCodeExtension({ mode: options?.mode ?? 'inline' }),
      ]);
      ctx.registerSlashItems({
        id: 'codeBlock.builtin',
        provide: (): SlashItem[] => [
          {
            id: 'codeBlock.insert',
            title: 'Code block',
            description: 'Insert a fenced code block',
            icon: 'square-code',
            keywords: ['code', 'block', '代码块', 'fenced'],
            section: 'Insert',
            commandId: 'insertCodeBlock',
          },
        ],
      });
    },
  };
}
