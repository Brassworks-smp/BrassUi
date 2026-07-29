// A small, honest Kotlin/Groovy tokenizer. Not a full parser - it doesn't need to be. It walks the
// source once, longest-match-first, and tags each run so the CSS can colour it. Handles the things
// that actually show up in these snippets: line/block comments, triple- and single-quoted strings
// with `$`/`${}` interpolation, char literals, annotations, numbers, keywords, types, and calls.
//
// Why hand-rolled instead of highlight.js: the whole point is that the palette is the *brass* palette,
// tinted off the live accent, and I want the token classes to be mine so they retint with the theme
// like everything else. A 2 kB scanner beats a 40 kB dependency for six token kinds.

export type Tok = { t: string; v: string };

const KOTLIN_KW = new Set([
  "as", "break", "class", "continue", "do", "else", "false", "for", "fun", "if", "in", "interface",
  "is", "null", "object", "package", "return", "super", "this", "throw", "true", "try", "typealias",
  "typeof", "val", "var", "when", "while", "by", "catch", "constructor", "delegate", "dynamic",
  "field", "file", "finally", "get", "import", "init", "param", "property", "receiver", "set",
  "setparam", "value", "where", "abstract", "actual", "annotation", "companion", "const", "crossinline",
  "data", "enum", "expect", "external", "final", "infix", "inline", "inner", "internal", "lateinit",
  "noinline", "open", "operator", "out", "override", "private", "protected", "public", "reified",
  "sealed", "suspend", "tailrec", "vararg", "it", "def", "assert", "new", "extends", "implements",
]);

const SOFT_TYPES = new Set([
  "String", "Int", "Float", "Double", "Boolean", "Long", "Char", "Byte", "Short", "Unit", "Any",
  "List", "Map", "Set", "Pair", "Array", "Color", "Nothing",
]);

const RE = {
  ws: /^\s+/,
  lineComment: /^\/\/[^\n]*/,
  blockComment: /^\/\*[\s\S]*?\*\//,
  tripleString: /^"""[\s\S]*?"""/,
  string: /^"(?:\\.|[^"\\\n])*"/,
  char: /^'(?:\\.|[^'\\\n])'/,
  annotation: /^@[A-Za-z_][\w.]*/,
  number: /^0[xX][0-9a-fA-F_]+[uUlL]*|^\d[\d_]*\.?\d*(?:[eE][+-]?\d+)?[fFdDlLuU]*/,
  ident: /^[A-Za-z_]\w*/,
  op: /^(?:->|::|\?:|\.\.|==|!=|<=|>=|&&|\|\||[-+*/%=<>!&|^~?:.,;])/,
  bracket: /^[()[\]{}]/,
};

export function tokenize(src: string): Tok[] {
  const out: Tok[] = [];
  let s = src;
  const push = (t: string, v: string) => v && out.push({ t, v });

  while (s.length) {
    let m: RegExpMatchArray | null;

    if ((m = s.match(RE.ws))) {
      push("ws", m[0]);
    } else if ((m = s.match(RE.lineComment)) || (m = s.match(RE.blockComment))) {
      push("comment", m[0]);
    } else if ((m = s.match(RE.tripleString)) || (m = s.match(RE.string))) {
      // Break out `$id` / `${...}` interpolation so it reads as code inside the string.
      pushString(out, m[0]);
    } else if ((m = s.match(RE.char))) {
      push("string", m[0]);
    } else if ((m = s.match(RE.annotation))) {
      push("annotation", m[0]);
    } else if ((m = s.match(RE.number))) {
      push("number", m[0]);
    } else if ((m = s.match(RE.ident))) {
      const word = m[0];
      const rest = s.slice(word.length);
      if (KOTLIN_KW.has(word)) push("keyword", word);
      else if (/^\s*\(/.test(rest)) push("fn", word);
      else if (/^[A-Z]/.test(word) || SOFT_TYPES.has(word)) push("type", word);
      else push("ident", word);
    } else if ((m = s.match(RE.op))) {
      push("op", m[0]);
    } else if ((m = s.match(RE.bracket))) {
      push("bracket", m[0]);
    } else {
      push("plain", s[0]);
      m = [s[0]] as unknown as RegExpMatchArray;
    }
    s = s.slice(m[0].length);
  }
  return out;
}

function pushString(out: Tok[], str: string) {
  const parts = str.split(/(\$\{[^}]*\}|\$[A-Za-z_]\w*)/g);
  for (const p of parts) {
    if (!p) continue;
    if (p.startsWith("$")) out.push({ t: "interp", v: p });
    else out.push({ t: "string", v: p });
  }
}
