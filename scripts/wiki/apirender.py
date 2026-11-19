import json, re, sys, os

core = json.load(open(sys.argv[1]))
mod = json.load(open(sys.argv[2]))
out = sys.argv[3]
VERSION = sys.argv[4] if len(sys.argv) > 4 else 'current'

PAGES = [
    ('API-Core', 'Screens, themes and colours', ['net.swzo.brass.ui', 'net.swzo.brass.ui.component', 'net.swzo.brass.ui.dsl']),
    ('API-Base', 'The widget base, state, focus, motion and lifecycle', ['net.swzo.brass.ui.kit.base']),
    ('API-Input', 'Buttons, choices, values, keybinds and inventory grids', ['net.swzo.brass.ui.kit.input']),
    ('API-Layout', 'Pages, sidebar, nav stack, boxes, flows, forms and scroll views', ['net.swzo.brass.ui.kit.layout']),
    ('API-Surface', 'Windows, panels, dialogs, menus, lists, tables, charts and feedback', ['net.swzo.brass.ui.kit.surface']),
    ('API-Text', 'Fonts, labels, tags, text editing, markdown and code', ['net.swzo.brass.ui.kit.text']),
    ('API-Media', 'Icons, images, player heads and game content', ['net.swzo.brass.ui.kit.media']),
    ('API-Quest', 'Quests, quest cards, the quest tree and the quest book', ['net.swzo.brass.ui.kit.quest']),
    ('API-Paint', 'Cards, keycaps, the selection look, swatches and wires', ['net.swzo.brass.ui.kit.paint']),
    ('API-Platform', 'The platform seam, cursors, native drawing and model rendering', ['net.swzo.brass.ui.kit.platform']),
    ('API-Settings', 'The appearance card', ['net.swzo.brass.ui.kit.settings']),
    ('API-Net', 'Actions, state, permissions, the wire codec and skins', ['net.swzo.brass.ui.kit.net']),
    ('API-Node', 'The node editor, graph model, fields, execution and IO', ['net.swzo.brass.ui.kit.node']),
    ('API-Dev', 'The inspector and the layout audit', ['net.swzo.brass.ui.kit.dev']),
    ('API-Demo', 'Widget demos and the capture seam', ['net.swzo.brass.ui.kit.demo']),
]
MOD_PAGES = [('API-NeoForge', 'The NeoForge host: platform, networking payloads and capture', ['net.swzo.brass.ui.neoforge', 'net.swzo.brass.ui.neoforge.net', 'net.swzo.brass.ui.neoforge.shot'])]

CONTAINERS = ('class', 'interface', 'object', 'companion object', 'fun interface')


def anchor(name):
    return re.sub(r'[^a-z0-9 -]', '', name.lower()).replace(' ', '-')


def member_lines(members):
    rows = []
    for mm in members:
        d = mm['doc'].split('\n\n')[0].replace('\n', ' ').strip() if mm['doc'] else ''
        d = re.sub(r'\*\*', '', d)
        rows.append(f"- `{mm['sig']}`" + (f"<br>{d}" if d else ''))
    return '\n'.join(rows)


def render(e, level, prefix=''):
    name = prefix + e['name']
    parts = [f"{'#' * level} {name}\n", '```kotlin\n' + e['sig'] + '\n```\n']
    if e['doc']:
        parts.append(e['doc'] + '\n')
    if e.get('enum'):
        parts.append('Values: ' + ', '.join(f'`{v}`' for v in e['enum']) + '\n')
    plain = [m for m in e['members'] if m['kind'] not in CONTAINERS]
    nested = [m for m in e['members'] if m['kind'] in CONTAINERS]
    if plain:
        parts.append(member_lines(plain) + '\n')
    for n in nested:
        if n['kind'] == 'companion object':
            cm = [m for m in n['members'] if m['kind'] not in CONTAINERS]
            if cm:
                parts.append(f"Called on `{e['name']}` itself:\n\n" + member_lines(cm) + '\n')
            for nn in [m for m in n['members'] if m['kind'] in CONTAINERS]:
                parts.append(render(nn, min(level + 1, 6), name + '.'))
            continue
        parts.append(render(n, min(level + 1, 6), name + '.'))
    return '\n'.join(parts)


index = []


def page(title, blurb, pkgs, data):
    body = [f"# {title.replace('API-', '')} API\n", f"{blurb}. Generated from the brassui {VERSION} sources: every public class, constructor, property and function, with its doc comment. Guides that explain how to use these are linked from [[API-Reference]].\n"]
    toc = []
    sections = []
    for pkg in pkgs:
        for fname, items in data.get(pkg, []):
            tops = [e for e in items if e['kind'] in CONTAINERS]
            funcs = [e for e in items if e['kind'] not in CONTAINERS]
            for e in tops:
                toc.append(f"[{e['name']}](#{anchor(e['name'])})")
                index.append((e['name'], title, pkg, e['doc'].split('\n\n')[0].replace('\n', ' ') if e['doc'] else ''))
                sections.append(render(e, 2))
            if funcs:
                h = f"Top-level functions in {fname}"
                toc.append(f"[{h}](#{anchor(h)})")
                sections.append(f"## {h}\n\nPackage `{pkg}`.\n\n" + member_lines(funcs) + '\n')
                for f in funcs:
                    index.append((f['name'], title, pkg, (f['doc'].split('\n\n')[0].replace('\n', ' ') if f['doc'] else '') + f" (in {fname})"))
    body.append('Packages: ' + ', '.join(f'`{p}`' for p in pkgs) + '\n')
    body.append('Contents: ' + ' · '.join(toc) + '\n')
    body.extend(sections)
    open(os.path.join(out, title + '.md'), 'w').write('\n'.join(body))


for t, b, p in PAGES:
    page(t, b, p, core)
for t, b, p in MOD_PAGES:
    page(t, b, p, mod)

idx = ["# API reference\n",
       "Every public type in brassui, A to Z, with the page that documents it in full. The API pages are generated from the source, so they list every constructor parameter and member; the guide pages explain how to use them.\n",
       "| Page | Covers |", "|---|---|"]
for t, b, p in PAGES + MOD_PAGES:
    idx.append(f"| [[{t}]] | {b} |")
idx.append("\n## A to Z\n")
idx.append("| Name | Page | What it is |")
idx.append("|---|---|---|")
seen = set()
for name, title, pkg, doc in sorted(index, key=lambda x: x[0].lower()):
    key = (name, title)
    if key in seen:
        continue
    seen.add(key)
    d = re.sub(r'\*\*', '', doc).replace('|', '\\|')
    if len(d) > 140:
        d = d[:137].rsplit(' ', 1)[0] + '…'
    idx.append(f"| `{name}` | [{title.replace('API-', '')}]({title}#{anchor(name)}) | {d} |")
open(os.path.join(out, 'API-Reference.md'), 'w').write('\n'.join(idx) + '\n')
print(len(index), 'entries')
