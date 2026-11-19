import sys, os
repo, wiki = sys.argv[1], sys.argv[2]
ex = os.path.join(repo, 'demo/src/main/kotlin/net/swzo/brass/ui/demo/examples')
def src(name):
    return open(os.path.join(ex, name)).read().rstrip()
def block(name):
    return f"<details>\n<summary><code>{name}</code> (complete file)</summary>\n\n```kotlin\n{src(name)}\n```\n\n</details>\n"
old = open(os.path.join(wiki, 'Examples.md')).read()
ref = old[old.index('## Reference screens'):]
page = f"""# Examples

Three complete screens, built the way the [[Design-Guide]] describes and recorded while a script clicks through them, plus the five reference screens the design guide is built on. The complete source of each example is on this page; it is the same code as in `demo/src/main/kotlin/net/swzo/brass/ui/demo/examples/` and it compiles against brassui as it is.

Run them on the desktop:

```sh
./gradlew :desktop:run -Dui.screen=hub
./gradlew :desktop:run -Dui.screen=mod-settings
./gradlew :desktop:run -Dui.screen=market
```

Each is also a regression fixture: `-Dui.reference=examples` drives every step, writes a screenshot per step and runs the layout audit on each one. All three pass with no findings. Add `-Dui.record=true -Dui.recordFull=true` to film the run the way these recordings were made ([[Dev-Tools]]).

| Example | Shows |
|---|---|
| [Server hub](#server-hub) | sidebar navigation, sliding pages, a hidden row revealed, master/detail, a confirm dialog, tabs that slide sideways, an empty state, a form, a quest card, the narrow layout |
| [Mod settings](#mod-settings) | a nav rail with pinned rows and icons, a pinned footer, drill-down with `BrassNavStack`, the theme card, markdown |
| [Market](#market) | networking end to end: actions, validation, rate limits, shared and per-player state, error codes, an op-only async action, plus a custom widget |

To use one as a starting point, copy the file into your mod, change the package, and replace `BrassReferenceScreen` with `BrassAppScreen` (drop the `id` and `scenario()` members, which only the test harness uses).

## Server hub

![server hub](images/example-hub.gif)

A server's main menu, as a mod would ship it.

- `BrassSidebar` with two groups, a status count (`6/20`), a needs-you count (`3` unread), a progress hairline on Claims, and a pinned Start event key.
- `BrassPages(slide = VERTICAL)` behind the sidebar, so each row's page slides up or down.
- A hidden Events row. Start event calls `reveal("events", open = false)`: the row grows in with a glow and a NEW dot while the player stays on their page.
- Home: `BrassStat` tiles in a `BrassFlow`, a `BrassChart`, and a feed of `BrassListRow`s with player heads, under section headers.
- Players: a `BrassMasterDetail`. Wide, the list and the detail panel sit side by side. At 320 pixels the sidebar folds to icons and picking a player pushes the detail over the list with a back key.
- Kick asks first, in a `BrassModal` with the danger key last.
- Mail: a second `BrassPages(slide = HORIZONTAL)` under `tabs()`. Rows reveal their keys on hover; Archive is a `BrassEmptyState`.
- Claims: a `BrassForm`. Events: a `BrassQuestCard`.

{block('ServerHubScreen.kt')}
## Mod settings

![mod settings](images/example-mod-settings.gif)

A settings screen for a mod.

- `pages.navRail(pinnedBottom = 1, pinnedTop = 1)`: Overview is a headline key above a separator, About sits at the bottom, and every row shows the icon its page was created with.
- `BrassPages(slide = VERTICAL)`, so rail clicks slide.
- The footer is pinned first (Reset, a spring, Cancel, Save), and the rail and pages fill the space above it with `BrassLayout.fillAbove(footer)`.
- Keybinds is a `BrassNavStack`: picking a category pushes a level of `BrassKeybind` fields, Back pops it, and the list is where it was.
- Appearance is `BrassThemeCard`; About is `BrassMarkdown`.

{block('ModSettingsScreen.kt')}
## Market

![market](images/example-market.gif)

A shop that runs on the server. In the recording the player buys two iron ingots (coins drop from 120 to 102, stock from 24 to 22), tries to buy an Elytra they can't afford and gets the error, then hovers the admin key, which is disabled because the desktop's pretend player isn't op. Run it with `-Dbrassui.net.op=2` and Restock all works.

- `MarketActions` is the action set: `open` (publishes the player's wallet), `buy` and `sell` (validated, rate-limited, with their own error codes and messages), and `restock` (async, op level 2).
- Stock is a shared `brassValue`; each player's wallet is kept on the server and sent to that player with `publishTo`.
- The screen binds both with `BrassNet.state(...).onChange` and removes the listeners with `disposeWith`.
- Buy and Sell are `actionButton`s, so they disable while a request is in flight and show failures as toasts.
- `StockMeter` is a custom widget (see [[Custom-Widgets]]).

The networking pages explain each piece: [[Networking]], [[Networking-Actions]], [[Networking-State]], [[Networking-Errors]].

{block('MarketActions.kt')}
{block('MarketScreen.kt')}
{block('StockMeter.kt')}
"""
open(os.path.join(wiki, 'Examples.md'), 'w').write(page + ref)
