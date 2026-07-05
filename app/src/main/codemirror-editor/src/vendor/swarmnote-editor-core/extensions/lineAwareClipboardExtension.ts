/**
 * 行感知剪贴板扩展
 *
 * **功能：**
 * 当没有文本选区时，copy/cut 操作会复制/剪切整行（包括换行符）。
 * 
 * **设计目标：**
 * 与 VS Code、Joplin 等主流编辑器的行为保持一致，提升用户体验。
 * 
 * **使用场景：**
 * - 光标在空白行 → 复制空行
 * - 光标在有内容的行 → 复制整行内容 + 换行符
 * - 有选区时 → 使用默认的选区复制逻辑
 */
import type { Extension } from '@codemirror/state';
import { EditorView } from '@codemirror/view';

/**
 * 创建行感知剪贴板扩展
 * 
 * **工作流程：**
 * 
 * **Copy（复制）：**
 * 1. 检查是否有选区，如果有则使用默认行为
 * 2. 获取光标所在的行
 * 3. 阻止默认复制行为
 * 4. 将整行文本 + 换行符写入剪贴板
 * 
 * **Cut（剪切）：**
 * 1. 检查是否有选区，如果有则使用默认行为
 * 2. 获取光标所在的行
 * 3. 阻止默认剪切行为
 * 4. 将整行文本 + 换行符写入剪贴板
 * 5. 从文档中删除该行（包括换行符）
 * 6. 标记为用户删除操作
 * 
 * @returns DOM 事件处理器扩展
 */
export function createLineAwareClipboardExtension(): Extension {
  return EditorView.domEventHandlers({
    /**
     * 处理复制事件
     * 
     * @param event - 剪贴板事件
     * @param view - 编辑器视图
     * @returns 是否已处理
     */
    copy(event, view) {
      // 获取主选区
      const sel = view.state.selection.main;
      // 如果有选区，使用默认行为
      if (!sel.empty) return false;

      // 获取光标所在的行
      const line = view.state.doc.lineAt(sel.anchor);
      // 阻止默认复制行为
      event.preventDefault();
      // 将整行文本 + 换行符写入剪贴板
      event.clipboardData?.setData('text/plain', `${line.text}\n`);
      return true;
    },
    /**
     * 处理剪切事件
     * 
     * @param event - 剪贴板事件
     * @param view - 编辑器视图
     * @returns 是否已处理
     */
    cut(event, view) {
      // 获取主选区
      const sel = view.state.selection.main;
      // 如果有选区，使用默认行为
      if (!sel.empty) return false;

      // 获取光标所在的行
      const line = view.state.doc.lineAt(sel.anchor);
      // 阻止默认剪切行为
      event.preventDefault();
      // 将整行文本 + 换行符写入剪贴板
      event.clipboardData?.setData('text/plain', `${line.text}\n`);

      // 从文档中删除该行（包括换行符）
      view.dispatch({
        changes: {
          from: line.from,  // 行起始位置
          to: Math.min(line.to + 1, view.state.doc.length),  // 行结束位置 + 1（包含换行符）
          insert: '',  // 替换为空字符串（即删除）
        },
        userEvent: 'delete.cut',  // 标记为用户剪切操作
      });
      return true;
    },
  });
}
