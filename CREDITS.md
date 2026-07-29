# Credits

## Remotely / ReScreen — RedxAx (ReStudioMC)

BrassSync's in-game UI toolkit (`brassui`) was originally built in the visual grammar of **Remotely**
and its **ReScreen** UI framework, by **RedxAx** / **ReStudioMC**.

- Project: https://github.com/ReStudioMC/Remotely
- Modrinth: https://modrinth.com/mod/remotely

### What is used here

**No Remotely code or artwork is distributed with BrassSync.** The ReScreen icon sprites that were
previously bundled under `assets/brassui/textures/icons/restudio/` have been **removed**, and the
toolkit's icons are now our own pixel art, defined in source in `BrassIcons.kt`.

What remains is a **design debt, not a distribution**: the widget look and feel — the raised "keycap"
render (flat fill, 1-px inner border, outer ring, bottom lip), the hover-lift, the accent model and
the window/popup chrome — was arrived at by studying ReScreen's design. Every line of the rendering,
layout and animation code is BrassSync's own, written from scratch in Kotlin on Elementa.

This section stays because the influence is real and worth acknowledging, not because a licence
clause now compels it.

### Historical note on licence terms

While the sprites were bundled, that use rested on Remotely's custom licence (see the `LICENSE` file
in the Remotely repository): **Clause 1** (attribution) and **Clause 4** (indie exemption for
individual, non-commercial developers).

Two things to keep in mind:

- **Git history still contains the removed sprites.** Deleting them from the working tree does not
  remove them from previous commits. A history rewrite would be needed to purge them entirely.
- **Commercial use.** With the artwork gone, Clauses 2, 3 and 5 no longer bear on the current tree —
  there is no Remotely code or asset left to restrict. If any Remotely asset or code is reintroduced,
  those clauses apply again and written permission from RedxAx would be required for commercial use.
