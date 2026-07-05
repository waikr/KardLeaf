import type { EditorSelectionRange } from '../types';

export function createSelectionRange(
  anchor: number,
  head: number,
): EditorSelectionRange {
  return {
    anchor,
    head,
    from: Math.min(anchor, head),
    to: Math.max(anchor, head),
  };
}
