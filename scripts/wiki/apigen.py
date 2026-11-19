import re, os, sys, glob, collections

ROOT = sys.argv[1]
OUT = sys.argv[2]

DECL = re.compile(
    r'^(?:@\w+(?:\([^)]*\))?\s+)*'
    r'(?P<mods>(?:(?:public|private|internal|protected|open|abstract|sealed|data|enum|inner|override|inline|const|lateinit|operator|infix|suspend|tailrec|external|value|annotation|fun(?= interface))\s+)*)'
    r'(?P<kind>class|interface|object|companion object|fun interface|fun|val|var|typealias)\b(?P<rest>.*)$'
)


def strip_code(line, state):
    out = []
    i = 0
    n = len(line)
    while i < n:
        if state['raw']:
            j = line.find('"""', i)
            if j < 0:
                return ''.join(out)
            state['raw'] = False
            i = j + 3
            out.append('""')
            continue
        if state['block']:
            j = line.find('*/', i)
            if j < 0:
                return ''.join(out)
            state['block'] = False
            i = j + 2
            continue
        c = line[i]
        if line.startswith('"""', i):
            state['raw'] = True
            i += 3
            continue
        if c == '"':
            j = i + 1
            while j < n:
                if line[j] == '\\':
                    j += 2
                    continue
                if line[j] == '"':
                    break
                j += 1
            out.append('""')
            i = j + 1
            continue
        if c == "'":
            m = re.match(r"'(\\.|[^\\'])'", line[i:])
            if m:
                out.append("' '")
                i += len(m.group(0))
                continue
        if line.startswith('//', i):
            break
        if line.startswith('/*', i):
            state['block'] = True
            i += 2
            continue
        out.append(c)
        i += 1
    return ''.join(out)


def clean_doc(lines):
    text = []
    for l in lines:
        l = l.strip()
        l = re.sub(r'^/\*\*\s?', '', l)
        l = re.sub(r'\*/$', '', l)
        l = re.sub(r'^\*\s?', '', l)
        text.append(l.rstrip())
    s = '\n'.join(text).strip()
    s = re.sub(r'\[([A-Za-z_][\w.]*)\]', r'`\1`', s)
    s = re.sub(r'`([\w.]+)\.(\w+)`', lambda m: '`' + (m.group(2) if m.group(1).startswith('net.') else m.group(1) + '.' + m.group(2)) + '`', s)
    s = re.sub(r'^#+\s*', '**', s, flags=re.M)
    s = re.sub(r'^\*\*(.+)$', r'**\1**', s, flags=re.M)
    s = re.sub(r'^\*\*\*\*(.*)\*\*$', r'**\1**', s, flags=re.M)
    return s


def first_para(s):
    return s.split('\n\n')[0].replace('\n', ' ').strip()


def collect_signature(raw_lines, start, text_lines=None):
    sig = ''
    depth = 0
    i = start
    while i < len(raw_lines):
        line = raw_lines[i].replace('->', '\u2192').replace('>=', '\u2265').replace('<=', '\u2264')
        for ch in line:
            if ch in '(<[':
                depth += 1
            elif ch in ')>]':
                depth -= 1
        shown = text_lines[i] if text_lines else raw_lines[i]
        shown = re.sub(r'/\*.*?\*/', '', shown)
        shown = re.sub(r'\s//\s.*$', '', shown)
        shown = shown.replace('->', '\u2192').replace('>=', '\u2265').replace('<=', '\u2264')
        sig += (' ' if sig else '') + shown.strip()
        if depth <= 0 and not line.rstrip().endswith((',', ':')):
            break
        i += 1
    sig = re.sub(r'\s+', ' ', sig)
    out = ''
    d = 0
    inq = False
    for idx, ch in enumerate(sig):
        if ch == '"' and (idx == 0 or sig[idx - 1] != '\\'):
            inq = not inq
        if inq:
            out += ch
            continue
        if ch in '(<[':
            d += 1
        elif ch in ')>]':
            d -= 1
        if d == 0 and (ch == '{' or (ch == '=' and sig[idx - 1] not in '!<>=' and (idx + 1 >= len(sig) or sig[idx + 1] not in '=>'))):
            break
        out += ch
    out = out.replace('\u2192', '->').replace('\u2265', '>=').replace('\u2264', '<=')
    out = out.strip().rstrip(',')
    out = re.sub(r'\b(private|protected) (val|var) ', '', out)
    out = re.sub(r'@JvmOverloads |@JvmStatic |@Volatile |@Synchronized ', '', out)
    out = re.sub(r'net\.swzo\.brass\.ui\.(?:[a-z]+\.)*([A-Z])', r'\1', out)
    out = re.sub(r'gg\.essential\.(?:[a-z]+\.)*([A-Z])', r'\1', out)
    out = re.sub(r'/\*\*.*?\*/', '', out)
    out = re.sub(r'\s+,', ',', out)
    out = re.sub(r'\(\s+', '(', out)
    out = re.sub(r',\s*\)', ')', out)
    return out, i


