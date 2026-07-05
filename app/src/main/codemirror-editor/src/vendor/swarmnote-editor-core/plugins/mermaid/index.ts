/**
 * Mermaid plugin (v0.1)
 *
 * 把 ```mermaid fenced 代码块渲染为 SVG 卡片。基于 `createBlockMermaidExtension`。
 */
import {
  type BlockMermaidOptions,
  createBlockMermaidExtension,
} from './renderBlockMermaid';
import type { EditorPlugin, SlashItem } from '../../types';

export type MermaidPluginOptions = BlockMermaidOptions;

export function mermaidPlugin(options?: MermaidPluginOptions): EditorPlugin {
  return {
    id: 'mermaid',
    setup(ctx) {
      ctx.registerCmExtensions([createBlockMermaidExtension(options)]);
      ctx.registerSlashItems({
        id: 'mermaid.builtin',
        provide: (): SlashItem[] => [
          {
            id: 'mermaid.insert',
            title: 'Mermaid diagram',
            description: 'Insert a mermaid code block (flowchart / sequence / class…)',
            icon: '📊',
            keywords: ['mermaid', 'diagram', 'chart', '图表'],
            section: 'Insert',
            run: ({ view, range }) => {
              const insert = '```mermaid\n\n```\n';
              view.dispatch({
                changes: { from: range.from, insert },
                selection: { anchor: range.from + 11 },
              });
            },
          },
        ],
      });
    },
  };
}
