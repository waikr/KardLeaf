/**
 * Raw HTML plugin (v0.1)
 *
 * 通过 DOMPurify 渲染 Markdown 中嵌入的原生 HTML（`<img>` / `<details>` / `<u>`
 * 等）。`<img>` 标签的 src 走 `ctx.host.resolveImage`。
 *
 * DOMPurify 作为 editor-core 的直接 dependency 存在；启用此 plugin 时才会
 * 实际拉取相关代码到 bundle。
 */
import {
  type RawHtmlOptions,
  createRawHtmlExtension,
} from './renderRawHtml';
import type { EditorPlugin } from '../../types';

export type RawHtmlPluginOptions = Omit<RawHtmlOptions, 'resolver'>;

export function rawHtmlPlugin(options?: RawHtmlPluginOptions): EditorPlugin {
  return {
    id: 'rawHtml',
    setup(ctx) {
      ctx.registerCmExtensions([
        createRawHtmlExtension({
          ...options,
          resolver: ctx.host.resolveImage,
        }),
      ]);
    },
  };
}