def parse(path):
    raw = open(path, encoding='utf-8').read().split('\n')
    pkg = None
    state = {'raw': False, 'block': False}
    code = []
    for line in raw:
        code.append(strip_code(line, state))
    items = []
    stack = []  # (container, body_depth)
    depth = 0
    doc = []
    in_doc = False
    i = 0
    while i < len(raw):
        r = raw[i]
        s = r.strip()
        if s.startswith('package '):
            pkg = s.split()[1]
        if s.startswith('/**'):
            in_doc = True
            doc = []
        if in_doc:
            doc.append(r)
            if '*/' in s:
                in_doc = False
            i += 1
            continue
        c = code[i]
        cs = c.strip()
        top = stack[-1] if stack else None
        at_level = (top is None and depth == 0) or (top is not None and depth == top[1])
        m = DECL.match(cs) if cs and at_level else None
        consumed = i
        if m and not re.search(r'\b(private|internal|protected)\b', m.group('mods')) and 'override' not in m.group('mods'):
            kind = m.group('kind')
            sig, consumed = collect_signature(code, i, raw)
            consumed = min(consumed, len(code) - 1)
            d = clean_doc(doc) if doc else ''
            sig = re.sub(r'(class \w+(?:<[^>]*>)?) constructor\(', r'\1(', sig)
            if kind in ('val', 'var') and not re.search(r'(?:val|var)\s+[\w.<>]+\s*:', sig):
                src = re.sub(r'\s//\s.*$', '', raw[i])
                init = src.split('=', 1)[1].strip() if '=' in src else ''
                if init and len(init) <= 48 and '{' not in init:
                    sig = sig + ' = ' + init
            entry = {'kind': kind, 'sig': sig, 'doc': d, 'members': [], 'enum': []}
            if kind in ('class', 'interface', 'object', 'companion object', 'fun interface'):
                nm = re.search(r'(?:class|interface|object)\s+(\w+)', sig)
                entry['name'] = nm.group(1) if nm else 'Companion'
                if top is None:
                    items.append(entry)
                else:
                    top[0]['members'].append(entry)
                if 'enum' in m.group('mods'):
                    entry['is_enum'] = True
                    body = code[i].split('{', 1)[1] if '{' in code[i] else ''
                    body = body.split(';')[0].split('}')[0]
                    entry['enum'] = [v for v in re.findall(r'(?:^|,)\s*([A-Z][A-Z0-9_]*)', body)]
                    if '}' in code[i] or ';' in code[i]:
                        entry['enum_done'] = True
                pending_container = entry
            else:
                nm = re.search(r'(?:fun|val|var|typealias)\s+(?:<[^>]*>\s*)?(?:[\w.<>?, ]+\.)?(`?\w+`?)', sig)
                entry['name'] = nm.group(1) if nm else sig
                if top is None:
                    items.append(entry)
                else:
                    top[0]['members'].append(entry)
                pending_container = None
        else:
            pending_container = None
        if top is not None and top[0].get('is_enum') and depth == top[1] and not top[0].get('enum_done'):
            if cs and not DECL.match(cs):
                names = re.findall(r'^\s*([A-Z][A-Z0-9_]*)\b', c)
                for nm2 in re.findall(r'(?:^|,)\s*([A-Z][A-Z0-9_]*)\s*(?=[,(;{]|$)', cs):
                    if nm2 not in top[0]['enum']:
                        top[0]['enum'].append(nm2)
                if ';' in cs:
                    top[0]['enum_done'] = True
        doc = []
        paren = 0
        for k in range(i, consumed + 1):
            for ch in code[k]:
                if ch == '(':
                    paren += 1
                elif ch == ')':
                    paren -= 1
                if ch == '{':
                    depth += 1
                    if pending_container is not None and paren <= 0:
                        stack.append((pending_container, depth))
                        pending_container = None
                elif ch == '}':
                    if stack and depth == stack[-1][1]:
                        stack.pop()
                    depth -= 1
        i = consumed + 1
    return pkg, items


def md_entry(e, level):
    out = []
    kind = e['kind']
    out.append(f"{'#' * level} {e['name']}\n")
    out.append('```kotlin\n' + e['sig'] + '\n```\n')
    if e['doc']:
        out.append(e['doc'] + '\n')
    if e.get('enum'):
        out.append('Values: ' + ', '.join(f'`{v}`' for v in e['enum']) + '\n')
    nested = [mm for mm in e['members'] if mm['kind'] in ('class', 'interface', 'object', 'companion object', 'fun interface')]
    plain = [mm for mm in e['members'] if mm not in nested]
    if plain:
        rows = []
        for mm in plain:
            d = first_para(mm['doc']) if mm['doc'] else ''
            rows.append(f"- `{mm['sig']}`" + (f"  \n  {d}" if d else ''))
        out.append('\n'.join(rows) + '\n')
    for nn in nested:
        if nn['kind'] == 'companion object' and not nn['members'] and not nn['doc']:
            continue
        sub = dict(nn)
        sub['name'] = e['name'] + '.' + nn['name']
        out.append(md_entry(sub, min(level + 1, 5)))
    return '\n'.join(out)



PAGES = collections.OrderedDict()
for path in sorted(glob.glob(os.path.join(ROOT, '**', '*.kt'), recursive=True)):
    pkg, items = parse(path)
    if not pkg or not items:
        continue
    PAGES.setdefault(pkg, []).append((os.path.basename(path), items))

import json
json.dump({pkg: files for pkg, files in PAGES.items()}, open(OUT, 'w'))
