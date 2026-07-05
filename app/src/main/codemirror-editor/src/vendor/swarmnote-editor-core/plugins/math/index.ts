/**
 * Math plugin (v0.1)
 *
 * 把 `$$...$$` 块级公式渲染为 KaTeX。基于 `createBlockMathExtension`。
 *
 * **v0.1 注意**：lezer markdown 扩展（识别 `$...$` / `$$...$$` 语法节点）
 * 目前仍由 `createEditor` 中的 `markdownMathExtension` 在 features 收敛后
 * 通过 plugin 探测自动启用——这是 v0.1 的临时折中，runtime 在 Section 7
 * 完成后从 `settings.features.mathRendering` 切换为「plugins[] 含 math」探测。
 */
import { createBlockMathExtension } from './renderBlockMath';
import type { EditorPlugin, SlashItem } from '../../types';

export interface MathPluginOptions {
  // v0.1 暂无可配置项；保留对象以便后续无破坏添加
  // 例如：delimiters / errorColor / macros 等
}

/**
 * 创建 math plugin。
 *
 * 使用方式：
 * ```ts
 * import { mathPlugin } from '@swarmnote/editor-core/plugins/math';
 * createEditor(parent, { ..., plugins: [mathPlugin()] });
 * ```
 */
export function mathPlugin(_options?: MathPluginOptions): EditorPlugin {
  return {
    id: 'math',
    setup(ctx) {
      ctx.registerCmExtensions([createBlockMathExtension()]);
      ctx.registerSlashItems({
        id: 'math.builtin',
        provide: (): SlashItem[] => [
          {
            id: 'math.insertBlock',
            title: 'Math block',
            description: 'Insert a $$...$$ math block',
            icon: 'sigma',
            keywords: ['math', 'equation', 'latex', '数学', '公式'],
            section: 'Insert',
            run: ({ view, range }) => {
              const insert = '$$\n\n$$\n';
              view.dispatch({
                changes: { from: range.from, insert },
                selection: { anchor: range.from + 3 },
              });
            },
          },
        ],
      });
    },
  };
}
