/**
 * 智能粘贴/拖放集成扩展
 *
 * **两大功能：**
 * 
 * 1. **pasteLinkPlugin（智能链接粘贴）**：
 *    当用户选中一段文本并粘贴 URL 时，自动将粘贴内容转换为 Markdown 链接格式
 *    `[选中文本](url)`。
 * 
 * 2. **dropFileHandler（文件拖放上传）**：
 *    通过可选的 `uploadFile` 回调处理文件拖放，在拖放位置插入 `![alt](url)`。
 *    如果未提供回调，则阻止默认行为并静默忽略拖放。
 *
 * **设计要点：**
 * 插件仅响应 `tr.isUserEvent("input.paste")`，因此程序化分发的 URL 内容不会被转换。
 */
import type { Extension } from '@codemirror/state';
import { EditorView, ViewPlugin, type ViewUpdate } from '@codemirror/view';

/** URL 正则表达式 —— 匹配 http/https 协议 */
const URL_REGEX = /^https?:\/\/[-a-zA-Z0-9@:%._+~#=]{1,256}([-a-zA-Z0-9()@:%_+.~#?&//=]*)$/;

/** 文件上传结果接口 */
export interface UploadFileResult {
  /** 上传后的 URL */
  url: string;
  /** 可选的 alt 文本 */
  alt?: string;
}

/** 文件上传处理器类型 */
export type UploadFileHandler = (file: File) => Promise<UploadFileResult>;

/** 智能粘贴选项 */
export interface SmartPasteOptions {
  /** 文件上传回调（可选） */
  uploadFile?: UploadFileHandler;
}

/**
 * 智能链接粘贴插件
 * 
 * **工作流程：**
 * 1. 监听所有事务，筛选出用户粘贴事件
 * 2. 检查是否有非空选区
 * 3. 提取粘贴的文本内容
 * 4. 验证是否为 URL
 * 5. 如果是 URL，将选中文本和 URL 组合成 Markdown 链接格式
 * 6. 延迟一帧执行替换（等待原始粘贴事务完成）
 */
const pasteLinkPlugin = ViewPlugin.fromClass(
  class {
    update(update: ViewUpdate): void {
      for (const tr of update.transactions) {
        // 仅响应用户粘贴事件
        if (!tr.isUserEvent('input.paste')) continue;

        // 获取粘贴前的主选区
        const selection = update.startState.selection.main;
        // 如果没有选区，跳过
        if (selection.empty) continue;

        // 提取粘贴的内容
        const pastedParts: string[] = [];
        let from = 0;
        let to = 0;
        tr.changes.iterChanges((fromA, _toA, _fromB, toB, inserted) => {
          pastedParts.push(inserted.sliceString(0));
          from = fromA;
          to = toB;
        });

        const pasted = pastedParts.join('').trim();
        // 如果不是 URL，跳过
        if (!URL_REGEX.test(pasted)) continue;

        // 获取选中的文本
        const selectedText = update.startState.sliceDoc(selection.from, selection.to);

        // 延迟一帧执行替换，确保原始粘贴事务已完成
        setTimeout(() => {
          update.view.dispatch({
            changes: { from, to, insert: `[${selectedText}](${pasted})` },
          });
        }, 0);
      }
    }
  },
);

/**
 * 构建文件拖放处理器
 * 
 * **工作流程：**
 * 1. 监听 drop 事件
 * 2. 提取拖放的文件列表
 * 3. 计算拖放位置的文档偏移量
 * 4. 依次上传每个文件（保证顺序稳定）
 * 5. 在对应位置插入 Markdown 图片语法
 * 6. 单个文件失败不影响其他文件的处理
 * 
 * @param uploadFile - 文件上传回调（可选）
 * @returns DOM 事件处理器扩展
 */
function buildDropHandler(uploadFile: UploadFileHandler | undefined) {
  return EditorView.domEventHandlers({
    drop(event, view) {
      // 获取拖放的文件列表
      const files = event.dataTransfer?.files;
      if (!files || files.length === 0) return false;

      // 阻止默认拖放行为
      event.preventDefault();
      // 如果没有提供上传回调，静默忽略
      if (!uploadFile) return true;

      // 计算拖放位置的文档偏移量（优先使用坐标，否则使用光标位置）
      const dropPos =
        view.posAtCoords({ x: event.clientX, y: event.clientY }) ??
        view.state.selection.main.head;

      // 依次处理文件（保证多文件拖放的顺序稳定）
      void (async () => {
        let insertPos = dropPos;
        for (const file of Array.from(files)) {
          try {
            // 上传文件
            const { url, alt } = await uploadFile(file);
            // 构建 Markdown 图片语法
            const insertion = `![${alt ?? ''}](${url})`;
            // 插入到文档
            view.dispatch({
              changes: { from: insertPos, insert: insertion },
            });
            // 更新下一个插入位置
            insertPos += insertion.length;
          } catch {
            // 吞掉单个文件的失败，继续处理剩余文件
          }
        }
      })();

      return true;
    },
  });
}

/**
 * 创建智能粘贴扩展
 * 
 * **功能：**
 * 1. 智能链接粘贴：选中文字 + 粘贴 URL → 自动转换为 Markdown 链接
 * 2. 文件拖放上传：拖放文件 → 自动上传并插入 Markdown 图片
 * 
 * @param options - 配置选项
 * @returns CodeMirror 扩展数组
 */
export function createSmartPasteExtension(options: SmartPasteOptions = {}): Extension {
  return [pasteLinkPlugin, buildDropHandler(options.uploadFile)];
}
