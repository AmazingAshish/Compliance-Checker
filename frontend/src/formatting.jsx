// Lightweight, dependency-free formatting for LLM reasoning text.
//
// Gemini's reasoning often comes back as a single unbroken string using
// markdown-style conventions - "**bold**" spans and inline numbered points
// like "1. **Missing Reference Number (KYC-101):** ...". Rendering that as
// one flat paragraph (the original UI) makes multi-issue reasoning hard to
// scan. This module does two things, both via safe React-node construction
// (never dangerouslySetInnerHTML, since this is LLM output and treating it
// as trusted HTML would be a prompt-injection-shaped XSS risk):
//   1. splits "N. **Title:** body" runs into a structured list
//   2. renders "**bold**" spans and "POLICY-123"-shaped codes distinctly

const POLICY_CODE = /\b[A-Z]{2,6}-\d{2,4}\b/g;
const BOLD = /\*\*([^*]+)\*\*/g;

/** Turn a plain string into React nodes, rendering **bold** and POLICY-CODE spans. */
export function renderInline(text, keyPrefix = "t") {
  if (!text) return null;
  const nodes = [];
  let lastIndex = 0;
  let key = 0;

  const pushPlain = (segment, from) => {
    if (!segment) return;
    // within a plain segment, still highlight policy codes
    let idx = 0;
    let m;
    POLICY_CODE.lastIndex = 0;
    while ((m = POLICY_CODE.exec(segment))) {
      if (m.index > idx) nodes.push(segment.slice(idx, m.index));
      nodes.push(
        <code className="policy-chip" key={`${keyPrefix}-pc-${from}-${key++}`}>
          {m[0]}
        </code>
      );
      idx = m.index + m[0].length;
    }
    if (idx < segment.length) nodes.push(segment.slice(idx));
  };

  BOLD.lastIndex = 0;
  let match;
  while ((match = BOLD.exec(text))) {
    pushPlain(text.slice(lastIndex, match.index), lastIndex);
    nodes.push(<strong key={`${keyPrefix}-b-${key++}`}>{match[1]}</strong>);
    lastIndex = match.index + match[0].length;
  }
  pushPlain(text.slice(lastIndex), lastIndex);

  return nodes;
}

const LIST_ITEM = /(\d+)\.\s+\*\*([^*]+?)\*\*:?\s*/g;

/**
 * Detect an inline numbered-list pattern ("1. **Title:** body 2. **Title:** body...")
 * and split it into { intro, items: [{ number, title, body }] }.
 * Returns null when the text doesn't contain that pattern, so callers can
 * fall back to plain paragraph rendering.
 */
export function parseReasoning(text) {
  if (!text) return null;
  LIST_ITEM.lastIndex = 0;
  const starts = [];
  let m;
  while ((m = LIST_ITEM.exec(text))) {
    starts.push({ index: m.index, headerEnd: m.index + m[0].length, number: m[1], title: m[2] });
  }
  if (starts.length < 2) return null; // need at least 2 items to call it a "list"

  const intro = text.slice(0, starts[0].index).trim();
  const items = starts.map((s, i) => {
    const end = i + 1 < starts.length ? starts[i + 1].index : text.length;
    return { number: s.number, title: s.title.trim(), body: text.slice(s.headerEnd, end).trim() };
  });
  return { intro, items };
}
