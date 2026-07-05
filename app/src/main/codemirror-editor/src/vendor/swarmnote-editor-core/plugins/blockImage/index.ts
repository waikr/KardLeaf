/**
 * Block image plugin (v0.1)
 *
 * 把 Markdown 图片 `![](url)` 渲染为内联或块级 widget。从 `ctx.host.resolveImage`
 * 读取宿主提供的 URL 解析器（例如 Tauri 的 workspace 相对路径 → `asset://`）。
 *
 * 同时 re-export `refreshBlockImagesEffect`：宿主在 P2P 媒体到达后需要 dispatch
 * 它来刷新已挂载 widget。Main 入口不再导出该 effect（v0.1 plugin-neutral 约定），
 * 仅在此 subpath 暴露。
 */
import {
  type BlockImageOptions,
  createBlockImageExtension,
} from './renderBlockImages';
import type { EditorPlugin, SlashItem } from '../../types';

export { refreshBlockImagesEffect } from './renderBlockImages';
export type { ImageResolver, BlockImageOptions } from './renderBlockImages';

export type BlockImagePluginOptions = Omit<BlockImageOptions, 'resolver'>;

export function blockImagePlugin(options?: BlockImagePluginOptions): EditorPlugin {
  return {
    id: 'blockImage',
    setup(ctx) {
      ctx.registerCmExtensions([
        createBlockImageExtension({
          ...options,
          resolver: ctx.host.resolveImage,
        }),
      ]);
      ctx.registerSlashItems({
        id: 'blockImage.builtin',
        provide: (): SlashItem[] => [
          {
            id: 'blockImage.insert',
            title: 'Image',
            description: 'Insert an image (paste / replace url after `![]()`)',
            icon: 'image',
            keywords: ['image', 'img', 'picture', '图片'],
            section: 'Insert',
            run: ({ view, range }) => {
              const insert = '![]()';
              view.dispatch({
                changes: { from: range.from, insert },
                selection: { anchor: range.from + 4 },
              });
            },
          },
        ],
      });
    },
  };
}
