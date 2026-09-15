type VisibleSegment = {
  sourceFrom: number;
  sourceTo: number;
  visible: string;
  contentFrom?: number;
  contentTo?: number;
};

const SAFE_URL_RE = /^(https?:\/\/|mailto:|\/|#|[^:]+$)/i;

function pushSegment(
  segments: VisibleSegment[],
  sourceFrom: number,
  sourceTo: number,
  visible: string,
  contentFrom?: number,
  contentTo?: number,
) {
  segments.push({ sourceFrom, sourceTo, visible, contentFrom, contentTo });
}

/**
 * Build the visible-text to Markdown-source map used by table cell ranges.
 * Formatting is a single visible segment, so selecting all of `**bold**`
 * includes its markers while a caret inside it still maps to the text.
 */
function visibleSegments(source: string): VisibleSegment[] {
  const segments: VisibleSegment[] = [];
  for (let i = 0; i < source.length;) {
    const rest = source.slice(i);
    const br = rest.match(/^<br\s*\/?\s*>/i);
    if (br) {
      pushSegment(segments, i, i + br[0].length, '\n');
      i += br[0].length;
      continue;
    }

    // Table parsing removes the escape before rendering, so it must remain a
    // two-code-unit source segment even though the DOM contains one pipe.
    if (rest.startsWith('\\|')) {
      pushSegment(segments, i, i + 2, '|');
      i += 2;
      continue;
    }

    const code = rest.match(/^`([^`\n]+)`/);
    if (code) {
      pushSegment(segments, i, i + code[0].length, code[1], i + 1, i + 1 + code[1].length);
      i += code[0].length;
      continue;
    }

    const link = rest.match(/^\[([^\]\n]+)\]\(([^\s)]+)\)/);
    if (link && SAFE_URL_RE.test(link[2].trim())) {
      pushSegment(segments, i, i + link[0].length, link[1], i + 1, i + 1 + link[1].length);
      i += link[0].length;
      continue;
    }

    const math = rest.match(/^\$([^$\n]+)\$/);
    if (math) {
      pushSegment(segments, i, i + math[0].length, math[0], i + 1, i + 1 + math[1].length);
      i += math[0].length;
      continue;
    }

    const formatting = rest.match(/^(\*{3}|_{3}|\*{2}|_{2}|~~|==|\*|_)([^\n]+?)\1/);
    if (formatting && formatting[2][0] !== ' ' && formatting[2].at(-1) !== ' ') {
      const open = formatting[1];
      const content = formatting[2];
      pushSegment(
        segments,
        i,
        i + formatting[0].length,
        content,
        i + open.length,
        i + open.length + content.length,
      );
      i += formatting[0].length;
      continue;
    }

    pushSegment(segments, i, i + 1, source[i]);
    i += 1;
  }
  return segments;
}

export function renderedTableCellOffsetToSource(source: string, renderedOffset: number) {
  const target = Math.max(0, renderedOffset);
  let visibleOffset = 0;
  for (const segment of visibleSegments(source)) {
    const end = visibleOffset + segment.visible.length;
    if (target < end) {
      if (segment.contentFrom == null || segment.contentTo == null) return segment.sourceFrom;
      return Math.min(segment.contentTo, segment.contentFrom + target - visibleOffset);
    }
    if (target === end) return segment.sourceTo;
    visibleOffset = end;
  }
  return source.length;
}
