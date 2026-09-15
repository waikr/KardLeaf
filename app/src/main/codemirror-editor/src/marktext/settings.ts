/*! MarkText settings normalization adapted for KardLeaf. Copyright (c) 2026 Renakoni. MIT; see LICENSE. */
export const commandLabels: Record<string, string> = {
  toggleBold: '加粗', toggleItalic: '斜体', toggleUnderline: '下划线', toggleStrike: '删除线',
  toggleCode: '行内代码', toggleBlockquote: '引用', toggleOrderedList: '有序列表',
  toggleUnorderedList: '无序列表', toggleCheckList: '待办',
};
export function normalizeSettings(value: any) {
  return { enabled: value?.enabled !== false && value?.enabled !== 'false',
    rows: value?.rows === 2 || value?.rows === '2' ? 2 : 1,
    commands: [...new Set<string>((Array.isArray(value?.commands) ? value.commands : [])
      .filter((id: unknown) => typeof id === 'string' && Object.prototype.hasOwnProperty.call(commandLabels, id)))].slice(0, 12) };
}
