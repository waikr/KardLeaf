/**
 * Smart paste plugin (v0.1)
 *
 * 处理粘贴 URL 自动转链接，以及拖拽 / 粘贴文件经 `ctx.host.uploadFile` 上传后
 * 插入 Markdown `![alt](url)`。Host 未提供 `uploadFile` 时，文件 drag/drop
 * 被静默 preventDefault。
 */
import {
  type SmartPasteOptions,
  createSmartPasteExtension,
} from './smartPasteExtension';
import type { EditorPlugin } from '../../types';

export type SmartPastePluginOptions = Omit<SmartPasteOptions, 'uploadFile'>;

export function smartPastePlugin(options?: SmartPastePluginOptions): EditorPlugin {
  return {
    id: 'smartPaste',
    setup(ctx) {
      ctx.registerCmExtensions([
        createSmartPasteExtension({
          ...options,
          uploadFile: ctx.host.uploadFile,
        }),
      ]);
    },
  };
}
