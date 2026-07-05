import { Decoration } from '@codemirror/view';
import type { InlineRenderingSpec } from './types';

const hiddenDecoration = Decoration.replace({});

/**
 * 隐藏反斜杠转义中的反斜杠字符。
 * Escape 节点跨越反斜杠和被转义字符（如 `\*`），只隐藏反斜杠。
 */
export const replaceBackslashEscapes: InlineRenderingSpec = {
  nodeNames: ['Escape'],
  extension: {
    createDecoration() {
      return hiddenDecoration;
    },
    getDecorationRange(node) {
      // Escape node = `\*` → only hide the `\` (from → from+1)
      return [node.from, node.from + 1];
    },
  },
};
