export function renderedTableCellOffsetToSource(source: string, renderedOffset: number) {
  const target = Math.max(0, renderedOffset);
  let sourceOffset = 0;
  let visibleOffset = 0;
  while (sourceOffset < source.length && visibleOffset < target) {
    if (source[sourceOffset] === '\\' && source[sourceOffset + 1] === '|') sourceOffset += 1;
    sourceOffset += 1;
    visibleOffset += 1;
  }
  return sourceOffset;
}
