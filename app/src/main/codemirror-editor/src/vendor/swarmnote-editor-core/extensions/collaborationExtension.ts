import type { Extension } from '@codemirror/state';
import { yCollab } from 'y-codemirror.next';
import * as Y from 'yjs';
import type { EditorCollaborationConfig } from '../types';

/**
 * 创建协作编辑扩展
 * 
 * **功能：**
 * 通过 y-codemirror.next 将 Y.Text 和可选的 Awareness 集成到 CodeMirror 中。
 * 
 * **协作特性：**
 * 当提供 `collaboration.awareness` 时，y-codemirror.next 会使用每个远程 awareness
 * 状态的 `user` 字段 (`{ user: { name, color, ... } }`) 渲染远程光标和姓名标签。
 * 调用方负责 Awareness 实例的生命周期和网络传播。
 * 
 * **技术细节：**
 * - 使用 Y.Doc 作为协同编辑的数据模型
 * - 通过 Y.Text 片段同步文档内容
 * - Awareness 提供用户状态（光标位置、用户名、颜色等）
 * 
 * @param collaboration - 协作配置
 * @returns CodeMirror 扩展数组
 */
export function createCollaborationExtension(
  collaboration?: EditorCollaborationConfig,
): Extension[] {
  // 如果没有提供协作配置，返回空数组（不启用协作）
  if (!collaboration) {
    return [];
  }

  // 从配置中获取 Y.Doc 实例
  const ydoc = collaboration.ydoc as Y.Doc;
  // 获取 Y.Text 片段（默认名为 'document'）
  const ytext = ydoc.getText(collaboration.fragmentName ?? 'document');

  // y-codemirror.next 接受 Awareness | null。
  // 我们在公共配置中将其类型设为 unknown，
  // 以避免将 y-protocols 拉入编辑器的依赖树。
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  const awareness = (collaboration.awareness ?? null) as any;

  // 返回 y-codemirror.next 的协作扩展
  return [yCollab(ytext, awareness)];
}
