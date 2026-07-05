/**
 * Admonition plugin (v0.1)
 *
 * 渲染 GFM `> [!NOTE]` 与 Obsidian `> **NOTE**` 双语法的提示块。
 * 默认启用全部 GFM + Obsidian 预设类型，调用方可通过 options.types 覆盖。
 */
import { type AdmonitionOptions, createAdmonitionExtension } from './admonitionExtension';
import { GFM_TYPES, OBSIDIAN_TYPES } from './presets';
import type { EditorPlugin, SlashItem } from '../../types';

export type AdmonitionPluginOptions = AdmonitionOptions;

export function admonitionPlugin(options?: AdmonitionPluginOptions): EditorPlugin {
  return {
    id: 'admonition',
    setup(ctx) {
      const merged: AdmonitionOptions = {
        ...options,
        types: options?.types ?? { ...GFM_TYPES, ...OBSIDIAN_TYPES },
      };
      ctx.registerCmExtensions([createAdmonitionExtension(merged)]);

      const insertAdmonition = (type: string) => ({ view, range }: {
        view: import('@codemirror/view').EditorView;
        range: { from: number; to: number };
      }) => {
        const insert = `> [!${type}]\n> `;
        view.dispatch({
          changes: { from: range.from, insert },
          selection: { anchor: range.from + insert.length },
        });
      };

      ctx.registerSlashItems({
        id: 'admonition.builtin',
        provide: (): SlashItem[] => [
          {
            id: 'admonition.note',
            title: 'Note',
            description: 'Insert a > [!note] admonition',
            icon: 'sticky-note',
            keywords: ['note', 'callout', '注释', 'admonition'],
            section: 'Callout',
            run: insertAdmonition('note'),
          },
          {
            id: 'admonition.tip',
            title: 'Tip',
            description: 'Insert a > [!tip] admonition',
            icon: 'lightbulb',
            keywords: ['tip', 'hint', '提示'],
            section: 'Callout',
            run: insertAdmonition('tip'),
          },
          {
            id: 'admonition.warning',
            title: 'Warning',
            description: 'Insert a > [!warning] admonition',
            icon: 'triangle-alert',
            keywords: ['warning', 'warn', '警告'],
            section: 'Callout',
            run: insertAdmonition('warning'),
          },
          {
            id: 'admonition.important',
            title: 'Important',
            description: 'Insert a > [!important] admonition',
            icon: 'circle-alert',
            keywords: ['important', '重要'],
            section: 'Callout',
            run: insertAdmonition('important'),
          },
          {
            id: 'admonition.caution',
            title: 'Caution',
            description: 'Insert a > [!caution] admonition',
            icon: 'octagon-alert',
            keywords: ['caution', 'danger', '危险'],
            section: 'Callout',
            run: insertAdmonition('caution'),
          },
        ],
      });
    },
  };
}
