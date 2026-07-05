/**
 * 内联渲染主题样式
 * 
 * **包含的样式：**
 * - 复选框 widget：指针光标、垂直居中、强调色
 * - Bullet widget：固定宽度、居中对齐、较小字体
 * - 分割线 widget：无边框、顶部边框、上下边距
 */
import { EditorView } from '@codemirror/view';

/**
 * 内联渲染基础主题
 * 
 * **样式说明：**
 * 1. .cm-checkbox-widget：复选框交互样式
 * 2. .cm-bullet-widget：列表标记显示样式
 * 3. .cm-divider-widget：水平分割线样式
 */
export const inlineRenderingTheme = EditorView.theme({
  '.cm-checkbox-widget': {
    cursor: 'pointer',
    verticalAlign: 'middle',
    marginRight: '6px',
    width: '15px',
    height: '15px',
    accentColor: 'hsl(40, 72%, 46%)',
  },
  '.cm-bullet-widget': {
    display: 'inline-block',
    width: '1.4em',
    textAlign: 'center',
    color: 'inherit',
    userSelect: 'none',
    fontSize: '0.65em',
    verticalAlign: 'middle',
  },
  '.cm-divider-widget': {
    border: 'none',
    borderTop: '1px solid rgba(127, 127, 127, 0.35)',
    margin: '12px 0',
  },
});
